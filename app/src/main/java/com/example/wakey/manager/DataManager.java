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

    public List<String> getAvailableDates() {
        if (photoRepository == null) return new ArrayList<>();
        return photoRepository.getAvailableDates();
    }

    public List<PhotoInfo> getAllPhotoInfo() {
        if (photoRepository == null) return new ArrayList<>();
        return photoRepository.getAllPhotos();
    }
}
