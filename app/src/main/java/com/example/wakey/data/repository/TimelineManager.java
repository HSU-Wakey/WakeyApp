// path: com.example.wakey/data/repository/TimelineManager.java

package com.example.wakey.data.repository;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.wakey.MainActivity;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.manager.UIManager;
import com.example.wakey.service.ClusterService;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import com.example.wakey.ui.timeline.StoryAdapter;
/**
 * 타임라인 데이터 관리 클래스
 */
public class TimelineManager {
    private static TimelineManager instance;
    private final Context context;
    private final ClusterService clusterService;

    private List<TimelineItem> currentTimelineItems = new ArrayList<>();
    private String currentDate;

    private StoryAdapter storyAdapter;

    public void setStoryAdapter(StoryAdapter adapter) {
        Log.d(TAG, "setStoryAdapter 호출됨");
        this.storyAdapter = adapter;
        Log.d(TAG, "storyAdapter 설정 완료: " + (this.storyAdapter != null ? "성공" : "실패"));
    }
    private TimelineManager(Context context) {
        this.context = context.getApplicationContext();
        this.clusterService = ClusterService.getInstance(context);
    }

    public static synchronized TimelineManager getInstance(Context context) {
        if (instance == null) {
            instance = new TimelineManager(context);
        }
        return instance;
    }

    /**
     * 특정 날짜의 타임라인 데이터 로드
     *
     * @param dateString 날짜 (yyyy-MM-dd 형식)
     * @return 타임라인 항목 리스트
     */
    public List<TimelineItem> loadTimelineForDate(String dateString) {
        currentDate = dateString;
        currentTimelineItems = clusterService.generateTimelineFromPhotos(dateString);
        return currentTimelineItems;
    }

    /**
     * 현재 로드된 타임라인 항목 가져오기
     *
     * @return 타임라인 항목 리스트
     */
    public List<TimelineItem> getCurrentTimelineItems() {
        return currentTimelineItems;
    }

    /**
     * 새로운 타임라인 항목 추가
     *
     * @param item 추가할 타임라인 항목
     */
    public void addTimelineItem(TimelineItem item) {
        if (item != null) {
            currentTimelineItems.add(item);
            // 여기에 로컬 DB에 저장하는 코드를 추가할 수 있음
        }
    }

    /**
     * 타임라인 항목 업데이트
     *
     * @param updatedItem 업데이트할 타임라인 항목
     */
    // TimelineManager.java의 updateTimelineItem 메서드 수정
    public void updateTimelineItem(TimelineItem updatedItem) {
        Log.d(TAG, "updateTimelineItem 호출됨: " + updatedItem.getPhotoPath());
        Log.d(TAG, "storyAdapter 상태: " + (storyAdapter != null ? "설정됨" : "설정되지 않음"));

        if (storyAdapter != null) {
            // 스토리 어댑터 업데이트
            storyAdapter.updateItem(updatedItem);

            // UI 스레드에서 전체 목록 갱신 및 탭 전환
            new Handler(Looper.getMainLooper()).post(() -> {
                // 전체 목록 갱신
                storyAdapter.updateItems(currentTimelineItems);

                // 스토리 탭으로 전환 (UIManager 통해)
                if (context instanceof MainActivity) {
                    UIManager.getInstance(context).switchToStoryTab();
                }
            });

            Log.d(TAG, "기존 스토리: " + updatedItem.getStory());
            Log.d(TAG, "새로운 스토리: " + updatedItem.getStory());
            Log.d(TAG, "업데이트 확인 - DB에 저장된 스토리: " + updatedItem.getStory());
        } else {
            Log.e(TAG, "storyAdapter가 설정되지 않았습니다.");
        }
    }

    // 타임라인 생성할 때, 객체 인식 결과도 함께 넣기
    public List<TimelineItem> buildTimelineWithObjects(List<PhotoInfo> photos) {
        List<TimelineItem> items = new ArrayList<>();

        for (PhotoInfo photo : photos) {
            LatLng latLng = null;

            // 로그 추가
            if (photo.getLatLng() != null) {
                latLng = photo.getLatLng();
                Log.d("LATLNG_CHECK", "📍 위도: " + latLng.latitude + ", 경도: " + latLng.longitude);
                Log.d("LATLNG_CHECK", "✅ 유효한 LatLng 생성됨: " + latLng.toString());
            } else {
                Log.w("LATLNG_CHECK", "⚠️ 유효하지 않은 위치 → null 처리됨");
            }

            // 위치 정보 우선순위: address → "위치 정보 없음"
            String location = (photo.getAddress() != null && !photo.getAddress().isEmpty())
                    ? photo.getAddress()
                    : "위치 정보 없음";
            String description = "";

            List<String> objects = new ArrayList<>();
            if (photo.getObjects() != null && !photo.getObjects().isEmpty()) {
                objects = photo.getObjects();
                description = "📌 " + String.join(", ", objects);
            }

            TimelineItem item = new TimelineItem(
                    photo.getDateTaken(),
                    location,
                    photo.getFilePath(),
                    latLng,
                    description
            );

            item.setDetectedObjects(String.valueOf(objects));
            item.setLatLng(latLng); // 🔥 LatLng 재설정 (getLatLng() 내부에서 latitude/longitude도 업데이트됨)
            items.add(item);
        }

        return items;
    }

}