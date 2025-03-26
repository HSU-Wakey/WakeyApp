package com.example.wakey.manager;

import android.content.Context;

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

/**
 * 데이터 관련 기능을 관리하는 매니저 클래스 (데관매)
 */
public class DataManager {
    private static final String TAG = "DataManager";
    private static DataManager instance;

    private Context context;
    private PhotoRepository photoRepository;
    private TimelineManager timelineManager;
    private ClusterService clusterService;
    private SearchService searchService;

    // 인터페이스 정의 (인정의)
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

    /**
     * 싱글톤 인스턴스 반환 (싱인반)
     */
    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }

    /**
     * 초기화 메소드 (초메)
     */
    public void init(Context context) {
        this.photoRepository = PhotoRepository.getInstance(context);
        this.timelineManager = TimelineManager.getInstance(context);
        this.clusterService = ClusterService.getInstance(context);
        this.searchService = SearchService.getInstance(context);
    }

    /**
     * 사진 데이터 로드 (사데로)
     */
    public void loadPhotoData() {
        photoRepository.loadAllPhotos();
    }

    /**
     * 특정 날짜의 사진 데이터 로드 (특날사데로)
     */
    public void loadPhotosForDate(String dateString, OnDataLoadedListener listener) {
        if (photoRepository == null || listener == null) return;

        // 사진 데이터 로드
        List<PhotoInfo> photos = photoRepository.getPhotosForDate(dateString);

        if (photos != null && !photos.isEmpty()) {
            // 클러스터링 수행
            Map<LatLng, List<PhotoInfo>> clusters = clusterService.clusterPhotosByLocation(dateString, 100.0);
            listener.onPhotosLoaded(photos, clusters);

            // 타임라인 데이터 로드
            List<TimelineItem> timelineItems = timelineManager.loadTimelineForDate(dateString);
            listener.onTimelineLoaded(timelineItems);

            // 경로 생성
            List<LatLng> route = clusterService.generateRouteForDate(dateString);
            listener.onRouteGenerated(route);
        } else {
            // 빈 데이터 반환
            listener.onPhotosLoaded(new ArrayList<>(), new HashMap<>());
            listener.onTimelineLoaded(new ArrayList<>());
            listener.onRouteGenerated(new ArrayList<>());

            ToastManager.getInstance().showToast("이 날짜에 사진이 없습니다");
        }
    }

    /**
     * 모든 사진을 지도에 로드 (모사지로)
     */
    public void loadAllPhotosToMap(OnDataLoadedListener listener) {
        if (photoRepository == null || listener == null) return;

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

        if (!allPhotos.isEmpty()) {
            listener.onPhotosLoaded(allPhotos, allClusters);
        } else {
            listener.onPhotosLoaded(new ArrayList<>(), new HashMap<>());
            ToastManager.getInstance().showToast("사진을 찾을 수 없습니다");
        }
    }

    /**
     * 검색 수행 (검수)
     */
    public void performSearch(String query, GoogleMap map, OnSearchResultListener listener) {
        if (searchService == null || listener == null) return;

        SearchService.SearchResult result = searchService.search(query, map);

        if (result.isSuccess()) {
            if (result.isDateSearch()) {
                // 날짜 검색 결과 처리
                Date searchDate = result.getDate();
                listener.onDateSearchResult(searchDate);
            } else if (result.isLocationSearch()) {
                // 위치 검색 결과 처리
                LatLng location = result.getLocation();
                String name = result.getLocationName();
                listener.onLocationSearchResult(location, name);
            }
        } else {
            listener.onSearchFailed(query);
        }
    }

    /**
     * 타임라인 항목 강조 (타항강)
     */
    public TimelineItem findTimelineItemForPhoto(String photoPath, List<TimelineItem> timelineItems) {
        if (photoPath == null || timelineItems == null) return null;

        for (TimelineItem item : timelineItems) {
            if (item.getPhotoPath() != null && item.getPhotoPath().equals(photoPath)) {
                return item;
            }
        }

        return null;
    }

    /**
     * 사용 가능한 날짜 목록 가져오기 (사가날목가)
     */
    public List<String> getAvailableDates() {
        if (photoRepository == null) return new ArrayList<>();
        return photoRepository.getAvailableDates();
    }
}