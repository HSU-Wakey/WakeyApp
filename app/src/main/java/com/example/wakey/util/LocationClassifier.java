package com.example.wakey.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import com.example.wakey.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LocationClassifier {
    private static final String TAG = "LocationClassifier";

    private Context context;
    private PlacesClient placesClient;
    private Geocoder geocoder;

    // 한국 지역 코드
    private static final String KOREA_COUNTRY_CODE = "KR";

    // 국가 이름 매핑 (영문 -> 한글)
    private static final Map<String, String> COUNTRY_NAME_MAP = new HashMap<>();
    static {
        COUNTRY_NAME_MAP.put("Japan", "일본");
        COUNTRY_NAME_MAP.put("China", "중국");
        COUNTRY_NAME_MAP.put("United States", "미국");
        COUNTRY_NAME_MAP.put("United States of America", "미국");
        COUNTRY_NAME_MAP.put("Thailand", "태국");
        COUNTRY_NAME_MAP.put("Vietnam", "베트남");
        COUNTRY_NAME_MAP.put("Malaysia", "말레이시아");
        COUNTRY_NAME_MAP.put("Indonesia", "인도네시아");
        COUNTRY_NAME_MAP.put("Singapore", "싱가포르");
        COUNTRY_NAME_MAP.put("Philippines", "필리핀");
        COUNTRY_NAME_MAP.put("Cambodia", "캄보디아");
        COUNTRY_NAME_MAP.put("United Kingdom", "영국");
        COUNTRY_NAME_MAP.put("France", "프랑스");
        COUNTRY_NAME_MAP.put("Germany", "독일");
        COUNTRY_NAME_MAP.put("Italy", "이탈리아");
        COUNTRY_NAME_MAP.put("Spain", "스페인");
        COUNTRY_NAME_MAP.put("Australia", "호주");
        COUNTRY_NAME_MAP.put("Canada", "캐나다");
        // 추가 국가는 필요에 따라 확장
    }

    public LocationClassifier(Context context) {
        this.context = context;

        // Places API 초기화
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_maps_api_key), Locale.KOREA);
        }

        placesClient = Places.createClient(context);
        geocoder = new Geocoder(context, Locale.KOREA);
    }

    /**
     * 좌표를 기반으로 국내/해외 구분
     * @param latLng 위도, 경도 좌표
     * @return CompletableFuture<Boolean> - true이면 국내, false이면 해외
     */
    public CompletableFuture<Boolean> isDomesticLocation(LatLng latLng) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String countryCode = address.getCountryCode();
                result.complete(KOREA_COUNTRY_CODE.equals(countryCode));
            } else {
                // 주소를 찾을 수 없는 경우 (바다, 국경 지역 등)
                result.complete(false);
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoding failed", e);
            result.completeExceptionally(e);
        }

        return result;
    }

    /**
     * 주소 문자열로부터 국내/해외 구분
     * @param address 주소 문자열
     * @return CompletableFuture<Boolean> - true이면 국내, false이면 해외
     */
    public CompletableFuture<Boolean> isDomesticLocation(String address) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        // 한국 주요 지역명 확인
        List<String> koreanKeywords = Arrays.asList(
                "서울", "부산", "인천", "대구", "광주", "대전", "울산", "세종",
                "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
                "특별시", "광역시", "특별자치시", "특별자치도", "한국", "대한민국"
        );

        // 해외 주요 국가/지역명 확인
        List<String> foreignKeywords = Arrays.asList(
                "Japan", "일본", "Tokyo", "도쿄", "Osaka", "오사카",
                "China", "중국", "Beijing", "베이징", "Shanghai", "상하이",
                "USA", "미국", "United States", "New York", "뉴욕",
                "Thailand", "태국", "Bangkok", "방콕",
                "Vietnam", "베트남", "UK", "영국", "France", "프랑스"
                // 더 많은 해외 키워드 추가 가능
        );

        // 한국 키워드가 포함되어 있는지 확인
        for (String keyword : koreanKeywords) {
            if (address.contains(keyword)) {
                result.complete(true);
                return result;
            }
        }

        // 해외 키워드가 포함되어 있는지 확인
        for (String keyword : foreignKeywords) {
            if (address.contains(keyword)) {
                result.complete(false);
                return result;
            }
        }

        // 키워드로 판단할 수 없는 경우 Geocoder 활용
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String countryCode = addr.getCountryCode();
                result.complete(KOREA_COUNTRY_CODE.equals(countryCode));
            } else {
                // 주소를 찾을 수 없는 경우 기본값
                result.complete(false);
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoding failed", e);
            // 에러 발생 시 키워드 기반으로 최선의 추측
            result.complete(containsMoreKoreanWords(address, koreanKeywords, foreignKeywords));
        }

        return result;
    }

    /**
     * 한국 키워드와 해외 키워드 중 어떤 것이 더 많은지 확인
     */
    private boolean containsMoreKoreanWords(String text, List<String> koreanKeywords, List<String> foreignKeywords) {
        int koreanCount = 0;
        int foreignCount = 0;

        for (String keyword : koreanKeywords) {
            if (text.contains(keyword)) koreanCount++;
        }

        for (String keyword : foreignKeywords) {
            if (text.contains(keyword)) foreignCount++;
        }

        return koreanCount >= foreignCount;
    }

    /**
     * 좌표로부터 국가 정보 가져오기
     * @param latLng 위도, 경도 좌표
     * @return CompletableFuture<LocationInfo> - 위치 정보 객체
     */
    public CompletableFuture<LocationInfo> getLocationInfo(LatLng latLng) {
        CompletableFuture<LocationInfo> result = new CompletableFuture<>();

        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LocationInfo locationInfo = new LocationInfo();

                // 국가 정보
                locationInfo.setCountryCode(address.getCountryCode());
                locationInfo.setCountryName(address.getCountryName());
                locationInfo.setTranslatedCountryName(getTranslatedCountryName(address.getCountryName()));

                // 행정구역 정보
                locationInfo.setAdminArea(address.getAdminArea()); // 시/도
                locationInfo.setLocality(address.getLocality()); // 시/군/구
                locationInfo.setSubLocality(address.getSubLocality()); // 동/읍/면

                result.complete(locationInfo);
            } else {
                result.completeExceptionally(new Exception("No address found for the coordinates"));
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoding failed", e);
            result.completeExceptionally(e);
        }

        return result;
    }

    /**
     * 국가명을 한글로 변환
     */
    public String getTranslatedCountryName(String englishName) {
        if (englishName == null) return "기타 국가";

        // 이미 한글이면 그대로 반환
        for (String koreanName : COUNTRY_NAME_MAP.values()) {
            if (englishName.equals(koreanName)) {
                return englishName;
            }
        }

        // 영문 국가명이면 한글로 변환
        return COUNTRY_NAME_MAP.getOrDefault(englishName, englishName);
    }

    /**
     * 위치 정보 클래스
     */
    public static class LocationInfo {
        private String countryCode;
        private String countryName;
        private String translatedCountryName;
        private String adminArea; // 시/도
        private String locality; // 시/군/구
        private String subLocality; // 동/읍/면

        // Getters and Setters
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

        public String getCountryName() { return countryName; }
        public void setCountryName(String countryName) { this.countryName = countryName; }

        public String getTranslatedCountryName() { return translatedCountryName; }
        public void setTranslatedCountryName(String translatedCountryName) {
            this.translatedCountryName = translatedCountryName;
        }

        public String getAdminArea() { return adminArea; }
        public void setAdminArea(String adminArea) { this.adminArea = adminArea; }

        public String getLocality() { return locality; }
        public void setLocality(String locality) { this.locality = locality; }

        public String getSubLocality() { return subLocality; }
        public void setSubLocality(String subLocality) { this.subLocality = subLocality; }

        /**
         * 국내 위치인지 확인
         */
        public boolean isDomestic() {
            return KOREA_COUNTRY_CODE.equals(countryCode);
        }

        /**
         * 표시할 지역명 가져오기 (국내)
         */
        public String getDomesticDisplayName() {
            if (adminArea != null && !adminArea.isEmpty()) {
                return adminArea; // 시/도 단위
            } else if (locality != null && !locality.isEmpty()) {
                return locality; // 시/군/구 단위
            } else {
                return "기타 지역";
            }
        }

        /**
         * 표시할 지역명 가져오기 (해외)
         */
        public String getOverseasDisplayName() {
            if (translatedCountryName != null && !translatedCountryName.isEmpty()) {
                return translatedCountryName; // 한글 국가명
            } else if (countryName != null && !countryName.isEmpty()) {
                return countryName; // 원본 국가명
            } else {
                return "기타 국가";
            }
        }
    }
}