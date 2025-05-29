package com.example.wakey.ui.timeline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.example.wakey.MainActivity;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.local.PhotoDao;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.manager.UIManager;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Google Gemini API를 사용하여 사진 정보로부터 창의적인 스토리를 생성하는 클래스
 * DB의 모든 정보를 활용하여 개성 있는 스토리 생성
 */
public class StoryGenerator {
    private static final String TAG = "StoryGenerator";
    private static StoryGenerator instance;
    private Context context;
    private ExecutorService executorService;
    private Handler mainHandler;
    private StoryAdapter storyAdapter;

    // Gemini API 설정
    private String getGeminiApiKey() {
        return context.getString(context.getResources().getIdentifier("gemini_api_key", "string", context.getPackageName()));
    }
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent";

    private OkHttpClient httpClient;
    private Gson gson;
    private PhotoDao photoDao;

    public interface OnStoryGeneratedListener {
        void onStoryGenerated(List<TimelineItem> itemsWithStories);
        void onStoryGenerationFailed(Exception e);
    }

    // 기본 스토리 템플릿 (API 실패 시 사용)
    private static final String[] CREATIVE_TEMPLATES = {
            "%s에서 마주친 일상의 한 조각, %s가 특별하게 다가왔다.",
            "시간이 멈춘 것 같은 %s의 순간, %s와 함께한 시간.",
            "%s의 정취가 물든 하루, %s로 가득 찬 기억.",
            "평범함 속 특별함을 발견한 %s, %s가 눈에 머물렀다.",
            "%s에서 포착한 소중한 순간, %s이 마음에 새겨졌다.",
            "일상 속 작은 행복을 찾은 %s의 오후, %s와 함께.",
            "%s의 풍경 속에 자연스럽게 녹아든 %s, 오늘의 기록.",
            "기억하고 싶은 %s의 한 순간, %s가 공간을 가득 채웠다.",
            "%s에서 만난 예상치 못한 순간, %s로 빛나는 하루.",
            "시간의 흐름을 담은 %s의 한 컷, %s가 주인공이 되었다."
    };

    private StoryGenerator() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public static synchronized StoryGenerator getInstance(Context context) {
        if (instance == null) {
            instance = new StoryGenerator();
            instance.context = context.getApplicationContext();
            instance.photoDao = AppDatabase.getInstance(context).photoDao();
        }
        return instance;
    }

    /**
     * 타임라인 항목에 대한 스토리 생성 (모든 DB 정보 활용)
     */
    public void generateStories(List<TimelineItem> timelineItems, OnStoryGeneratedListener listener) {
        if (timelineItems == null || timelineItems.isEmpty()) {
            if (listener != null) {
                listener.onStoryGenerationFailed(new Exception("타임라인 항목이 없습니다."));
            }
            return;
        }

        executorService.submit(() -> {
            try {
                List<TimelineItem> processedItems = new ArrayList<>();

                for (TimelineItem item : timelineItems) {
                    if (item.getPhotoPath() == null) {
                        processedItems.add(item);
                        continue;
                    }

                    // 이미 스토리가 있으면 건너뛰기
                    if (item.getStory() != null && !item.getStory().isEmpty() &&
                            !item.getStory().equals("스토리 생성 중...")) {
                        Log.d(TAG, "🔄 기존 스토리 발견: " + item.getStory());
                        processedItems.add(item);
                        continue;
                    }

                    // DB에서 추가 정보 로드
                    loadAdditionalInfoFromDB(item);

                    // DB에서 저장된 스토리 확인
                    Photo existingPhoto = photoDao.getPhotoByFilePath(item.getPhotoPath());
                    if (existingPhoto != null && existingPhoto.story != null &&
                            !existingPhoto.story.isEmpty()) {
                        Log.d(TAG, "💾 DB에서 스토리 로드: " + existingPhoto.story);
                        item.setStory(existingPhoto.story);
                        processedItems.add(item);
                        continue;
                    }

                    // 스토리가 없는 경우 - Gemini API로 생성
                    Log.d(TAG, "🆕 새로운 스토리 생성 시작: " + item.getPhotoPath());
                    generateStoryWithGemini(item);
                    processedItems.add(item);
                }

                final List<TimelineItem> finalProcessedItems = processedItems;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onStoryGenerated(finalProcessedItems);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "스토리 생성 중 오류: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onStoryGenerationFailed(e);
                    }
                });
            }
        });
    }

    // Gemini API를 사용한 스토리 생성 메서드
    private void generateStoryWithGemini(TimelineItem item) {
        try {
            String apiKey = getGeminiApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                Log.e(TAG, "Gemini API 키가 설정되지 않았습니다");
                item.setStory("API 키가 설정되지 않았습니다");
                return;
            }

            // 이미지 인코딩
            String base64Image = encodeImageToBase64(item.getPhotoPath());
            if (base64Image == null) {
                Log.e(TAG, "이미지 인코딩 실패");
                item.setStory("이미지를 처리할 수 없습니다");
                return;
            }

            // 프롬프트 생성
            String prompt = createStoryPrompt(item);

            // API 호출
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            // JSON 요청 생성
            JsonObject requestJson = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject content = new JsonObject();
            JsonArray parts = new JsonArray();

            // 텍스트 파트
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);
            parts.add(textPart);

            // 이미지 파트
            JsonObject imagePart = new JsonObject();
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mimeType", "image/jpeg");
            inlineData.addProperty("data", base64Image);
            imagePart.add("inlineData", inlineData);
            parts.add(imagePart);

            content.add("parts", parts);
            contents.add(content);
            requestJson.add("contents", contents);

            // 생성 설정
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 0.7);
            generationConfig.addProperty("maxOutputTokens", 100);
            requestJson.add("generationConfig", generationConfig);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    requestJson.toString()
            );

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    String story = parseGeminiResponse(responseBody);

                    if (story != null && !story.isEmpty()) {
                        item.setStory(story);

                        // DB에 저장
                        saveStoryToDB(item.getPhotoPath(), story);

                        // UI 업데이트
                        updateUI(item);
                    } else {
                        item.setStory("스토리 생성 실패");
                    }
                } else {
                    Log.e(TAG, "API 응답 오류: " + response.code());
                    item.setStory("스토리 생성 오류");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Gemini API 호출 실패: " + e.getMessage(), e);
            item.setStory("스토리 생성 중 오류 발생");
        }
    }

    // 스토리 프롬프트 생성
    private String createStoryPrompt(TimelineItem item) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("이 사진에 대한 짧고 감성적인 한 줄 스토리를 작성해주세요.\n\n");

        if (item.getTime() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일 HH시 mm분", Locale.KOREAN);
            prompt.append("시간: ").append(dateFormat.format(item.getTime())).append("\n");
        }

        if (item.getLocation() != null && !item.getLocation().isEmpty()) {
            prompt.append("장소: ").append(item.getLocation()).append("\n");
        }

        if (item.getDetectedObjects() != null && !item.getDetectedObjects().isEmpty()) {
            prompt.append("사진 속 요소: ").append(item.getDetectedObjects()).append("\n");
        }

        prompt.append("\n규칙:\n");
        prompt.append("- 한 문장으로 작성\n");
        prompt.append("- 감성적이고 시적인 표현 사용\n");
        prompt.append("- 이모지 1-2개 포함\n");
        prompt.append("- 30자 이내로 간결하게\n");

        return prompt.toString();
    }

    // Gemini 응답 파싱
    private String parseGeminiResponse(String jsonResponse) {
        try {
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            JsonArray candidates = response.getAsJsonArray("candidates");

            if (candidates != null && candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");

                if (parts != null && parts.size() > 0) {
                    JsonObject part = parts.get(0).getAsJsonObject();
                    return part.get("text").getAsString().trim();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "응답 파싱 오류: " + e.getMessage());
        }
        return null;
    }

    // DB에 스토리 저장
    private void saveStoryToDB(String photoPath, String story) {
        executorService.execute(() -> {
            try {
                int updated = photoDao.updateStory(photoPath, story);
                Log.d(TAG, "스토리 DB 저장 완료: " + (updated > 0 ? "성공" : "실패"));
            } catch (Exception e) {
                Log.e(TAG, "스토리 DB 저장 실패: " + e.getMessage());
            }
        });
    }

    // UI 업데이트
    private void updateUI(TimelineItem item) {
        mainHandler.post(() -> {
            if (storyAdapter != null) {
                storyAdapter.updateItem(item);
            }
        });
    }

    /**
     * DB에서 추가 정보 로드
     */
    private void loadAdditionalInfoFromDB(TimelineItem item) {
        try {
            Photo photo = photoDao.getPhotoByFilePath(item.getPhotoPath());
            if (photo != null) {
                // 상세 주소 정보
                if (photo.fullAddress != null && !photo.fullAddress.isEmpty()) {
                    item.setLocation(photo.fullAddress);
                }

                // 장소명
                if (photo.locationGu != null && !photo.locationGu.isEmpty()) {
                    item.setPlaceName(photo.locationGu);
                }

                // 객체 정보 병합 (Vision + DB)
                String existingObjects = item.getDetectedObjects() != null ? item.getDetectedObjects() : "";
                String dbObjects = photo.detectedObjects != null ? photo.detectedObjects : "";
                String combinedObjects = mergeDetectedObjects(existingObjects, dbObjects);
                item.setDetectedObjects(combinedObjects);

                // 기존 캡션
                if (photo.caption != null && !photo.caption.isEmpty()) {
                    item.setCaption(photo.caption);
                }

                // 위치 좌표
                if (photo.latitude != 0 && photo.longitude != 0) {
                    item.setLatitude(photo.latitude);
                    item.setLongitude(photo.longitude);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "DB 정보 로드 실패: " + e.getMessage());
        }
    }

    /**
     * 감지된 객체들 병합 (중복 제거)
     */
    private String mergeDetectedObjects(String existingObjects, String additionalObjects) {
        Set<String> objectSet = new HashSet<>();

        if (existingObjects != null && !existingObjects.isEmpty()) {
            objectSet.addAll(Arrays.asList(existingObjects.split(",")));
        }

        if (additionalObjects != null && !additionalObjects.isEmpty()) {
            objectSet.addAll(Arrays.asList(additionalObjects.split(",")));
        }

        // 빈 문자열 제거
        objectSet.removeIf(s -> s.trim().isEmpty());

        return String.join(",", objectSet);
    }


    /**
     * 시간대별 분위기 추가
     */
    private void addTimeBasedMood(StringBuilder prompt, java.util.Date time) {
        SimpleDateFormat hourFormat = new SimpleDateFormat("HH", Locale.getDefault());
        int hour = Integer.parseInt(hourFormat.format(time));

        prompt.append("\n분위기 조건: ");
        if (hour >= 5 && hour < 10) {
            prompt.append("이른 아침의 상쾌함과 시작의 에너지를 담아주세요");
        } else if (hour >= 10 && hour < 14) {
            prompt.append("밝은 오전/점심의 활기차고 생기 넘치는 분위기를 담아주세요");
        } else if (hour >= 14 && hour < 18) {
            prompt.append("따뜻한 오후 햇살의 편안하고 여유로운 분위기를 담아주세요");
        } else if (hour >= 18 && hour < 21) {
            prompt.append("저녁 노을의 로맨틱하고 감성적인 분위기를 담아주세요");
        } else {
            prompt.append("밤의 신비롭고 고요한 분위기를 담아주세요");
        }
    }

    /**
     * 이미지를 Base64로 인코딩
     */
    private String encodeImageToBase64(String imagePath) {
        try {
            InputStream inputStream = context.getContentResolver()
                    .openInputStream(Uri.parse(imagePath));

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) return null;

            // 메모리 효율을 위해 이미지 리사이징
            bitmap = resizeBitmap(bitmap, 512);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            bitmap.recycle();

            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "이미지 Base64 인코딩 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * 이미지 리사이징
     */
    private Bitmap resizeBitmap(Bitmap original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();
        float ratio = Math.min((float) maxDimension / width, (float) maxDimension / height);

        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    public void setStoryAdapter(StoryAdapter adapter) {
        this.storyAdapter = adapter;
        Log.d(TAG, "StoryAdapter 설정됨: " + (adapter != null));
    }


    public List<TimelineItem> getStoriesForDate(Date date) {
        // 날짜를 yyyy-MM-dd 형식 문자열로 변환
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(date);

        Log.d(TAG, "스토리 로드 시작 - 날짜: " + dateString);

        // PhotoDao에서 해당 날짜 사진들 가져오기 (수정된 쿼리 사용)
        List<Photo> photos = photoDao.getPhotosForDatePattern(dateString);
        List<TimelineItem> timelineItems = new ArrayList<>();

        Log.d(TAG, "검색된 사진 수: " + photos.size());

        for (Photo photo : photos) {
            try {
                // photo.dateTaken은 "yyyy-MM-dd HH:mm:ss" 형식
                Date photoDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(photo.dateTaken);

                // LatLng 생성
                LatLng latLng = null;
                if (photo.latitude != null && photo.longitude != null &&
                        photo.latitude != 0 && photo.longitude != 0) {
                    latLng = new LatLng(photo.latitude, photo.longitude);
                }

                // TimelineItem 빌더로 생성
                TimelineItem.Builder builder = new TimelineItem.Builder()
                        .setPhotoPath(photo.filePath)
                        .setTime(photoDate)
                        .setLatLng(latLng);

                // 캡션 설정
                if (photo.caption != null && !photo.caption.isEmpty()) {
                    builder.setCaption(photo.caption);
                }

                // 위치 정보 설정
                String location = buildLocationString(photo);
                if (!location.isEmpty()) {
                    builder.setLocation(location);
                }

                // 감지된 객체 설정
                if (photo.detectedObjects != null && !photo.detectedObjects.isEmpty()) {
                    builder.setDetectedObjects(photo.detectedObjects);
                }

                // **중요: DB에 저장된 스토리가 있으면 설정**
                if (photo.story != null && !photo.story.isEmpty()) {
                    builder.setStory(photo.story);
                    Log.d(TAG, "기존 스토리 로드: " + photo.story);
                }

                TimelineItem item = builder.build();
                timelineItems.add(item);

            } catch (Exception e) {
                Log.e(TAG, "TimelineItem 생성 실패: " + e.getMessage());
                e.printStackTrace();
            }
        }

        Log.d(TAG, "생성된 타임라인 아이템 수: " + timelineItems.size());
        return timelineItems;
    }

    // 위치 정보 문자열 생성 헬퍼 메서드
    private String buildLocationString(Photo photo) {
        StringBuilder location = new StringBuilder();

        if (photo.fullAddress != null && !photo.fullAddress.isEmpty()) {
            return photo.fullAddress;
        }

        if (photo.locationDo != null && !photo.locationDo.isEmpty()) {
            location.append(photo.locationDo).append(" ");
        }
        if (photo.locationSi != null && !photo.locationSi.isEmpty()) {
            location.append(photo.locationSi).append(" ");
        }
        if (photo.locationGu != null && !photo.locationGu.isEmpty()) {
            location.append(photo.locationGu).append(" ");
        }
        if (photo.locationStreet != null && !photo.locationStreet.isEmpty()) {
            location.append(photo.locationStreet);
        }

        return location.toString().trim();
    }

    /**
     * 리소스 해제
     */
    public void release() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}