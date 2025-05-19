package com.example.wakey.ui.timeline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import com.example.wakey.MainActivity;
import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.local.PhotoDao;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.manager.UIManager;
import com.example.wakey.service.ClusterService;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 타임라인 데이터 관리 클래스
 * Google Vision API + Gemini API를 조합하여 창의적인 스토리 생성
 */
public class TimelineManager {
    private static final String TAG = "TimelineManager";
    private static TimelineManager instance;
    private final Context context;
    private final ClusterService clusterService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<TimelineItem> currentTimelineItems = new ArrayList<>();
    private String currentDate;

    private StoryAdapter storyAdapter;

    public void setStoryAdapter(StoryAdapter adapter) {
        this.storyAdapter = adapter;
        Log.d(TAG, "TimelineManager: StoryAdapter 설정됨");
    }
    // 스토리 생성 리스너
    public interface OnStoryGeneratedListener {
        void onStoryGenerated(List<TimelineItem> itemsWithStories);
    }

    private OnStoryGeneratedListener onStoryGeneratedListener;

    private TimelineManager(Context context) {
        this.context = context.getApplicationContext();
        this.clusterService = ClusterService.getInstance(context);

        // API 키 상태 확인
        String apiKey = getGeminiApiKey();
        Log.d(TAG, "=== TimelineManager 초기화 ===");
        Log.d(TAG, "Gemini API 키 상태: " + (apiKey != null ? "설정됨" : "미설정"));
        if (apiKey != null) {
            Log.d(TAG, "API 키 길이: " + apiKey.length());
        }
    }

    public static synchronized TimelineManager getInstance(Context context) {
        if (instance == null) {
            instance = new TimelineManager(context);
        }
        return instance;
    }

    /**
     * strings.xml에서 API 키 읽기
     */
    private String getVisionApiKey() {
        try {
            return context.getString(R.string.google_vision_api_key);
        } catch (Exception e) {
            Log.e(TAG, "Vision API 키 로드 실패: " + e.getMessage());
            return null;
        }
    }

    private String getGeminiApiKey() {
        try {
            String apiKey = context.getString(R.string.gemini_api_key);
            if (apiKey != null) {
                // 공백과 개행문자 제거
                apiKey = apiKey.trim();
                Log.d(TAG, "API 키 정제 후 길이: " + apiKey.length());
            }
            return apiKey;
        } catch (Exception e) {
            Log.e(TAG, "Gemini API 키 로드 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * 특정 날짜의 타임라인 데이터 로드
     */
    public List<TimelineItem> loadTimelineForDate(String dateString) {
        currentDate = dateString;
        currentTimelineItems = clusterService.generateTimelineFromPhotos(dateString);

        // DB 정보 추가 로드
        enhanceTimelineItemsWithDBInfo(currentTimelineItems);

        // 기존 스토리 확인 로그 추가
        Log.d(TAG, "=== 타임라인 로드 후 스토리 상태 ===");
        for (TimelineItem item : currentTimelineItems) {
            Log.d(TAG, "아이템: " + item.getPhotoPath());
            Log.d(TAG, "스토리: " + (item.getStory() != null ? item.getStory() : "없음"));
        }

        // 스토리 생성
        generateStoriesForTimelineOptimized(currentTimelineItems);

        return currentTimelineItems;
    }

    /**
     * DB의 상세 정보를 타임라인 아이템에 추가
     */
    private void enhanceTimelineItemsWithDBInfo(List<TimelineItem> items) {
        PhotoDao photoDao = AppDatabase.getInstance(context).photoDao();

        for (TimelineItem item : items) {
            try {
                Photo photo = photoDao.getPhotoByFilePath(item.getPhotoPath());
                if (photo != null) {
                    // 상세 주소 정보
                    if (photo.fullAddress != null && !photo.fullAddress.isEmpty()) {
                        item.setLocation(photo.fullAddress);
                    }

                    // 기존 DB 저장된 객체 인식 정보 (망코프로파일의 해시태그)
                    if (photo.detectedObjects != null && !photo.detectedObjects.isEmpty()) {
                        // Vision API 결과와 DB의 기존 결과를 결합
                        String existingObjects = item.getDetectedObjects() != null ? item.getDetectedObjects() : "";
                        String dbObjects = photo.detectedObjects;

                        // 중복 제거하면서 결합
                        String combined = combineAndDeduplicateObjects(existingObjects, dbObjects);
                        item.setDetectedObjects(combined);
                    }

                    // 장소명 정보 (DB에서 가져온 실제 장소명)
                    if (photo.locationGu != null && !photo.locationGu.isEmpty()) {
                        item.setPlaceName(photo.locationGu);
                    }

                    // GPS 좌표 추가
                    if (photo.latitude != 0 && photo.longitude != 0) {
                        item.setLatLng(new LatLng(photo.latitude, photo.longitude));
                    }

                    // *** 중요: 기존 스토리도 로드하기 ***
                    if (photo.story != null && !photo.story.isEmpty()) {
                        item.setStory(photo.story);
                        Log.d(TAG, "기존 스토리 로드됨: " + photo.story);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "DB 정보 로드 실패: " + e.getMessage());
            }
        }
    }

    /**
     * 객체들을 결합하고 중복 제거
     */
    private String combineAndDeduplicateObjects(String existing, String additional) {
        if (existing == null) existing = "";
        if (additional == null) additional = "";

        String[] existingArray = existing.split(",");
        String[] additionalArray = additional.split(",");

        // Set을 사용하여 중복 제거
        java.util.Set<String> objectSet = new java.util.HashSet<>();

        for (String obj : existingArray) {
            if (!obj.trim().isEmpty()) {
                objectSet.add(obj.trim());
            }
        }

        for (String obj : additionalArray) {
            if (!obj.trim().isEmpty()) {
                objectSet.add(obj.trim());
            }
        }

        return String.join(",", objectSet);
    }

    /**
     * 최적화된 스토리 생성
     */
    // 5. 스토리 생성 전 기존 스토리 확인 강화
    public void generateStoriesForTimelineOptimized(List<TimelineItem> timelineItems) {
        if (timelineItems == null || timelineItems.isEmpty()) {
            Log.d(TAG, "타임라인 항목이 없음");
            return;
        }

        // 스토리가 없는 항목만 필터링
        List<TimelineItem> itemsNeedingStories = new ArrayList<>();
        for (TimelineItem item : timelineItems) {
            Log.d(TAG, "=== 스토리 체크: " + item.getPhotoPath() + " ===");
            Log.d(TAG, "현재 스토리: " + (item.getStory() != null ? item.getStory() : "null"));

            if (item.getStory() == null || item.getStory().isEmpty()) {
                itemsNeedingStories.add(item);
                Log.d(TAG, "스토리 필요: " + item.getPhotoPath());
            } else {
                Log.d(TAG, "이미 스토리 있음: " + item.getPhotoPath());
            }
        }

        Log.d(TAG, "전체 항목: " + timelineItems.size() + "개, 스토리 필요: " + itemsNeedingStories.size() + "개");

        if (itemsNeedingStories.isEmpty()) {
            Log.d(TAG, "모든 항목이 이미 스토리를 가지고 있음");
            if (onStoryGeneratedListener != null) {
                onStoryGeneratedListener.onStoryGenerated(timelineItems);
            }
            return;
        }

        // 스토리 생성 시작
        executor.execute(() -> {
            try {
                for (int i = 0; i < itemsNeedingStories.size(); i++) {
                    TimelineItem item = itemsNeedingStories.get(i);
                    Log.d(TAG, "스토리 생성 시작 [" + (i+1) + "/" + itemsNeedingStories.size() + "]: " + item.getPhotoPath());

                    generateStoryForItem(item);

                    // API 레이트 리밋 고려
                    if (i < itemsNeedingStories.size() - 1) {
                        Thread.sleep(2000); // 2초 대기 (Gemini도 호출하므로)
                    }
                }

                Log.d(TAG, "모든 스토리 생성 완료");

                // 최종 UI 업데이트 전 상태 확인
                Log.d(TAG, "=== 최종 UI 업데이트 전 상태 ===");
                for (TimelineItem item : timelineItems) {
                    Log.d(TAG, "아이템: " + item.getPhotoPath());
                    Log.d(TAG, "최종 스토리: " + (item.getStory() != null ? item.getStory() : "null"));
                }

                // UI 업데이트
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (onStoryGeneratedListener != null) {
                        onStoryGeneratedListener.onStoryGenerated(timelineItems);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "스토리 생성 중 오류: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 단일 타임라인 항목에 대한 스토리 생성
     */
    private void generateStoryForItem(TimelineItem item) {
        if (item == null || item.getPhotoPath() == null) {
            Log.d(TAG, "항목 없음");
            return;
        }

        if (item.getStory() != null && !item.getStory().isEmpty()) {
            Log.d(TAG, "이미 스토리 있음 - 건너뜀: " + item.getPhotoPath());
            return;
        }

        Log.d(TAG, "스토리 생성 시작: " + item.getPhotoPath());

        // 1단계: Vision API로 객체 인식
        analyzeImageWithVision(item.getPhotoPath(), new VisionAnalysisCallback() {
            @Override
            public void onAnalysisComplete(VisionResult result) {
                Log.d(TAG, "Vision 분석 완료: " + item.getPhotoPath());

                // Vision 결과를 타임라인 아이템에 추가
                if (result.getCaption() != null && !result.getCaption().isEmpty()) {
                    item.setCaption(result.getCaption());
                }

                if (!result.getDetectedObjects().isEmpty()) {
                    String objectsStr = String.join(",", result.getDetectedObjects());
                    String combined = combineAndDeduplicateObjects(item.getDetectedObjects(), objectsStr);
                    item.setDetectedObjects(combined);
                }

                // 2단계: Gemini API로 창의적인 스토리 생성
                generateCreativeStoryWithGemini(item);
            }

            @Override
            public void onAnalysisError(Exception e) {
                Log.e(TAG, "Vision 분석 실패: " + e.getMessage());
                // Vision 실패해도 Gemini로 스토리 생성 시도
                generateCreativeStoryWithGemini(item);
            }
        });
    }

    // generateCreativeStoryWithGemini 메서드 수정
    private void generateCreativeStoryWithGemini(TimelineItem item) {
        executor.execute(() -> {
            String apiKey = getGeminiApiKey();

            // API 키 검증
            if (!validateGeminiApiKey(apiKey)) {
                Log.e(TAG, "API 키 검증 실패");
                // 기본 스토리 생성 대신 오류 처리
                item.setStory("API 키 오류로 스토리를 생성할 수 없습니다");
                return;
            }

            try {
                Log.d(TAG, "=== Gemini API 호출 시작 ===");
                String story = callGeminiAPI(item);

                if (story != null && !story.isEmpty()) {
                    Log.d(TAG, "=== Gemini 스토리 업데이트 시작 ===");
                    Log.d(TAG, "스토리 내용: " + story);

                    // 아이템에 스토리 설정
                    item.setStory(story);

                    // DB 업데이트
                    updateItemInDatabase(item);

                    Log.d(TAG, "✅ Gemini 스토리 생성 완료: \"" + story + "\"");

                    item.setStory(story);

                    // UI 업데이트
                    if (storyAdapter != null) {
                        final TimelineItem finalItem = item;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            storyAdapter.updateItem(finalItem);
                            Log.d(TAG, "✅ 스토리 어댑터 아이템 업데이트: " + finalItem.getPhotoPath());
                        });
                    }
                    // UI 업데이트 - 딜레이 추가하여 DB 업데이트 완료 대기
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Log.d(TAG, "=== UI 업데이트 시작 ===");
                        if (onStoryGeneratedListener != null) {
                            Log.d(TAG, "onStoryGeneratedListener 호출");
                            onStoryGeneratedListener.onStoryGenerated(currentTimelineItems);
                        } else {
                            Log.e(TAG, "onStoryGeneratedListener가 null입니다!");
                        }
                    }, 500); // 500ms 딜레이
                } else {
                    Log.e(TAG, "Gemini 응답이 비어있음");
                    // 기본 스토리 생성 대신 오류 메시지
                    item.setStory("스토리 생성 실패. 다시 시도해주세요.");
                }
            } catch (Exception e) {
                Log.e(TAG, "=== Gemini API 호출 실패 상세 정보 ===");
                Log.e(TAG, "Exception 타입: " + e.getClass().getSimpleName());
                Log.e(TAG, "오류 메시지: " + e.getMessage());
                e.printStackTrace();

                // 기본 스토리 생성 대신 오류 메시지
                item.setStory("스토리 생성 중 오류가 발생했습니다.");
            }
        });
    }

    // API 키 검증 메서드 추가
    private boolean validateGeminiApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "API 키가 설정되지 않았습니다");
            return false;
        }

        // API 키 유효성 검사
        if (!apiKey.startsWith("AIza") || apiKey.length() < 35) {
            Log.e(TAG, "잘못된 API 키 형식: " + apiKey.length() + "자");
            Log.e(TAG, "API 키는 'AIza'로 시작하고 최소 35자 이상이어야 합니다");
            return false;
        }

        return true;
    }

    // callGeminiAPI 메서드 완전 수정
    private String callGeminiAPI(TimelineItem item) throws Exception {
        String apiKey = getGeminiApiKey();

        // 모델명을 gemini-1.5-flash로 변경 (vision 작업에 더 안정적)
        String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        Log.d(TAG, "=== API 호출 URL ===");
        Log.d(TAG, "URL: " + apiUrl.replace(apiKey, "HIDDEN_KEY"));

        // 이미지 Base64 인코딩
        String base64Image = loadAndEncodeImage(item.getPhotoPath());
        if (base64Image == null) {
            Log.e(TAG, "이미지 인코딩 실패");
            throw new Exception("이미지 인코딩 실패");
        }

        Log.d(TAG, "이미지 인코딩 완료, 크기: " + base64Image.length());

        // 프롬프트 생성
        String prompt = createSimplePrompt(item);

        // JSON 요청 생성 - 수정된 구조
        JSONObject requestJson = createGeminiRequest(base64Image, prompt);

        Log.d(TAG, "Gemini API 호출 시작...");

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);

        try {
            // 요청 전송
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(requestJson.toString());
            writer.flush();
            writer.close();

            // 응답 확인
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Gemini 응답 코드: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String responseStr = response.toString();
                Log.d(TAG, "응답 길이: " + responseStr.length());

                // 응답 파싱
                return parseGeminiResponse(responseStr);
            } else {
                // 에러 응답 읽기
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String errorLine;

                while ((errorLine = errorReader.readLine()) != null) {
                    errorResponse.append(errorLine);
                }
                errorReader.close();

                Log.e(TAG, "API 오류 응답: " + errorResponse.toString());
                throw new Exception("API 오류: " + responseCode + " - " + errorResponse.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP 연결 오류: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            throw e;
        } finally {
            connection.disconnect();
        }
    }

    // createGeminiRequest 메서드 - 단일 정의로 통합
    private JSONObject createGeminiRequest(String base64Image, String prompt) throws Exception {
        JSONObject requestJson = new JSONObject();

        // contents 배열
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();

        // parts 배열
        JSONArray parts = new JSONArray();

        // 텍스트 파트
        JSONObject textPart = new JSONObject();
        textPart.put("text", prompt);
        parts.put(textPart);

        // 이미지 파트 - inlineData 형식으로 수정
        JSONObject imagePart = new JSONObject();
        JSONObject inlineData = new JSONObject();
        inlineData.put("mimeType", "image/jpeg");
        inlineData.put("data", base64Image);
        imagePart.put("inlineData", inlineData);
        parts.put(imagePart);

        content.put("parts", parts);
        contents.put(content);
        requestJson.put("contents", contents);

        // 생성 설정 - 2문장에 적합하게 조정
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.5);  // 적당한 창의성
        generationConfig.put("maxOutputTokens", 60);  // 2문장에 적합한 길이
        generationConfig.put("topP", 0.8);
        generationConfig.put("topK", 30);
        requestJson.put("generationConfig", generationConfig);

        return requestJson;
    }

    private String createSimplePrompt(TimelineItem item) {
        StringBuilder prompt = new StringBuilder();
        Random random = new Random();
        int styleChoice = random.nextInt(5); // 0~4 사이의 랜덤 스타일

        prompt.append("이 사진에 대한 짧고 간결한 두~세줄 정도의 스토리 일기를 만들어줘.\n\n");
        prompt.append("참고 정보:\n");

        // 시간 정보
        if (item.getTime() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("a h시 mm분", Locale.KOREAN);
            SimpleDateFormat dayFormat = new SimpleDateFormat("M월 d일 E요일", Locale.KOREAN);
            prompt.append("시간: ").append(dayFormat.format(item.getTime())).append(" ").append(timeFormat.format(item.getTime())).append("\n");
        }

        // 장소 정보
        if (item.getPlaceName() != null && !item.getPlaceName().isEmpty()) {
            prompt.append("장소: ").append(item.getPlaceName()).append("\n");
        } else if (item.getLocation() != null && !item.getLocation().isEmpty() && !item.getLocation().equals("위치 정보 없음")) {
            prompt.append("위치: ").append(item.getLocation()).append("\n");
        }

        // 감지된 객체들
        if (item.getDetectedObjects() != null && !item.getDetectedObjects().isEmpty()) {
            prompt.append("사진 속 요소: ").append(item.getDetectedObjects()).append("\n");
        }

        // 캡션 정보
        if (item.getCaption() != null && !item.getCaption().isEmpty()) {
            prompt.append("AI 설명: ").append(item.getCaption()).append("\n");
        }

        // 공통 가이드라인
        prompt.append("\n스토리 작성 가이드라인:\n");
        prompt.append("- 두 문장으로 짧게 작성해줘. 어울리는 이모지와 함께.\n");
        prompt.append("- '~했다', '~였다', '~이다', '~하다' 같은 간결한 종결어미 사용해줘.\n");
        prompt.append("- 과도한 감성표현이나 오글거리는 표현은 사용하지 마.\n");
        prompt.append("- 날짜와 장소는 문장 중간이나 끝에 자연스럽게 녹여서 간결하게 사용해줘.\n");
        prompt.append("- '와', '대박', '좋았다', '재밌었다', '맛있었다' 같은 간단한 표현 사용해줘.\n");

        // 랜덤 스타일에 따른 추가 지침
        switch (styleChoice) {
            case 0:
                // 단순 사실형
                prompt.append("- 사실만 간결하게 적어줘. (예: 치킨 먹었다. 영화 봤다.)\n");
                prompt.append("- 불필요한 수식어 없이 핵심만 적어줘.\n");
                break;
            case 1:
                // 감각 중심형
                prompt.append("- 맛, 향, 색깔 등 한 가지 감각에 집중해서 짧게 표현해줘. (예: 커피 향 좋았다.)\n");
                prompt.append("- 장소나 시간은 최소한으로만 언급해줘.\n");
                break;
            case 2:
                // 상태 표현형
                prompt.append("- 현재 상태나 기분을 간단히 표현해줘. (예: 피곤하다. 행복하다.)\n");
                prompt.append("- 감정을 단순하고 직관적으로 표현해줘.\n");
                break;
            case 3:
                // 짧은 독백형
                prompt.append("- 매우 짧은 독백체로 적어줘. (예: 좋은 날이다. 대박.)\n");
                prompt.append("- '뭐지?', '왜?', '진짜?' 같은 간단한 의문형도 가능.\n");
                break;
            case 4:
                // 행동 중심형
                prompt.append("- 주요 행동만 간결하게 적어줘. (예: 산책했다. 밥 먹었다.)\n");
                prompt.append("- 동작 중심으로 간단하게 표현해줘.\n");
                break;
        }

        // 중요한 지시사항 추가
        prompt.append("\n특별 지시사항: 반드시 매우 짧은 한 문장으로만 작성하고, 앱 화면에 짤리지 않도록 간결하게 만들어줘. ");
        prompt.append("날짜나 장소 정보는 꼭 필요한 경우에만 간단히 포함해줘. ");
        prompt.append("'~했어', '~구나' 같은 표현 대신 '~했다', '~이다', '대박', '좋다' 같은 매우 간결한 표현을 사용해줘.\n\n");
        prompt.append("스토리:");

        return prompt.toString();
    }
    /**
     * Gemini 응답 파싱
     */
    private String parseGeminiResponse(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);

            if (jsonResponse.has("candidates")) {
                JSONArray candidates = jsonResponse.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);
                    JSONObject content = candidate.getJSONObject("content");
                    JSONArray parts = content.getJSONArray("parts");
                    if (parts.length() > 0) {
                        JSONObject part = parts.getJSONObject(0);
                        return part.getString("text").trim();
                    }
                }
            }

            Log.e(TAG, "Gemini 응답 파싱 실패");
            return null;

        } catch (Exception e) {
            Log.e(TAG, "Gemini 응답 파싱 오류: " + e.getMessage());
            return null;
        }
    }

    /**
     * 이미지 로드 및 Base64 인코딩
     */
    private String loadAndEncodeImage(String imagePath) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(Uri.parse(imagePath));
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) return null;

            Log.d(TAG, "원본 이미지 크기: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            // 리사이징 - 더 작은 크기로
            bitmap = resizeBitmap(bitmap, 512);

            // 품질 낮춤
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            bitmap.recycle();

            // 최대 크기 체크 (3MB 이하로 제한)
            if (imageBytes.length > 3 * 1024 * 1024) {
                Log.w(TAG, "이미지가 너무 큽니다. 다시 리사이징합니다.");
                // 더 작게 리사이징
                inputStream = context.getContentResolver().openInputStream(Uri.parse(imagePath));
                bitmap = BitmapFactory.decodeStream(inputStream);
                bitmap = resizeBitmap(bitmap, 400);
                outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
                imageBytes = outputStream.toByteArray();
                bitmap.recycle();
            }

            Log.d(TAG, "인코딩된 이미지 크기: " + imageBytes.length + " bytes");

            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "이미지 인코딩 실패: " + e.getMessage());
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
     * DB에 타임라인 아이템 업데이트
     */
    private void updateItemInDatabase(TimelineItem item) {
        executor.execute(() -> {
            try {
                PhotoDao photoDao = AppDatabase.getInstance(context).photoDao();
                Photo photo = photoDao.getPhotoByFilePath(item.getPhotoPath());

                if (photo != null) {
                    // 스토리 저장 전 로그
                    Log.d(TAG, "=== DB 업데이트 시작 ===");
                    Log.d(TAG, "기존 스토리: " + photo.story);
                    Log.d(TAG, "새로운 스토리: " + item.getStory());

                    photo.story = item.getStory();

                    // 캡션 저장
                    if (item.getCaption() != null) {
                        photo.caption = item.getCaption();
                    }

                    // 감지된 객체들 저장
                    if (item.getDetectedObjects() != null) {
                        photo.detectedObjects = item.getDetectedObjects();
                    }

                    photoDao.updatePhoto(photo);
                    Log.d(TAG, "DB 업데이트 성공: " + item.getPhotoPath());

                    // 업데이트 후 다시 확인
                    Photo updatedPhoto = photoDao.getPhotoByFilePath(item.getPhotoPath());
                    if (updatedPhoto != null) {
                        Log.d(TAG, "업데이트 확인 - DB에 저장된 스토리: " + updatedPhoto.story);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "DB 업데이트 실패: " + e.getMessage(), e);
            }
        });
    }

    // === Vision API 관련 기존 메서드들 ===

    public void analyzeImageWithVision(String imagePath, VisionAnalysisCallback callback) {
        String apiKey = getVisionApiKey();

        Log.d(TAG, "=== Vision API 호출 시작 [" + imagePath + "] ===");

        if (apiKey == null || apiKey.isEmpty()) {
            Log.e(TAG, "❌ Vision API 키가 없습니다!");
            callback.onAnalysisError(new Exception("API 키가 설정되지 않았습니다."));
            return;
        }

        executor.execute(() -> {
            try {
                Log.d(TAG, "🔄 분석 시작: " + imagePath);

                // 이미지 파일 읽기 - Content URI 지원
                Bitmap bitmap = loadImage(imagePath);

                if (bitmap == null) {
                    Log.e(TAG, "❌ 이미지 로드 실패");
                    callback.onAnalysisError(new Exception("이미지를 읽을 수 없습니다."));
                    return;
                }

                Log.d(TAG, "✅ 이미지 로드 성공: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                // 이미지 처리 및 API 호출
                String base64Image = prepareImageForAPI(bitmap);
                String response = callVisionAPI(base64Image);
                VisionResult result = parseVisionResponse(response);

                // 메인 스레드에서 콜백 실행
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.d(TAG, "✅ 분석 완료 콜백");
                    callback.onAnalysisComplete(result);
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Vision API 분석 실패: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onAnalysisError(e);
                });
            }
        });
    }

    private Bitmap loadImage(String imagePath) {
        try {
            if (imagePath.startsWith("content://")) {
                Log.d(TAG, "Content URI 처리: " + imagePath);
                Uri imageUri = Uri.parse(imagePath);
                InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                if (inputStream != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                    return bitmap;
                }
            } else if (imagePath.startsWith("file://")) {
                Log.d(TAG, "File URI 처리: " + imagePath);
                return BitmapFactory.decodeFile(imagePath.substring(7));
            } else {
                Log.d(TAG, "일반 파일 처리: " + imagePath);
                return BitmapFactory.decodeFile(imagePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "이미지 로드 실패: " + e.getMessage());
        }
        return null;
    }

    private String prepareImageForAPI(Bitmap bitmap) throws Exception {
        // 리사이징
        Bitmap resizedBitmap = resizeBitmap(bitmap, 800);

        // Base64 인코딩
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        // 메모리 해제
        if (bitmap != resizedBitmap) {
            bitmap.recycle();
        }
        resizedBitmap.recycle();

        Log.d(TAG, "이미지 준비 완료: " + base64Image.length() + " bytes");
        return base64Image;
    }

    private String callVisionAPI(String base64Image) throws Exception {
        String apiKey = getVisionApiKey();
        String apiUrl = "https://vision.googleapis.com/v1/images:annotate?key=" + apiKey;

        Log.d(TAG, "📡 Vision API 호출 시작...");

        // JSON 요청 생성
        JSONObject requestJson = createVisionRequest(base64Image);
        String requestBody = requestJson.toString();

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);

        try {
            // 요청 전송
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(requestBody);
            writer.flush();
            writer.close();

            // 응답 확인
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "🌐 응답 코드: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String result = response.toString();
                Log.d(TAG, "✅ API 응답 수신: " + result.length() + " bytes");
                return result;
            } else {
                throw new Exception("Vision API 오류: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject createVisionRequest(String base64Image) throws Exception {
        JSONObject requestJson = new JSONObject();
        JSONArray requests = new JSONArray();
        JSONObject request = new JSONObject();

        // 이미지 데이터
        JSONObject image = new JSONObject();
        image.put("content", base64Image);
        request.put("image", image);

        // 요청 기능
        JSONArray features = new JSONArray();

        // 객체 감지
        JSONObject objectDetection = new JSONObject();
        objectDetection.put("type", "OBJECT_LOCALIZATION");
        objectDetection.put("maxResults", 10);
        features.put(objectDetection);

        // 라벨 감지
        JSONObject labelDetection = new JSONObject();
        labelDetection.put("type", "LABEL_DETECTION");
        labelDetection.put("maxResults", 10);
        features.put(labelDetection);

        request.put("features", features);
        requests.put(request);
        requestJson.put("requests", requests);

        return requestJson;
    }

    private VisionResult parseVisionResponse(String response) throws Exception {
        JSONObject responseJson = new JSONObject(response);
        JSONArray responses = responseJson.getJSONArray("responses");

        if (responses.length() == 0) {
            throw new Exception("응답 데이터 없음");
        }

        JSONObject imageResponse = responses.getJSONObject(0);

        // 에러 체크
        if (imageResponse.has("error")) {
            JSONObject error = imageResponse.getJSONObject("error");
            throw new Exception("Vision API 오류: " + error.getString("message"));
        }

        // 결과 파싱
        List<String> detectedObjects = parseObjects(imageResponse);
        List<String> labels = parseLabels(imageResponse);
        String caption = generateCaptionFromLabels(labels);

        Log.d(TAG, "파싱 결과 - 객체: " + detectedObjects.size() + "개, 라벨: " + labels.size() + "개");

        return new VisionResult(detectedObjects, caption);
    }

    private List<String> parseObjects(JSONObject imageResponse) throws Exception {
        List<String> detectedObjects = new ArrayList<>();

        if (imageResponse.has("localizedObjectAnnotations")) {
            JSONArray objects = imageResponse.getJSONArray("localizedObjectAnnotations");
            for (int i = 0; i < objects.length(); i++) {
                JSONObject obj = objects.getJSONObject(i);
                String name = obj.getString("name");
                double score = obj.getDouble("score");
                if (score > 0.5) {
                    detectedObjects.add(name);
                    Log.d(TAG, "객체: " + name + " (" + score + ")");
                }
            }
        }

        return detectedObjects;
    }

    private List<String> parseLabels(JSONObject imageResponse) throws Exception {
        List<String> labels = new ArrayList<>();

        if (imageResponse.has("labelAnnotations")) {
            JSONArray labelArray = imageResponse.getJSONArray("labelAnnotations");
            for (int i = 0; i < labelArray.length(); i++) {
                JSONObject label = labelArray.getJSONObject(i);
                String description = label.getString("description");
                double score = label.getDouble("score");
                if (score > 0.5) {
                    labels.add(description);
                    Log.d(TAG, "라벨: " + description + " (" + score + ")");
                }
            }
        }

        return labels;
    }

    private String generateCaptionFromLabels(List<String> labels) {
        if (labels.isEmpty()) {
            return "이미지를 분석했습니다.";
        }

        String primaryLabel = labels.get(0);
        if (labels.size() > 1) {
            return String.format("%s와 %s가 있는 이미지입니다.", primaryLabel, labels.get(1));
        } else {
            return String.format("%s가 있는 이미지입니다.", primaryLabel);
        }
    }

    // Vision 분석 결과를 위한 도우미 클래스
    public static class VisionResult {
        private final List<String> detectedObjects;
        private final String caption;

        public VisionResult(List<String> detectedObjects, String caption) {
            this.detectedObjects = detectedObjects;
            this.caption = caption;
        }

        public List<String> getDetectedObjects() {
            return detectedObjects;
        }

        public String getCaption() {
            return caption;
        }
    }

    // Vision 분석 콜백 인터페이스
    public interface VisionAnalysisCallback {
        void onAnalysisComplete(VisionResult result);
        void onAnalysisError(Exception e);
    }

    // 스토리 생성 리스너 설정
    public void setOnStoryGeneratedListener(OnStoryGeneratedListener listener) {
        this.onStoryGeneratedListener = listener;
    }

    // 현재 타임라인 아이템 가져오기
    public List<TimelineItem> getCurrentTimelineItems() {
        return new ArrayList<>(currentTimelineItems); // 복사본 반환
    }

    // 특정 아이템의 스토리 다시 생성
    public void regenerateStoryForItem(TimelineItem item, OnStoryGeneratedListener listener) {
        // 기존 스토리 제거
        item.setStory(null);

        // 새로운 스토리 생성
        generateStoryForItem(item);

        // 완료 알림
        executor.execute(() -> {
            // 스토리 생성이 완료될 때까지 대기
            int maxRetries = 10;
            for (int i = 0; i < maxRetries; i++) {
                if (item.getStory() != null && !item.getStory().isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (listener != null) {
                            listener.onStoryGenerated(currentTimelineItems);
                        }
                    });
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "재생성 대기 중 인터럽트", e);
                }
            }
        });
    }

    // 테스트용 API 호출 메서드 (디버깅용)
    private void testGeminiApiCall() {
        executor.execute(() -> {
            try {
                String apiKey = getGeminiApiKey();
                String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

                // 간단한 텍스트 전용 테스트
                JSONObject testRequest = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject textPart = new JSONObject();
                textPart.put("text", "간단한 한국어 문장을 하나 작성해주세요.");
                parts.put(textPart);
                content.put("parts", parts);
                contents.put(content);
                testRequest.put("contents", contents);

                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(testRequest.toString());
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "테스트 API 응답 코드: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "✅ API 키가 정상적으로 작동합니다");
                } else {
                    Log.e(TAG, "❌ API 키 또는 설정에 문제가 있습니다");
                }

                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "테스트 API 호출 실패: " + e.getMessage());
            }
        });
    }

    // TimelineManager.java에 추가
    // In com.example.wakey.data.repository.TimelineManager.java, modify the updateTimelineItem method:

    // TimelineManager.java의 updateTimelineItem 메서드 수정

    public void updateTimelineItem(TimelineItem updatedItem) {
        Log.d(TAG, "🔄 updateTimelineItem 호출됨: " + updatedItem.getPhotoPath());
        Log.d(TAG, "🔄 storyAdapter 상태: " + (storyAdapter != null ? "설정됨" : "설정되지 않음"));
        Log.d(TAG, "🔄 업데이트 전 스토리: " + updatedItem.getStory());

        // 1. 기존 아이템 찾기 및 업데이트
        boolean itemFound = false;
        for (int i = 0; i < currentTimelineItems.size(); i++) {
            TimelineItem item = currentTimelineItems.get(i);
            if (item.getPhotoPath() != null &&
                    item.getPhotoPath().equals(updatedItem.getPhotoPath())) {
                // 스토리 필드 직접 업데이트
                Log.d(TAG, "🔄 아이템 찾음, 인덱스: " + i);

                // 완전히 새 인스턴스로 교체
                currentTimelineItems.set(i, updatedItem);
                itemFound = true;
                break;
            }
        }

        if (!itemFound) {
            Log.e(TAG, "❌ 업데이트할 아이템을 찾을 수 없음: " + updatedItem.getPhotoPath());
        }

        // 2. DB 업데이트 확인
        executor.execute(() -> {
            try {
                PhotoDao photoDao = AppDatabase.getInstance(context).photoDao();
                Photo photo = photoDao.getPhotoByFilePath(updatedItem.getPhotoPath());

                if (photo != null) {
                    String dbStory = photo.story;
                    String newStory = updatedItem.getStory();

                    Log.d(TAG, "🔄 DB 확인 - 파일: " + updatedItem.getPhotoPath());
                    Log.d(TAG, "🔄 DB 스토리: " + (dbStory != null ? dbStory : "null"));
                    Log.d(TAG, "🔄 새 스토리: " + (newStory != null ? newStory : "null"));

                    // DB에 저장되지 않았으면 저장
                    if (newStory != null && !newStory.equals(dbStory)) {
                        Log.d(TAG, "🔄 스토리 DB 업데이트 필요");
                        int updated = photoDao.updateStory(updatedItem.getPhotoPath(), newStory);
                        Log.d(TAG, "🔄 DB 업데이트 결과: " + updated + "행");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ DB 확인 중 오류: " + e.getMessage(), e);
            }
        });

        // 3. 어댑터 업데이트
        if (storyAdapter != null) {
            // UI 스레드에서 실행
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Log.d(TAG, "🔄 storyAdapter.updateItem 호출");
                    storyAdapter.updateItem(updatedItem);

                    // 전체 데이터 갱신도 함께
                    Log.d(TAG, "🔄 storyAdapter.updateItems 호출");
                    storyAdapter.updateItems(currentTimelineItems);
                } catch (Exception e) {
                    Log.e(TAG, "❌ 어댑터 업데이트 중 오류: " + e.getMessage(), e);
                }
            });
        } else {
            Log.e(TAG, "❌ storyAdapter가 설정되지 않았습니다");
        }
    }
}