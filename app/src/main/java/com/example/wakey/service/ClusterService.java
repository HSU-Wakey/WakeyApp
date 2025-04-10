package com.example.wakey.service;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.PhotoRepository;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClusterService {
    private static final String TAG = "ClusterService";
    private static ClusterService instance;

    private Context context;
    private PhotoRepository photoRepository;

    private static final double DEFAULT_CLUSTER_RADIUS_IN_METERS = 100.0;

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

    public Map<LatLng, List<PhotoInfo>> clusterPhotosByLocation(String dateString, double radiusInMeters) {
        List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
        if (photos == null || photos.isEmpty()) {
            return new HashMap<>();
        }

        Map<LatLng, List<PhotoInfo>> clusters = new HashMap<>();

        for (PhotoInfo photo : photos) {
            LatLng latLng = photo.getLatLng();
            if (latLng == null) {
                Log.w(TAG, "위치 정보가 없는 사진입니다: " + photo.getFilePath());
                continue;
            }

            boolean addedToCluster = false;

            for (Map.Entry<LatLng, List<PhotoInfo>> entry : clusters.entrySet()) {
                LatLng clusterCenter = entry.getKey();
                float[] results = new float[1];
                android.location.Location.distanceBetween(
                        clusterCenter.latitude, clusterCenter.longitude,
                        latLng.latitude, latLng.longitude,
                        results);

                if (results[0] <= radiusInMeters) {
                    entry.getValue().add(photo);
                    addedToCluster = true;
                    break;
                }
            }

            if (!addedToCluster) {
                List<PhotoInfo> newCluster = new ArrayList<>();
                newCluster.add(photo);
                clusters.put(latLng, newCluster);
            }
        }

        return clusters;
    }

    public List<TimelineItem> generateTimelineFromPhotos(String dateString) {
        List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
        if (photos == null || photos.isEmpty()) {
            return new ArrayList<>();
        }

        Collections.sort(photos, new Comparator<PhotoInfo>() {
            @Override
            public int compare(PhotoInfo p1, PhotoInfo p2) {
                return p1.getDateTaken().compareTo(p2.getDateTaken());
            }
        });

        List<TimelineItem> timelineItems = new ArrayList<>();

        for (PhotoInfo photo : photos) {
            LatLng latLng = photo.getLatLng();
            if (latLng != null) {
                String activityType = inferActivityByTime(photo.getDateTaken());
                TimelineItem item = new TimelineItem.Builder()
                        .setTime(photo.getDateTaken())
                        .setLocation(photo.getPlaceName() != null ? photo.getPlaceName() : "미상")
                        .setPhotoPath(photo.getFilePath())
                        .setLatLng(latLng)
                        .setDescription(generateDescription(photo))
                        .setActivityType(activityType)
                        .setDetectedObjectPairs(
                                photo.getDetectedObjectPairs() != null ?
                                        photo.getDetectedObjectPairs().stream()
                                                .collect(Collectors.toMap(
                                                        pair -> pair.first,
                                                        pair -> pair.second
                                                ))
                                        : new HashMap<>()
                        )
                        .build();


            } else {
                Log.w(TAG, "위치 정보 없는 사진 제외됨 (타임라인): " + photo.getFilePath());
            }
        }

        return timelineItems;
    }

    private String generateDescription(PhotoInfo photo) {
        StringBuilder description = new StringBuilder();
        Calendar cal = Calendar.getInstance();
        cal.setTime(photo.getDateTaken());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        description.append(String.format("%02d:%02d", hour, minute));

        if (photo.getPlaceName() != null && !photo.getPlaceName().isEmpty()) {
            description.append("에 ").append(photo.getPlaceName()).append("에서 ");
        } else {
            description.append("에 ");
        }

        description.append(inferActivityByTime(photo.getDateTaken())).append(" 중에 촬영한 사진");

        return description.toString();
    }

    private String inferActivityByTime(Date time) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        if (hour >= 6 && hour < 9) return "아침 식사";
        else if (hour >= 9 && hour < 12) return "오전 관광";
        else if (hour >= 12 && hour < 14) return "점심 식사";
        else if (hour >= 14 && hour < 18) return "오후 관광";
        else if (hour >= 18 && hour < 21) return "저녁 식사";
        else if (hour >= 21) return "야간 활동";

        return "여행";
    }

    public List<LatLng> generateRouteForDate(String dateString) {
        List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
        if (photos == null || photos.isEmpty()) {
            return new ArrayList<>();
        }

        Collections.sort(photos, new Comparator<PhotoInfo>() {
            @Override
            public int compare(PhotoInfo p1, PhotoInfo p2) {
                return p1.getDateTaken().compareTo(p2.getDateTaken());
            }
        });

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