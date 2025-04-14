package com.example.wakey.manager;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.example.wakey.R;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.ui.map.PhotoClusterItem;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.clustering.ClusterManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 구글 맵 관련 기능을 관리하는 매니저 클래스
 */
public class MapManager {
    private static final String TAG = "MapManager";
    private static MapManager instance;
    private Context context;
    private GoogleMap googleMap;
    private ClusterManager<PhotoClusterItem> clusterManager;

    // 맵 설정 변수
    private boolean clusteringEnabled = true;
    private boolean showPOIs = false;
    private static final int POI_SEARCH_RADIUS = 300; // 미터 단위

    // 인터페이스 정의
    public interface OnMarkerClickListener {
        void onMarkerClick(PhotoInfo photoInfo);
        void onClusterClick(LatLng position);
        void onPlaceMarkerClick(String placeId);
    }

    private OnMarkerClickListener markerClickListener;

    private MapManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 싱글톤 인스턴스 반환
     */
    public static synchronized MapManager getInstance(Context context) {
        if (instance == null) {
            instance = new MapManager(context);
        }
        return instance;
    }

    /**
     * 초기화 메소드
     */
    public void init(Activity activity, OnMarkerClickListener listener) {
        this.markerClickListener = listener;
    }

    /**
     * 구글맵 설정
     */
    public void setGoogleMap(GoogleMap map) {
        this.googleMap = map;
        setupClusterManager();
    }

    /**
     * 클러스터링 활성화 여부 설정
     */
    public void setClusteringEnabled(boolean enabled) {
        this.clusteringEnabled = enabled;
    }

    /**
     * POI 표시 여부 설정
     */
    public void setShowPOIs(boolean show) {
        this.showPOIs = show;
    }

    /**
     * 구글맵 유형 설정
     */
    public void setMapType(int mapType) {
        if (googleMap != null) {
            googleMap.setMapType(mapType);
        }
    }

    /**
     * 교통 정보 표시 설정
     */
    public void setTrafficEnabled(boolean enabled) {
        if (googleMap != null) {
            googleMap.setTrafficEnabled(enabled);
        }
    }

    /**
     * 클러스터 매니저 설정
     */
    private void setupClusterManager() {
        if (context == null || googleMap == null) return;

        // 클러스터 매니저 생성
        clusterManager = new ClusterManager<>(context, googleMap);
        googleMap.setOnMarkerClickListener(clusterManager);
        googleMap.setOnCameraIdleListener(clusterManager);

        // 클러스터 클릭 리스너
        clusterManager.setOnClusterClickListener(cluster -> {
            LatLng clusterPosition = cluster.getPosition();
            if (markerClickListener != null) {
                markerClickListener.onClusterClick(clusterPosition);
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    clusterPosition, googleMap.getCameraPosition().zoom + 2));
            return true;
        });

        // 클러스터 아이템 클릭 리스너
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

    /**
     * 지도 클리어
     */
    public void clearMap() {
        if (googleMap != null) {
            googleMap.clear();
        }

        if (clusterManager != null) {
            clusterManager.clearItems();
        }
    }

    /**
     * 특정 위치로 카메라 이동
     */
    public void moveCamera(LatLng latLng, float zoom) {
        if (googleMap != null && latLng != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        }
    }

    /**
     * 사진 클러스터를 마커로 추가
     */
    public void addMarkersForClusters(Map<LatLng, List<PhotoInfo>> clusters) {
        if (clusters == null || clusters.isEmpty()) return;

        for (Map.Entry<LatLng, List<PhotoInfo>> entry : clusters.entrySet()) {
            List<PhotoInfo> clusterPhotos = entry.getValue();
            if (!clusterPhotos.isEmpty()) {
                PhotoInfo representativePhoto = clusterPhotos.get(0);
                if (representativePhoto.getLatLng() != null) {
                    addDefaultMarker(representativePhoto, clusterPhotos.size());
                }
            }
        }

        // 클러스터링 활성화된 경우 클러스터링 수행
        if (clusteringEnabled && clusterManager != null) {
            clusterManager.cluster();
        }
    }

    /**
     * 장소 정보로 마커 추가
     */
    public void addPlaceMarker(String name, String address, LatLng latLng, Bitmap photo, PhotoInfo photoInfo, int clusterSize) {
        if (latLng == null) return;

        if (clusteringEnabled && clusterManager != null) {
            String timeString = DateUtil.formatTime(photoInfo.getDateTaken());
            PhotoClusterItem item = new PhotoClusterItem(
                    latLng,
                    name,
                    address + "\n" + (clusterSize > 1 ? clusterSize + "개 사진" : "사진"),
                    photoInfo,
                    photo  // 누락된 Bitmap 매개변수 추가
            );
            clusterManager.addItem(item);
            clusterManager.cluster();
        } else {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title(name)
                    .snippet(address + "\n" + (clusterSize > 1 ? clusterSize + "개 사진" : "사진"));

            if (photo != null) {
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(photo));
            }

            Marker marker = googleMap.addMarker(markerOptions);
            if (marker != null) {
                marker.setTag(photoInfo);
            }
        }
    }

    /**
     * 기본 마커 추가
     */
    public void addDefaultMarker(PhotoInfo photoInfo, int clusterSize) {
        if (photoInfo == null || photoInfo.getLatLng() == null) return;

        if (clusteringEnabled && clusterManager != null) {
            String timeString = DateUtil.formatTime(photoInfo.getDateTaken());
            PhotoClusterItem item = new PhotoClusterItem(
                    photoInfo.getLatLng(),
                    timeString,
                    clusterSize > 1 ? clusterSize + "개 사진" : "사진",
                    photoInfo,
                    null  // 누락된 Bitmap 매개변수 추가 (기본 마커는 Bitmap이 없으므로 null 전달)
            );
            clusterManager.addItem(item);
        } else {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(photoInfo.getLatLng())
                    .title(DateUtil.formatTime(photoInfo.getDateTaken()))
                    .snippet(clusterSize > 1 ? clusterSize + "개 사진" : "사진");

            Marker marker = googleMap.addMarker(markerOptions);
            if (marker != null) {
                marker.setTag(photoInfo);
            }
        }
    }

    /**
     * 경로 그리기
     */
    public void drawRoute(List<LatLng> points) {
        if (googleMap == null || points == null || points.size() < 2) return;

        // 주요 경로선
        PolylineOptions routeOptions = new PolylineOptions()
                .addAll(points)
                .width(8)
                .color(context.getResources().getColor(R.color.route_color))
                .geodesic(true);

        // 맵에 경로 추가
        googleMap.addPolyline(routeOptions);

        // 입체감을 위한 경로 그림자
        PolylineOptions shadowOptions = new PolylineOptions()
                .addAll(points)
                .width(12)
                .color(Color.argb(50, 0, 0, 0))
                .geodesic(true)
                .zIndex(-1);

        googleMap.addPolyline(shadowOptions);

        // 시작과 끝 마커 추가
        if (!points.isEmpty()) {
            // 시작 마커
            MarkerOptions startMarker = new MarkerOptions()
                    .position(points.get(0))
                    .title("시작")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            googleMap.addMarker(startMarker);

            // 끝 마커
            MarkerOptions endMarker = new MarkerOptions()
                    .position(points.get(points.size() - 1))
                    .title("끝")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            googleMap.addMarker(endMarker);
        }
    }

    /**
     * 검색 결과 마커 추가
     */
    public void addSearchResultMarker(LatLng location, String title) {
        if (googleMap == null || location == null) return;

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 14));
        googleMap.addMarker(new MarkerOptions()
                .position(location)
                .title(title != null ? title : "검색 결과"));
    }

    /**
     * 객체 탐지 결과와 위치 정보를 기반으로 마커 클러스터 추가
     */
    public void addObjectClusters(List<PhotoInfo> photoList) {
        if (photoList == null || photoList.isEmpty() || clusterManager == null) return;

        for (PhotoInfo photo : photoList) {
            if (photo.getLatLng() != null && photo.getObjects() != null) {
                List<String> objects = photo.getObjects();


                // 클러스터 항목 생성 (Bitmap 없음 → null, 필요시 썸네일 처리 가능)
                PhotoClusterItem item = new PhotoClusterItem(
                        photo.getLatLng(),
                        DateUtil.formatTime(photo.getDateTaken()),
                        String.join(", ", objects),  // snippet으로 객체 표시
                        photo,  // tag
                        null   // 썸네일 Bitmap 필요시 추후 삽입
                );
                clusterManager.addItem(item);
            }
        }

        clusterManager.cluster();
    }
}