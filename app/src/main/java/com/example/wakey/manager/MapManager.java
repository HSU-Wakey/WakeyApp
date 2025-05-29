package com.example.wakey.manager;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.example.wakey.R;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.ui.map.PhotoClusterItem;
import com.example.wakey.ui.map.PhotoClusterRenderer;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.ThumbnailCache;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.clustering.ClusterManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapManager {
    private static final String TAG = "MapManager";
    private static MapManager instance;
    private Context context;
    private GoogleMap googleMap;
    private ClusterManager<PhotoClusterItem> clusterManager;

    private boolean clusteringEnabled = true;
    private static final float ZOOM_THRESHOLD = 13.5f;

    private OnMarkerClickListener markerClickListener;
    private Map<LatLng, List<PhotoInfo>> photoClusters = new HashMap<>();

    public interface OnMarkerClickListener {
        void onMarkerClick(PhotoInfo photoInfo);
        void onClusterClick(LatLng position);
        void onPlaceMarkerClick(String placeId);
    }

    private MapManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized MapManager getInstance(Context context) {
        if (instance == null) {
            instance = new MapManager(context);
        }
        return instance;
    }

    public void init(Activity activity, OnMarkerClickListener listener) {
        this.markerClickListener = listener;
    }

    public void setGoogleMap(GoogleMap map) {
        this.googleMap = map;
        setupClusterManager();
    }

    public void setClusteringEnabled(boolean enabled) {
        this.clusteringEnabled = enabled;
    }

    private void setupClusterManager() {
        if (context == null || googleMap == null) return;

        clusterManager = new ClusterManager<>(context, googleMap);
        clusterManager.setRenderer(new PhotoClusterRenderer(context, googleMap, clusterManager));

        googleMap.setOnMarkerClickListener(clusterManager);

        googleMap.setOnCameraIdleListener(() -> {
            float zoom = googleMap.getCameraPosition().zoom;
            Log.d("MapZoom", "🧭 줌 레벨: " + zoom);

            if (clusterManager == null || photoClusters == null) return;

            clusterManager.clearItems();

            if (zoom >= ZOOM_THRESHOLD) {
                Log.d("MapZoom", "🔍 확대 — 모든 사진 표시");
                addAllPhotoMarkers();
            } else {
                Log.d("MapZoom", "🌍 축소 — 대표 사진만 표시");
                addRepresentativeMarkers();
            }

            clusterManager.cluster();
        });

        clusterManager.setOnClusterClickListener(cluster -> {
            LatLng clusterPosition = cluster.getPosition();
            if (markerClickListener != null) {
                markerClickListener.onClusterClick(clusterPosition);
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    clusterPosition, googleMap.getCameraPosition().zoom + 2));
            return true;
        });

        clusterManager.setOnClusterItemClickListener(item -> {
            PhotoInfo photoInfo = (PhotoInfo) item.getTag();
            if (photoInfo != null && markerClickListener != null) {
                if (photoInfo.getPlaceId() != null) {
                    markerClickListener.onPlaceMarkerClick(photoInfo.getPlaceId());
                } else {
                    markerClickListener.onMarkerClick(photoInfo);
                }
            }
            return false;
        });
    }

    public void clearMap() {
        if (googleMap != null) googleMap.clear();
        if (clusterManager != null) clusterManager.clearItems();

        // 메모리 절약을 위해 캐시 정리 (선택사항)
        // ThumbnailCache.getInstance(context).clearCache();
    }

    public void moveCamera(LatLng latLng, float zoom) {
        if (googleMap != null && latLng != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        }
    }

    public void addMarkersForClusters(Map<LatLng, List<PhotoInfo>> clusters) {
        this.photoClusters = clusters;
        if (clusters == null || clusters.isEmpty()) return;

        float zoom = googleMap.getCameraPosition().zoom;
        clusterManager.clearItems();

        if (zoom >= ZOOM_THRESHOLD) {
            addAllPhotoMarkers();
        } else {
            addRepresentativeMarkers();
        }

        if (clusteringEnabled && clusterManager != null) {
            clusterManager.cluster();
        }
    }

    private void addRepresentativeMarkers() {
        if (photoClusters == null || photoClusters.isEmpty()) return;

        ThumbnailCache thumbnailCache = ThumbnailCache.getInstance(context);

        for (Map.Entry<LatLng, List<PhotoInfo>> entry : photoClusters.entrySet()) {
            List<PhotoInfo> clusterPhotos = entry.getValue();
            if (!clusterPhotos.isEmpty()) {
                PhotoInfo photo = clusterPhotos.get(0);

                // 먼저 캐시된 썸네일 확인
                Bitmap cachedThumbnail = thumbnailCache.getThumbnailSync(photo.getFilePath());

                if (cachedThumbnail != null) {
                    // 캐시에 있으면 바로 마커 추가
                    PhotoClusterItem item = new PhotoClusterItem(
                            photo.getLatLng(), "", "", photo, cachedThumbnail);
                    clusterManager.addItem(item);
                } else {
                    // 캐시에 없으면 일단 placeholder로 마커 추가
                    PhotoClusterItem item = new PhotoClusterItem(
                            photo.getLatLng(), "", "", photo, null);
                    clusterManager.addItem(item);

                    // 비동기로 썸네일 로드
                    thumbnailCache.loadThumbnail(photo.getFilePath(), new ThumbnailCache.ThumbnailLoadCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            // 썸네일 로드 완료 시 마커 업데이트
                            item.setThumbnail(bitmap);
                            clusterManager.cluster(); // 클러스터 다시 그리기
                        }

                        @Override
                        public void onLoadFailed() {
                            Log.e(TAG, "썸네일 로드 실패: " + photo.getFilePath());
                        }
                    });
                }
            }
        }
    }

    private void addAllPhotoMarkers() {
        if (photoClusters == null || photoClusters.isEmpty()) return;

        ThumbnailCache thumbnailCache = ThumbnailCache.getInstance(context);
        double offsetStep = 0.00002;
        int counter = 0;

        for (Map.Entry<LatLng, List<PhotoInfo>> entry : photoClusters.entrySet()) {
            List<PhotoInfo> photos = entry.getValue();

            for (PhotoInfo photo : photos) {
                LatLng original = photo.getLatLng();
                LatLng adjusted = new LatLng(
                        original.latitude + (offsetStep * counter),
                        original.longitude + (offsetStep * counter)
                );
                counter++;

                // 캐시된 썸네일 확인
                Bitmap cachedThumbnail = thumbnailCache.getThumbnailSync(photo.getFilePath());

                PhotoClusterItem item = new PhotoClusterItem(
                        adjusted, "", "", photo, cachedThumbnail);
                clusterManager.addItem(item);

                // 캐시에 없으면 비동기 로드
                if (cachedThumbnail == null) {
                    thumbnailCache.loadThumbnail(photo.getFilePath(), new ThumbnailCache.ThumbnailLoadCallback() {
                        @Override
                        public void onThumbnailLoaded(Bitmap bitmap) {
                            item.setThumbnail(bitmap);
                            // 개별 마커는 업데이트하지 않음 (성능상)
                        }

                        @Override
                        public void onLoadFailed() {
                            Log.e(TAG, "썸네일 로드 실패: " + photo.getFilePath());
                        }
                    });
                }
            }
        }
    }



    public void drawRoute(List<LatLng> points) {
        if (googleMap == null || points == null || points.size() < 2) return;

        PolylineOptions routeOptions = new PolylineOptions()
                .addAll(points)
                .width(8)
                .color(context.getResources().getColor(R.color.route_color))
                .geodesic(true);

        googleMap.addPolyline(routeOptions);

        PolylineOptions shadowOptions = new PolylineOptions()
                .addAll(points)
                .width(12)
                .color(Color.argb(50, 0, 0, 0))
                .geodesic(true)
                .zIndex(-1);

        googleMap.addPolyline(shadowOptions);
    }

    public void addSearchResultMarker(LatLng location, String title) {
        if (googleMap == null || location == null) return;

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 14));
    }

    public void addObjectClusters(List<PhotoInfo> photoList) {
        if (photoList == null || photoList.isEmpty() || clusterManager == null) return;

        for (PhotoInfo photo : photoList) {
            if (photo.getLatLng() != null && photo.getObjects() != null) {
                List<String> objects = photo.getObjects();
                Bitmap thumbnail = ImageUtils.loadThumbnailFromPath(context, photo.getFilePath());
                PhotoClusterItem item = new PhotoClusterItem(
                        photo.getLatLng(),
                        DateUtil.formatTime(photo.getDateTaken()),
                        String.join(", ", objects),
                        photo,
                        thumbnail
                );

                clusterManager.addItem(item);
            }
        }

        clusterManager.cluster();
    }
}
