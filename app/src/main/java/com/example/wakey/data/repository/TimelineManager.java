// path: com.example.wakey/data/repository/TimelineManager.java

package com.example.wakey.data.repository;

import android.content.Context;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.service.ClusterService;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 타임라인 데이터 관리 클래스
 */
public class TimelineManager {
    private static TimelineManager instance;
    private final Context context;
    private final ClusterService clusterService;

    private List<TimelineItem> currentTimelineItems = new ArrayList<>();
    private String currentDate;

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
     * @param item 업데이트할 타임라인 항목
     */
    public void updateTimelineItem(TimelineItem item) {
        if (item != null) {
            // 기존 항목 찾기
            for (int i = 0; i < currentTimelineItems.size(); i++) {
                if (currentTimelineItems.get(i).getTime().equals(item.getTime()) &&
                        currentTimelineItems.get(i).getPhotoPath() != null &&
                        currentTimelineItems.get(i).getPhotoPath().equals(item.getPhotoPath())) {
                    // 기존 항목 업데이트
                    currentTimelineItems.set(i, item);
                    break;
                }
            }
        }
    }

    // 타임라인 생성할 때, 객체 인식 결과도 함께 넣기
    public List<TimelineItem> buildTimelineWithObjects(List<PhotoInfo> photos) {
        List<TimelineItem> items = new ArrayList<>();

        for (PhotoInfo photo : photos) {
            LatLng latLng = photo.getLatLng();
            String location = photo.getAddress() != null ? photo.getAddress() : "위치 정보 없음";
            String description = "";  // 기본값

            List<String> objects = new ArrayList<>();
            if (photo.getObjects() != null && !photo.getObjects().isEmpty()) {
                objects = photo.getObjects();  // ✅ 리스트 그대로 사용
                description = "📌 " + String.join(", ", objects);  // ✅ 문자열로 이어 붙이기
            }

            TimelineItem item = new TimelineItem(
                    photo.getDateTaken(),
                    location,
                    photo.getFilePath(),
                    latLng,
                    description
            );

            item.setDetectedObjects(objects); // ✅ 객체 리스트 저장
            items.add(item);
        }

        return items;
    }

}