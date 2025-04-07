package com.example.wakey.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.wakey.R;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.PlaceData;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.data.util.PlaceHelper;
import com.example.wakey.service.CaptionService;
import com.example.wakey.service.PlaceService;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPhotoRequest;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * API 관련 기능을 관리하는 매니저 클래스
 */
public class ApiManager {
    private static final String TAG = "ApiManager";
    private static ApiManager instance;

    private Context context;
    private PlacesClient placesClient;
    private PlaceService placeService;
    private CaptionService captionService;

    // 상수 정의 (상정)
    private static final int POI_SEARCH_RADIUS = 300; // 미터 단위

    // 인터페이스 정의 (인정의)
    public interface OnPlaceDetailsFetchedListener {
        void onSuccess(String name, String address, LatLng latLng, Bitmap photo, String placeId);
        void onFailure(Exception e);
    }

    public interface OnAddressResolvedListener {
        void onSuccess(Address address, PhotoInfo photoInfo, List<PlaceData> places);
        void onFailure(Exception e);
    }

    private ApiManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 싱글톤 인스턴스 반환 (싱인반)
     */
    public static synchronized ApiManager getInstance(Context context) {
        if (instance == null) {
            instance = new ApiManager(context);
        }
        return instance;
    }

    /**
     * 초기화 메소드 (초메)
     */
    public void init(Context context) {
        // Places API 초기화
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_maps_api_key));
        }

        placesClient = Places.createClient(context);
        placeService = PlaceService.getInstance(context);
        captionService = CaptionService.getInstance();
    }

    /**
     * 장소 세부정보 가져오기 (장세가)
     */
    public void fetchPlaceDetails(String placeId, OnPlaceDetailsFetchedListener listener) {
        if (placesClient == null || placeId == null || listener == null) return;

        // 요청할 필드 정의
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.PHOTO_METADATAS,
                Place.Field.TYPES
        );

        // FetchPlaceRequest 생성
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);

        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    String name = place.getName();
                    String address = place.getAddress();
                    LatLng latLng = place.getLatLng();
                    List<PhotoMetadata> photoMetadataList = place.getPhotoMetadatas();

                    // 사진이 있으면 첫 번째 사진 메타데이터 사용
                    if (photoMetadataList != null && !photoMetadataList.isEmpty()) {
                        PhotoMetadata photoMetadata = photoMetadataList.get(0);
                        FetchPhotoRequest photoRequest = FetchPhotoRequest.builder(photoMetadata)
                                .setMaxWidth(500) // 사진 최대 너비 설정
                                .setMaxHeight(500) // 사진 최대 높이 설정
                                .build();

                        placesClient.fetchPhoto(photoRequest)
                                .addOnSuccessListener(photoResponse -> {
                                    Bitmap photoBitmap = photoResponse.getBitmap();
                                    listener.onSuccess(name, address, latLng, photoBitmap, placeId);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "사진 가져오기 실패", e);
                                    listener.onSuccess(name, address, latLng, null, placeId); // 사진 없이 반환
                                });
                    } else {
                        listener.onSuccess(name, address, latLng, null, placeId); // 사진 없음
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "장소 세부정보 가져오기 실패", e);
                    listener.onFailure(e);
                });
    }

    /**
     * 주소 및 POI 정보 가져오기 (주포정가)
     */
    public void fetchAddressAndPOIs(PhotoInfo photoInfo, OnAddressResolvedListener listener) {
        if (photoInfo == null || photoInfo.getLatLng() == null || listener == null) return;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(
                        photoInfo.getLatLng().latitude,
                        photoInfo.getLatLng().longitude,
                        1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    // POI 검색
                    handler.post(() -> {
                        placeService.findNearbyPlaces(photoInfo.getLatLng(), POI_SEARCH_RADIUS,
                                new PlaceService.PlacesCallback() {
                                    @Override
                                    public void onPlacesLoaded(List<PlaceData> places) {
                                        listener.onSuccess(address, photoInfo, places);
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Log.e(TAG, "POI 검색 오류", e);
                                        // POI 없이도 주소 정보는 전달
                                        listener.onSuccess(address, photoInfo, new ArrayList<>());
                                    }
                                });
                    });
                } else {
                    // 주소를 찾을 수 없을 때
                    handler.post(() -> listener.onFailure(new IOException("주소를 찾을 수 없습니다")));
                }
            } catch (IOException e) {
                Log.e(TAG, "위치 정보 가져오기 오류", e);
                handler.post(() -> listener.onFailure(e));
            }
        });
    }

    /**
     * 타임라인 항목 생성 (타항생)
     */
    public TimelineItem createTimelineItem(PhotoInfo photo, Address address, List<PlaceData> places) {
        if (photo == null) return null;

        String caption;
        String locationName;
        String activityType;

        if (address != null) {
            // 🔧 캡션 생성
            caption = captionService.generateCaption(photo, address, places);

            // ✅ 주소에서 '구 + 동' 추출: 예) "종로구 혜화동"
            String locality = address.getLocality();          // 종로구
            String subLocality = address.getSubLocality();    // 혜화동

            if (locality != null && subLocality != null) {
                locationName = locality + " " + subLocality;
            } else if (address.getAddressLine(0) != null) {
                locationName = address.getAddressLine(0); // 대체용 전체 주소
            } else {
                locationName = "알 수 없는 위치";
            }

            activityType = captionService.inferActivityType(photo, address);
        } else {
            caption = DateUtil.formatTime(photo.getDateTaken()) + "에 촬영한 사진";
            locationName = photo.getPlaceName() != null ? photo.getPlaceName() : "알 수 없는 위치";
            activityType = "여행";
        }

        List<String> poiNames = new ArrayList<>();
        if (places != null) {
            for (PlaceData place : places) {
                poiNames.add(place.getName());
            }
        }

        return new TimelineItem.Builder()
                .setTime(photo.getDateTaken())
                .setLocation(locationName)
                .setPhotoPath(photo.getFilePath())
                .setLatLng(photo.getLatLng())
                .setDescription(caption)
                .setActivityType(activityType)
                .setPlaceProbability(1.0f)
                .setNearbyPOIs(poiNames)
                .build();
    }


    /**
     * 주변 장소 검색 (주장검)
     */
    public void searchNearbyPlaces(LatLng location, PlaceHelper.OnPlaceFoundListener listener) {
        if (placesClient == null || location == null || listener == null) return;

        PlaceHelper.getNearbyPlaces(placesClient, location, POI_SEARCH_RADIUS, listener);
    }
}