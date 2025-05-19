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