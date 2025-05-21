package com.example.wakey.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.PhotoRepository;
import com.example.wakey.data.repository.TimelineManager;
import com.example.wakey.service.ClusterService;
import com.example.wakey.service.SearchService;
import com.example.wakey.util.ToastManager;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DataManager {
    private static final String TAG = "DataManager";
    private static DataManager instance;

    private Context context;
    private PhotoRepository photoRepository;
    private TimelineManager timelineManager;
    private ClusterService clusterService;
    private SearchService searchService;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnDataLoadedListener {
        void onPhotosLoaded(List<PhotoInfo> photos, Map<LatLng, List<PhotoInfo>> clusters);
        void onTimelineLoaded(List<TimelineItem> timelineItems);
        void onRouteGenerated(List<LatLng> route);
    }

    public interface OnSearchResultListener {
        void onDateSearchResult(Date date);
        void onLocationSearchResult(LatLng location, String name);
        void onSearchFailed(String query);
    }

    private DataManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }

    public void init(Context context) {
        this.photoRepository = PhotoRepository.getInstance(context);
        this.timelineManager = TimelineManager.getInstance(context);
        this.clusterService = ClusterService.getInstance(context);
        this.searchService = SearchService.getInstance(context);
    }

    public void loadPhotosForDate(String dateString, OnDataLoadedListener listener) {
        if (photoRepository == null || listener == null) return;

        //백그라운드에서 접근해야 오류 안난대
        new Thread(() -> {
            List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);

            if (photos != null && !photos.isEmpty()) {
                Map<LatLng, List<PhotoInfo>> clusters = clusterService.clusterPhotosByLocation(dateString, 100.0);
                List<TimelineItem> timelineItems = timelineManager.loadTimelineForDate(dateString);
                List<LatLng> route = clusterService.generateRouteForDate(dateString);

                mainHandler.post(() -> {
                    listener.onPhotosLoaded(photos, clusters);
                    listener.onTimelineLoaded(timelineItems);
                    listener.onRouteGenerated(route);
                });
            } else {
                mainHandler.post(() -> {
                    listener.onPhotosLoaded(new ArrayList<>(), new HashMap<>());
                    listener.onTimelineLoaded(new ArrayList<>());
                    listener.onRouteGenerated(new ArrayList<>());
                    ToastManager.getInstance().showToast("이 날짜에 사진이 없습니다");
                });
            }
        }).start();
    }

    public void loadAllPhotosToMap(OnDataLoadedListener listener) {
        if (photoRepository == null || listener == null) return;

        new Thread(() -> {
            List<String> availableDates = photoRepository.getAvailableDates();
            List<PhotoInfo> allPhotos = new ArrayList<>();
            Map<LatLng, List<PhotoInfo>> allClusters = new HashMap<>();

            for (String dateString : availableDates) {
                List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
                if (photos != null && !photos.isEmpty()) {
                    allPhotos.addAll(photos);

                    Map<LatLng, List<PhotoInfo>> clusters = clusterService.clusterPhotosByLocation(dateString, 100.0);
                    allClusters.putAll(clusters);
                }
            }

            mainHandler.post(() -> {
                if (!allPhotos.isEmpty()) {
                    listener.onPhotosLoaded(allPhotos, allClusters);
                } else {
                    listener.onPhotosLoaded(new ArrayList<>(), new HashMap<>());
                    ToastManager.getInstance().showToast("사진을 찾을 수 없습니다");
                }
            });
        }).start();
    }

    public void performSearch(String query, GoogleMap map, OnSearchResultListener listener) {
        if (searchService == null || listener == null) return;

        SearchService.SearchResult result = searchService.search(query, map);

        if (result.isSuccess()) {
            if (result.isDateSearch()) {
                Date searchDate = result.getDate();
                listener.onDateSearchResult(searchDate);
            } else if (result.isLocationSearch()) {
                LatLng location = result.getLocation();
                String name = result.getLocationName();
                listener.onLocationSearchResult(location, name);
            }
        } else {
            listener.onSearchFailed(query);
        }
    }

    public TimelineItem findTimelineItemForPhoto(String photoPath, List<TimelineItem> timelineItems) {
        if (photoPath == null || timelineItems == null) return null;

        for (TimelineItem item : timelineItems) {
            if (item.getPhotoPath() != null && item.getPhotoPath().equals(photoPath)) {
                return item;
            }
        }
        return null;
    }
    // DataManager.java에 다음 메서드 추가
    public void loadPhotosForDateRange(String dateRangeStr, OnDataLoadedListener listener) {
        if (photoRepository == null || listener == null) return;

        String[] dates = dateRangeStr.split(":");
        if (dates.length != 2) {
            // 잘못된 형식이면 단일 날짜로 처리
            loadPhotosForDate(dateRangeStr, listener);
            return;
        }

        String startDateStr = dates[0];
        String endDateStr = dates[1];

        // 백그라운드에서 실행
        new Thread(() -> {
            try {
                // 날짜 문자열을 Date 객체로 변환
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date startDate = dateFormat.parse(startDateStr);
                Date endDate = dateFormat.parse(endDateStr);

                // 종료일의 끝 시간으로 설정 (23:59:59)
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endDate);
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                endDate = calendar.getTime();

                // 시작일부터 종료일까지의 모든 사진 가져오기
                List<PhotoInfo> allPhotos = new ArrayList<>();
                List<TimelineItem> allTimelineItems = new ArrayList<>();
                List<LatLng> allRoutePoints = new ArrayList<>();
                Map<LatLng, List<PhotoInfo>> allClusters = new HashMap<>();

                // 시작일부터 종료일까지 날짜별로 데이터 로드
                Calendar currentDate = Calendar.getInstance();
                currentDate.setTime(startDate);

                while (!currentDate.getTime().after(endDate)) {
                    String currentDateStr = dateFormat.format(currentDate.getTime());

                    // 현재 날짜의 사진 데이터 가져오기
                    List<PhotoInfo> photos = photoRepository.getPhotosForDate(currentDateStr);
                    if (photos != null && !photos.isEmpty()) {
                        allPhotos.addAll(photos);

                        // 현재 날짜의 클러스터 데이터 가져오기
                        Map<LatLng, List<PhotoInfo>> clusters = clusterService.clusterPhotosByLocation(currentDateStr, 100.0);
                        allClusters.putAll(clusters);

                        // 현재 날짜의 타임라인 데이터 가져오기
                        List<TimelineItem> timelineItems = timelineManager.loadTimelineForDate(currentDateStr);
                        allTimelineItems.addAll(timelineItems);

                        // 현재 날짜의 경로 데이터 가져오기
                        List<LatLng> routePoints = clusterService.generateRouteForDate(currentDateStr);
                        allRoutePoints.addAll(routePoints);
                    }

                    // 다음 날짜로 이동
                    currentDate.add(Calendar.DAY_OF_MONTH, 1);
                }

                // 결과 반환
                final List<PhotoInfo> finalPhotos = allPhotos;
                final Map<LatLng, List<PhotoInfo>> finalClusters = allClusters;
                final List<TimelineItem> finalTimelineItems = allTimelineItems;
                final List<LatLng> finalRoutePoints = allRoutePoints;

                mainHandler.post(() -> {
                    if (!finalPhotos.isEmpty()) {
                        listener.onPhotosLoaded(finalPhotos, finalClusters);
                        listener.onTimelineLoaded(finalTimelineItems);
                        listener.onRouteGenerated(finalRoutePoints);
                    } else {
                        listener.onPhotosLoaded(new ArrayList<>(), new HashMap<>());
                        listener.onTimelineLoaded(new ArrayList<>());
                        listener.onRouteGenerated(new ArrayList<>());
                        ToastManager.getInstance().showToast("해당 기간에 사진이 없습니다");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    listener.onPhotosLoaded(new ArrayList<>(), new HashMap<>());
                    listener.onTimelineLoaded(new ArrayList<>());
                    listener.onRouteGenerated(new ArrayList<>());
                    ToastManager.getInstance().showToast("날짜 로딩 중 오류가 발생했습니다");
                });
            }
        }).start();
    }
    public List<String> getAvailableDates() {
        if (photoRepository == null) return new ArrayList<>();
        return photoRepository.getAvailableDates();
    }

    public List<PhotoInfo> getAllPhotoInfo() {
        if (photoRepository == null) return new ArrayList<>();
        return photoRepository.getAllPhotos();
    }
}
