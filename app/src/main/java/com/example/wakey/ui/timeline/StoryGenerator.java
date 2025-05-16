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

                    // 이미 스토리가 있으면 건너뛰기 (API에서 이미 생성된 경우)
                    if (item.getStory() != null && !item.getStory().isEmpty()) {
                        Log.d(TAG, "🔄 기존 스토리 발견, 스토리 생성 건너뛰기: [" + item.getStory() + "]");
                        processedItems.add(item);
                        continue;
                    }

                    // DB에서 추가 정보 로드
                    loadAdditionalInfoFromDB(item);

                    // DB에서 이미 저장된 스토리가 있는지 확인
                    Photo existingPhoto = photoDao.getPhotoByFilePath(item.getPhotoPath());
                    if (existingPhoto != null && existingPhoto.story != null && !existingPhoto.story.isEmpty()) {
                        // DB에 스토리가 있다면 그것을 사용
                        Log.d(TAG, "💾 DB에서 기존 스토리 불러옴: [" + existingPhoto.story + "]");
                        item.setStory(existingPhoto.story);
                    } else {
                        // 스토리가 없는 경우 - 임시 메시지 설정
                        item.setStory("스토리 생성 중...");
                        Log.d(TAG, "⏳ 스토리 생성 대기 중: " + item.getPhotoPath());
                    }

                    // UI 업데이트
                    if (context instanceof MainActivity) {
                        final TimelineItem finalItem = item;
                        ((MainActivity) context).runOnUiThread(() -> {
                            try {
                                // TimelineManager를 통한 업데이트
                                com.example.wakey.data.repository.TimelineManager.getInstance(context)
                                        .updateTimelineItem(finalItem);

                                // 스토리 어댑터 직접 업데이트
                                if (storyAdapter != null) {
                                    storyAdapter.updateItem(finalItem);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "UI 업데이트 중 오류: " + e.getMessage(), e);
                            }
                        });
                    }

                    processedItems.add(item);
                }

                final List<TimelineItem> finalProcessedItems = processedItems;
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onStoryGenerated(finalProcessedItems);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "스토리 생성 중 오류 발생: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onStoryGenerationFailed(e);
                    }
                });
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
     * Gemini API를 사용한 창의적인 스토리 생성
     * 현재 API 호출은 TimelineManager에서 처리되므로 이 메서드는 사용하지 않습니다.
     */
    private String generateStoryWithGemini(TimelineItem item) {
        // TimelineManager에서 API 호출이 처리되므로 사용하지 않음
        return null;
    }

    /**
     * 창의적인 프롬프트 생성 (모든 DB 정보 활용)
     */
    /**
     * 창의적인 프롬프트 생성 (모든 DB 정보 활용)
     */
    private String createCreativePromptForStory(TimelineItem item) {
        StringBuilder prompt = new StringBuilder();

        // 랜덤으로 스토리 스타일 선택
        Random random = new Random();
        int styleChoice = random.nextInt(5); // 0~4 사이의 랜덤 스타일

        prompt.append("이 사진에 대한 창의적이고 개성 있는 1-2줄 스토리를 만들어주세요.\n\n");
        prompt.append("참고 정보:\n");

        // 시간 정보
        if (item.getTime() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("a h시 mm분", Locale.KOREAN);
            SimpleDateFormat dayFormat = new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN);
            prompt.append("⏰ 시간: ").append(dayFormat.format(item.getTime())).append(" ").append(timeFormat.format(item.getTime())).append("\n");
        }

        // 장소 정보
        if (item.getPlaceName() != null && !item.getPlaceName().isEmpty()) {
            prompt.append("🏛️ 장소: ").append(item.getPlaceName()).append("\n");
        } else if (item.getLocation() != null && !item.getLocation().isEmpty() && !item.getLocation().equals("위치 정보 없음")) {
            prompt.append("📍 장소: ").append(item.getLocation()).append("\n");
        }

        // 감지된 객체들
        if (item.getDetectedObjects() != null && !item.getDetectedObjects().isEmpty()) {
            prompt.append("🔍 사진 속 요소: ").append(item.getDetectedObjects()).append("\n");
        }

        // 캡션
        if (item.getCaption() != null && !item.getCaption().isEmpty()) {
            prompt.append("💬 AI 설명: ").append(item.getCaption()).append("\n");
        }

        // 주변 관심장소
        if (item.getNearbyPOIs() != null && !item.getNearbyPOIs().isEmpty()) {
            prompt.append("🗺️ 주변 명소: ").append(String.join(", ", item.getNearbyPOIs())).append("\n");
        }

        // 활동 유형
        if (item.getActivityType() != null && !item.getActivityType().isEmpty()) {
            prompt.append("🎯 활동: ").append(item.getActivityType()).append("\n");
        }

        prompt.append("\n스토리 작성 가이드라인:\n");
        prompt.append("- 1-2문장으로 간결하게 작성\n");
        prompt.append("- 창의적이고 개성 있는 표현 사용\n");
        prompt.append("- 감정적으로 공감할 수 있도록 작성\n");
        prompt.append("- 뻔한 표현(예: '추억이 되었다', '소중한 시간')은 피하고, 특별한 느낌의 표현 선호\n");

        // 랜덤 스타일에 따른 추가 지침
        switch (styleChoice) {
            case 0:
                // 감성적 일기체
                prompt.append("- 감성적인 일기체로 작성\n");
                prompt.append("- 시간과 장소는 문장 중간이나 끝에 자연스럽게 녹여서 사용\n");
                prompt.append("- 첫 문장을 '오늘은', '어느새', '문득' 같은 표현으로 시작\n");
                prompt.append("- '-했다', '-였다' 같은 과거형 종결어미 사용\n");
                break;
            case 1:
                // 시적 표현체
                prompt.append("- 서정적이고 시적인 표현으로 작성\n");
                prompt.append("- 비유와 은유를 활용한 독특한 문체 사용\n");
                prompt.append("- 날짜나 장소를 직접적으로 언급하지 말고 간접적인 표현으로 암시\n");
                prompt.append("- 마치 짧은 시처럼 감각적인 표현 사용\n");
                break;
            case 2:
                // 현재 진행형 체험
                prompt.append("- 현재형 시점으로 작성\n");
                prompt.append("- '-고 있다', '-는 중이다' 같은 현재 진행형 표현 사용\n");
                prompt.append("- 마치 지금 그 순간을 경험하는 것처럼 생생하게 묘사\n");
                prompt.append("- 날짜보다 시간대(아침, 저녁 등)와 분위기에 초점\n");
                break;
            case 3:
                // 대화체/독백체
                prompt.append("- 대화체나 독백체로 작성\n");
                prompt.append("- '~네', '~구나', '~지' 같은 종결어미 사용\n");
                prompt.append("- 마치 친구에게 말하듯 편안한 어조 사용\n");
                prompt.append("- 날짜와 장소는 '거기' '그때' 같은 지시어로 간접적으로 표현\n");
                break;
            case 4:
                // 감탄/질문형
                prompt.append("- 감탄사나 질문형으로 시작하는 문장 포함\n");
                prompt.append("- '어쩜!', '와!', '정말?'과 같은 표현으로 시작하거나 끝맺음\n");
                prompt.append("- 시간과 장소는 구체적으로 언급하되 문장 구조 속에 자연스럽게 통합\n");
                prompt.append("- 감정을 강조하는 어조 사용\n");
                break;
        }

        prompt.append("- 이 사진만의 특별한 요소나 분위기를 포착\n");
        prompt.append("- 한국어로 작성하고, 어울리는 이모지도 함께 사용\n");

        // 시간대별 분위기 추가 (기존 코드와 동일)
        if (item.getTime() != null) {
            addTimeBasedMood(prompt, item.getTime());
        }

        // 중요: 날짜/장소 구조 변경 명시
        prompt.append("\n\n특별 지시사항: 날짜와 장소를 문장 시작에 '5월 4일 일요일 중구에서...'와 같은 형식으로 나열하지 마세요. 문장 중간이나 뒷부분에 자연스럽게 통합하거나, 간접적으로 표현하세요. 매번 다른 문장 구조를 사용해서 다양한 스토리를 만들어주세요.\n");

        prompt.append("\n\n스토리:");

        return prompt.toString();
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

    /**
     * Gemini API 요청 생성
     */
    private JsonObject createGeminiRequest(String base64Image, String prompt) {
        JsonObject requestBody = new JsonObject();

        // contents 배열
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");

        // parts 배열
        JsonArray parts = new JsonArray();

        // 텍스트 파트
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);

        // 이미지 파트
        JsonObject imagePart = new JsonObject();
        imagePart.addProperty("mime_type", "image/jpeg");
        JsonObject fileData = new JsonObject();
        fileData.addProperty("data", base64Image);
        imagePart.add("file_data", fileData);
        parts.add(imagePart);

        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        // 생성 설정 (창의성 높임)
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.9);
        generationConfig.addProperty("maxOutputTokens", 150);
        generationConfig.addProperty("topP", 0.95);
        generationConfig.addProperty("topK", 40);
        requestBody.add("generationConfig", generationConfig);

        return requestBody;
    }

    /**
     * Gemini API 호출
     */
    private String callGeminiAPI(JsonObject requestBody) throws Exception {
        String apiKey = getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("Gemini API 키가 설정되지 않았습니다.");
        }

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.get("application/json")
        );

        Request request = new Request.Builder()
                .url(GEMINI_API_URL + "?key=" + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "API 호출 실패: " + response.code() + " " + response.message());
                return null;
            }

            return response.body().string();
        }
    }

    /**
     * Gemini API 응답 파싱 (에러 처리 강화)
     */
    private String parseGeminiResponse(String response) {
        try {
            if (response == null || response.isEmpty()) {
                Log.e(TAG, "빈 응답 수신");
                return null;
            }

            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            if (jsonResponse.has("candidates")) {
                JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
                if (candidates.size() > 0) {
                    JsonObject candidate = candidates.get(0).getAsJsonObject();

                    // finishReason 확인
                    if (candidate.has("finishReason") && !candidate.get("finishReason").getAsString().equals("STOP")) {
                        Log.w(TAG, "비정상적인 완료 상태: " + candidate.get("finishReason").getAsString());
                    }

                    if (candidate.has("content")) {
                        JsonObject content = candidate.getAsJsonObject("content");
                        JsonArray parts = content.getAsJsonArray("parts");
                        if (parts.size() > 0) {
                            JsonObject part = parts.get(0).getAsJsonObject();
                            return part.get("text").getAsString().trim();
                        }
                    }
                }
            }

            // 에러 메시지 확인
            if (jsonResponse.has("error")) {
                JsonObject error = jsonResponse.getAsJsonObject("error");
                String errorMessage = error.get("message").getAsString();
                Log.e(TAG, "API 에러 응답: " + errorMessage);
            }

            Log.e(TAG, "응답 파싱 실패: 예상된 구조가 아님");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "응답 파싱 중 오류: " + e.getMessage());
            return null;
        }
    }

    /**
     * 기본 스토리 생성 (API 실패 시)
     * 현재는 사용하지 않습니다.
     */
    private String generateFallbackStory(TimelineItem item) {
        // 더 이상 기본 템플릿 스토리를 사용하지 않음
        return "스토리 생성 중...";
    }
    /**
     * 위치 표현 추출
     */
    private String getLocationString(TimelineItem item) {
        if (item.getPlaceName() != null && !item.getPlaceName().isEmpty()) {
            return item.getPlaceName();
        }

        if (item.getLocation() != null && !item.getLocation().isEmpty() && !item.getLocation().equals("위치 정보 없음")) {
            // 주소에서 핵심 위치만 추출
            String location = item.getLocation();
            if (location.contains("구 ")) {
                String[] parts = location.split("구 ");
                if (parts.length > 0) {
                    return parts[0].substring(parts[0].lastIndexOf(" ") + 1) + "구";
                }
            }
            return location;
        }

        return "이곳";
    }

    /**
     * 객체 표현 추출
     */
    private String getObjectString(TimelineItem item) {
        if (item.getDetectedObjects() == null || item.getDetectedObjects().isEmpty()) {
            return "일상의 순간들";
        }

        String[] objects = item.getDetectedObjects().split(",");
        if (objects.length > 0) {
            return objects[0].trim();
        }

        return "사진 속 풍경";
    }

    public void setStoryAdapter(StoryAdapter adapter) {
        this.storyAdapter = adapter;
        Log.d(TAG, "StoryAdapter 설정됨: " + (adapter != null));
    }

    // 스토리 생성 완료 후 어댑터 직접 업데이트 메서드 수정
    // StoryGenerator.java에서 updateStoryInUI 메서드 수정

    private void updateStoryInUI(TimelineItem item) {
        // 로깅
        Log.d(TAG, "⭐ updateStoryInUI 호출됨: " + item.getPhotoPath());
        Log.d(TAG, "⭐ 스토리 내용: " + item.getStory());

        // StoryAdapter 업데이트
        if (storyAdapter != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Log.d(TAG, "⭐ storyAdapter.updateItem 호출");
                    storyAdapter.updateItem(item);

                    // 전체 리스트 갱신
                    storyAdapter.notifyDataSetChanged();
                    Log.d(TAG, "⭐ 어댑터 갱신 완료");
                } catch (Exception e) {
                    Log.e(TAG, "⭐ 어댑터 업데이트 실패: " + e.getMessage(), e);
                }
            });
        } else {
            Log.e(TAG, "⭐ storyAdapter가 null입니다!");
        }

        // TimelineManager를 통한 추가 업데이트
        try {
            Log.d(TAG, "⭐ TimelineManager 업데이트 호출");
            com.example.wakey.data.repository.TimelineManager.getInstance(context)
                    .updateTimelineItem(item);
        } catch (Exception e) {
            Log.e(TAG, "⭐ TimelineManager 업데이트 실패: " + e.getMessage(), e);
        }

        // UI 탭 전환
        if (context instanceof MainActivity) {
            Log.d(TAG, "⭐ UIManager 스토리 탭 전환 호출");
            new Handler(Looper.getMainLooper()).post(() -> {
                UIManager.getInstance(context).switchToStoryTab();
            });
        }
    }
    public List<TimelineItem> getStoriesForDate(Date date) {
        // 날짜를 yyyy-MM-dd 형식 문자열로 변환
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(date);

        // PhotoDao에서 해당 날짜 사진들 가져오기
        List<Photo> photos = photoDao.getPhotosForDate(dateString);
        List<TimelineItem> timelineItems = new ArrayList<>();

        for (Photo photo : photos) {
            // photo.dateTaken은 String 이므로 Date로 변환 필요 (yyyy-MM-dd HH:mm:ss 가정)
            Date photoDate = null;
            try {
                photoDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(photo.dateTaken);
            } catch (ParseException e) {
                // 변환 실패 시, 기본 null 또는 예외 처리
                e.printStackTrace();
            }

            // LatLng 생성 (위도/경도 정보가 있을 때)
            LatLng latLng = null;
            if (photo.latitude != null && photo.longitude != null) {
                latLng = new LatLng(photo.latitude, photo.longitude);
            }

            // TimelineItem 빌더로 세팅
            TimelineItem item = new TimelineItem.Builder()
                    .setPhotoPath(photo.filePath)
                    .setTime(photoDate)
                    .setStory(photo.story)
                    .setCaption(photo.caption)
                    .setLocation(photo.fullAddress)
                    .setLatLng(latLng)
                    .setDetectedObjects(photo.detectedObjects)
                    .build();

            timelineItems.add(item);
        }

        return timelineItems;
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