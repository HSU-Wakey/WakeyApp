package com.example.wakey.ui.timeline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.local.PhotoDao;
import com.example.wakey.data.model.TimelineItem;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
                    if (item.getStory() != null && !item.getStory().isEmpty()) {
                        processedItems.add(item);
                        continue;
                    }

                    // DB에서 추가 정보 로드
                    loadAdditionalInfoFromDB(item);

                    // Gemini API로 스토리 생성
                    String story = generateStoryWithGemini(item);

                    // API 실패 시 기본 스토리 생성
                    if (story == null || story.isEmpty()) {
                        story = generateFallbackStory(item);
                    }

                    item.setStory(story);

                    // DB에 저장
                    photoDao.updateStory(item.getPhotoPath(), story);

                    Log.d(TAG, "스토리 생성 완료: " + story);
                    processedItems.add(item);

                    // API 레이트 리밋 고려
                    Thread.sleep(2000);
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
     */
    private String generateStoryWithGemini(TimelineItem item) {
        try {
            // 이미지를 Base64로 인코딩
            String base64Image = encodeImageToBase64(item.getPhotoPath());
            if (base64Image == null) {
                return null;
            }

            // 창의적인 프롬프트 생성
            String prompt = createCreativePromptForStory(item);

            // API 요청 생성
            JsonObject requestBody = createGeminiRequest(base64Image, prompt);

            // API 호출
            String response = callGeminiAPI(requestBody);

            return parseGeminiResponse(response);

        } catch (Exception e) {
            Log.e(TAG, "Gemini API 호출 중 오류: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 창의적인 프롬프트 생성 (모든 DB 정보 활용)
     */
    private String createCreativePromptForStory(TimelineItem item) {
        StringBuilder prompt = new StringBuilder();

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
        prompt.append("- 일반적인 표현('추억이 되었다', '소중한 시간')보다는 특별한 느낌의 표현 선호\n");
        prompt.append("- 시간, 장소, 요소들을 자연스럽게 녹여서 사용\n");
        prompt.append("- 매번 다른 스타일의 문체 시도\n");
        prompt.append("- 한국어로 작성, 이모지는 사용하지 않음\n");

        // 시간대별 특별한 분위기 추가
        if (item.getTime() != null) {
            addTimeBasedMood(prompt, item.getTime());
        }

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
     */
    private String generateFallbackStory(TimelineItem item) {
        Random random = new Random();
        String template = CREATIVE_TEMPLATES[random.nextInt(CREATIVE_TEMPLATES.length)];

        String location = getLocationString(item);
        String object = getObjectString(item);

        String story = String.format(template, location, object);

        // null 처리
        story = story.replace("null", "");
        story = story.replaceAll("\\s+", " ").trim();

        Log.d(TAG, "기본 스토리 생성: " + story);
        return story;
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

    /**
     * 리소스 해제
     */
    public void release() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}