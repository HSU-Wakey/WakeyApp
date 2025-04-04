package com.example.wakey.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.PhotoRepository;
import com.example.wakey.ui.timeline.TimelineManager;
import com.example.wakey.service.ClusterService;
import com.example.wakey.service.SearchService;
import com.example.wakey.util.ToastManager;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

    // ✅ 날짜별 캐시 저장소
    private final Map<String, List<TimelineItem>> timelineCache = new HashMap<>();
    private final Map<String, List<PhotoInfo>> photoCache = new HashMap<>();
    private final Map<String, Map<LatLng, List<PhotoInfo>>> clusterCache = new HashMap<>();
    private final Map<String, List<LatLng>> routeCache = new HashMap<>();

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

        // ✅ 캐시 HIT: 바로 응답
        if (timelineCache.containsKey(dateString) &&
                photoCache.containsKey(dateString) &&
                clusterCache.containsKey(dateString) &&
                routeCache.containsKey(dateString)) {

            Log.d(TAG, "🧠 캐시 HIT → date: " + dateString);
            mainHandler.post(() -> {
                listener.onPhotosLoaded(photoCache.get(dateString), clusterCache.get(dateString));
                listener.onTimelineLoaded(timelineCache.get(dateString));
                listener.onRouteGenerated(routeCache.get(dateString));
            });
            return;
        }

        // ✅ 캐시 MISS: 새로 로드 후 캐싱
        new Thread(() -> {
            List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);
            Map<LatLng, List<PhotoInfo>> clusters = clusterService.clusterPhotosByLocation(dateString, 100.0);
            List<LatLng> route = clusterService.generateRouteForDate(dateString);
            List<TimelineItem> timelineItems = timelineManager.loadTimelineForDate(dateString);

            // 캐시 저장
            timelineCache.put(dateString, timelineItems);
            photoCache.put(dateString, photos);
            clusterCache.put(dateString, clusters);
            routeCache.put(dateString, route);

            mainHandler.post(() -> {
                Log.d(TAG, "🆕 캐시 MISS → DB 로드 완료: " + dateString);
                listener.onPhotosLoaded(photos, clusters);
                listener.onTimelineLoaded(timelineItems);
                listener.onRouteGenerated(route);
            });
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

    public List<String> getAvailableDates() {
        if (photoRepository == null) return new ArrayList<>();
        return photoRepository.getAvailableDates();
    }

    public List<PhotoInfo> getAllPhotoInfo() {
        if (photoRepository == null) return new ArrayList<>();
        return photoRepository.getAllPhotos();
    }

    // ✅ 캐시된 타임라인 반환
    public List<TimelineItem> getCachedTimeline(String dateString) {
        return timelineCache.getOrDefault(dateString, new ArrayList<>());
    }

    /**
     * 사진 데이터 로드 메서드 추가
     * MainActivity에서 호출되는 메서드
     */
    public void loadPhotoData() {
        if (photoRepository == null) return;

        // 백그라운드 스레드에서 사진 데이터 로드
        new Thread(() -> {
            try {
                // 참고: loadPhotosFromDevice는 정의되어 있지 않으므로 이를 대체하는 코드 사용
                // 예를 들어, 모든 사진을 로드하는 메서드 사용:
                List<PhotoInfo> allPhotos = photoRepository.getAllPhotos();

                // 날짜별 사진 캐시 초기화
                List<String> availableDates = photoRepository.getAvailableDates();
                for (String date : availableDates) {
                    List<PhotoInfo> photos = photoRepository.getPhotosForDate(date);
                    if (photos != null && !photos.isEmpty()) {
                        photoCache.put(date, photos);
                    }
                }

                Log.d(TAG, "사진 데이터 로드 완료: " + availableDates.size() + " 날짜");
            } catch (Exception e) {
                Log.e(TAG, "사진 데이터 로드 실패: " + e.getMessage(), e);
            }
        }).start();
    }
}