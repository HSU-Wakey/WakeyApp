// service/PlaceService.java
package com.example.wakey.service;

import android.content.Context;
import android.util.Log;

import com.example.wakey.data.model.PlaceData;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaceService {
    private static final String TAG = "PlaceService";
    private static PlaceService instance;

    private final Context context;
    private final PlacesClient placesClient;

    // 싱글톤 인스턴스
    public static PlaceService getInstance(Context context) {
        if (instance == null) {
            instance = new PlaceService(context.getApplicationContext());
        }
        return instance;
    }

    private PlaceService(Context context) {
        this.context = context;
        // Places API 초기화
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(com.example.wakey.R.string.google_maps_api_key));
        }
        placesClient = Places.createClient(context);
    }

    // 위치 주변의 장소 검색
    public void findNearbyPlaces(LatLng location, int radius, PlacesCallback callback) {
        // 검색할 필드 지정
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.TYPES,
                Place.Field.RATING
        );

        // FindCurrentPlace 요청 생성
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);

        try {
            // API 호출 (Note: 실제로는 현재 위치 기반이라 검색 위치 지정이 불가능)
            // 실제 구현에서는 Place Search API를 HTTP 요청으로 호출해야 함
            placesClient.findCurrentPlace(request).addOnSuccessListener((response) -> {
                List<PlaceData> places = new ArrayList<>();

                // 결과 처리
                for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                    Place place = placeLikelihood.getPlace();

                    // 위치가 반경 내에 있는지 확인 (간단한 필터링)
                    if (place.getLatLng() != null) {
                        float[] results = new float[1];
                        android.location.Location.distanceBetween(
                                location.latitude, location.longitude,
                                place.getLatLng().latitude, place.getLatLng().longitude,
                                results);

                        if (results[0] <= radius) {
                            places.add(new PlaceData(
                                    place.getName(),
                                    place.getAddress(),
                                    place.getLatLng(),
                                    place.getTypes(),
                                    place.getRating()
                            ));
                        }
                    }
                }

                callback.onPlacesLoaded(places);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error finding places", e);
                callback.onError(e);
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission for Places API", e);
            callback.onError(e);
        }
    }

    // 콜백 인터페이스
    public interface PlacesCallback {
        void onPlacesLoaded(List<PlaceData> places);
        void onError(Exception e);
    }

    // 장소 유형에 따른 캡션 생성
    public static String getPlaceTypeCaption(Place.Type placeType) {
        if (placeType == null) return "방문한 장소";

        switch (placeType) {
            case RESTAURANT:
                return "맛있는 음식을 즐긴 레스토랑";
            case CAFE:
                return "커피와 함께한 시간";
            case SHOPPING_MALL:
                return "쇼핑을 즐긴 곳";
            case TOURIST_ATTRACTION:
                return "관광 명소 방문";
            case PARK:
                return "자연을 즐긴 공원";
            case MUSEUM:
                return "예술과 역사를 감상한 곳";
            case LODGING:
                return "숙박한 곳";
            case BAR:
                return "음료를 즐긴 바";
            case MOVIE_THEATER:
                return "영화를 관람한 곳";
            case ART_GALLERY:
                return "예술 작품을 감상한 갤러리";
            default:
                return "방문한 장소";
        }
    }
}