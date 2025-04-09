// path: com.example.wakey/service/ClusterService.java

package com.example.wakey.service;

import android.content.Context;
import android.util.Log;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.PhotoRepository;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.android.gms.maps.GoogleMap;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClusterService {
    private static final String TAG = "ClusterService";
    private static ClusterService instance;

    private Context context;
    private PhotoRepository photoRepository;

    // 클러스터링 관련 설정값
    private static final double DEFAULT_CLUSTER_RADIUS_IN_METERS = 100.0; // 기본 클러스터 반경 (미터)

    private ClusterService(Context context) {
        this.context = context.getApplicationContext();
        this.photoRepository = PhotoRepository.getInstance(context);
    }

    public static synchronized ClusterService getInstance(Context context) {
        if (instance == null) {
            instance = new ClusterService(context);
        }
        return instance;
    }

    /**
     * 특정 날짜의 사진들을 위치 기반으로 클러스터링
     *
     * @param dateString 날짜 (yyyy-MM-dd 형식)
     * @param radiusInMeters 클러스터링 반경 (미터)
     * @return 클러스터링 결과 Map (위치 -> 해당 위치의 사진 목록)
     */
    public Map<LatLng, List<PhotoInfo>> clusterPhotosByLocation(String dateString, double radiusInMeters) {
        List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
        if (photos == null || photos.isEmpty()) {
            return new HashMap<>();
        }

        Map<LatLng, List<PhotoInfo>> clusters = new HashMap<>();

        // 각 사진에 대해
        for (PhotoInfo photo : photos) {
            LatLng latLng = photo.getLatLng();
            if (latLng == null) {
                Log.w(TAG, "위치 정보가 없는 사진입니다: " + photo.getFilePath());
                continue;
            }

            boolean addedToCluster = false;

            // 기존 클러스터에 추가할 수 있는지 검사
            for (Map.Entry<LatLng, List<PhotoInfo>> entry : clusters.entrySet()) {
                LatLng clusterCenter = entry.getKey();

                // 현재 사진이 기존 클러스터 반경 내에 있는지 계산
                float[] results = new float[1];
                android.location.Location.distanceBetween(
                        clusterCenter.latitude, clusterCenter.longitude,
                        latLng.latitude, latLng.longitude,
                        results);

                if (results[0] <= radiusInMeters) {
                    // 기존 클러스터에 추가
                    entry.getValue().add(photo);
                    addedToCluster = true;
                    break;
                }
            }

            // 기존 클러스터에 추가되지 않았으면 새 클러스터 생성
            if (!addedToCluster) {
                List<PhotoInfo> newCluster = new ArrayList<>();
                newCluster.add(photo);
                clusters.put(latLng, newCluster);
            }
        }

        return clusters;
    }

    /**
     * 클러스터링된 사진들을 기반으로 타임라인 항목 생성
     *
     * @param dateString 날짜 (yyyy-MM-dd 형식)
     * @return 타임라인 항목 리스트 (시간순 정렬)
     */
    public List<TimelineItem> generateTimelineFromPhotos(String dateString) {
        // 📍 로그: 위치 정보 확인용
        for (PhotoInfo photo : photoRepository.getAllPhotos()) {
            String fullAddress = photo.getLocationDo() + " " +
                    photo.getLocationGu() + " " +
                    photo.getLocationStreet();
            android.util.Log.d("📍주소확인", "불러온 주소: " + fullAddress);
        }

        List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
        if (photos == null || photos.isEmpty()) {
            return new ArrayList<>();
        }

        // 시간별로 정렬
        Collections.sort(photos, new Comparator<PhotoInfo>() {
            @Override
            public int compare(PhotoInfo p1, PhotoInfo p2) {
                return p1.getDateTaken().compareTo(p2.getDateTaken());
            }
        });

        // 사진들을 시간순으로 타임라인 항목으로 변환
        List<TimelineItem> timelineItems = new ArrayList<>();

        for (PhotoInfo photo : photos) {
            // 위치 정보가 있는 사진만 추가
            LatLng latLng = photo.getLatLng();
            if (latLng != null) {
                // 시간대별 활동 추론
                String activityType = inferActivityByTime(photo.getDateTaken());

                // 타임라인 항목 생성
                TimelineItem item = new TimelineItem.Builder()
                        .setTime(photo.getDateTaken())
                        .setLocation(photo.getPlaceName() != null ? photo.getPlaceName() : "미상")
                        .setPhotoPath(photo.getFilePath())
                        .setLatLng(latLng)
                        .setDescription(generateDescription(photo))
                        .setActivityType(activityType)
                        .build();

                timelineItems.add(item);
            } else {
                Log.w(TAG, "위치 정보 없는 사진 제외됨 (타임라인): " + photo.getFilePath());
            }
        }

        return timelineItems;
    }

    /**
     * 사진 정보를 기반으로 설명 생성
     *
     * @param photo 사진 정보
     * @return 생성된 설명 문자열
     */
    private String generateDescription(PhotoInfo photo) {
        // 간단한 설명 생성 (실제 구현에서는 더 복잡한 로직 필요)
        StringBuilder description = new StringBuilder();

        // 시간 정보 추가
        Calendar cal = Calendar.getInstance();
        cal.setTime(photo.getDateTaken());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        description.append(String.format("%02d:%02d", hour, minute));

        // 장소 정보가 있으면 추가
        if (photo.getPlaceName() != null && !photo.getPlaceName().isEmpty()) {
            description.append("에 ").append(photo.getPlaceName()).append("에서 ");
        } else {
            description.append("에 ");
        }

        // 활동 정보 추가
        description.append(inferActivityByTime(photo.getDateTaken())).append(" 중에 촬영한 사진");

        return description.toString();
    }

    /**
     * 시간대에 따른 활동 유형 추론
     *
     * @param time 시간
     * @return 추론된 활동 유형
     */
    private String inferActivityByTime(Date time) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // 시간대 기반 활동 추론
        if (hour >= 6 && hour < 9) {
            return "아침 식사";
        } else if (hour >= 9 && hour < 12) {
            return "오전 관광";
        } else if (hour >= 12 && hour < 14) {
            return "점심 식사";
        } else if (hour >= 14 && hour < 18) {
            return "오후 관광";
        } else if (hour >= 18 && hour < 21) {
            return "저녁 식사";
        } else if (hour >= 21) {
            return "야간 활동";
        }

        return "여행";  // 기본값
    }

    /**
     * 특정 날짜의 사진들로 이동 경로 생성
     *
     * @param dateString 날짜 (yyyy-MM-dd 형식)
     * @return 경로 좌표 리스트 (시간순 정렬)
     */
    public List<LatLng> generateRouteForDate(String dateString) {
        List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
        if (photos == null || photos.isEmpty()) {
            return new ArrayList<>();
        }

        // 사진을 시간순으로 정렬
        Collections.sort(photos, new Comparator<PhotoInfo>() {
            @Override
            public int compare(PhotoInfo p1, PhotoInfo p2) {
                return p1.getDateTaken().compareTo(p2.getDateTaken());
            }
        });

        // 경로 생성
        List<LatLng> route = new ArrayList<>();
        for (PhotoInfo photo : photos) {
            LatLng latLng = photo.getLatLng();
            if (latLng != null) {
                route.add(latLng);
            } else {
                Log.w(TAG, "위치 정보 없는 사진 제외됨 (경로): " + photo.getFilePath());
            }
        }

        return route;
    }
}
