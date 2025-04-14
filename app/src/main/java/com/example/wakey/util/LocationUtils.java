package com.example.wakey.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;
import android.Manifest;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.wakey.data.local.Photo;
import com.example.wakey.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LocationUtils {
    private static final String TAG = "LocationUtils";

    private Context context;
    private PlacesClient placesClient;
    private Geocoder geocoder;

    // API 키 (하드코딩하여 임시로 해결하는 방법 - 보안 취약점이 될 수 있어 실제 앱에서는 권장하지 않음)
    private static final String DEFAULT_API_KEY = "YOUR_ACTUAL_API_KEY";

    // 국가 코드별 한글 이름 매핑
    private static final Map<String, String> COUNTRY_CODE_TO_KOREAN = new HashMap<>();
    static {
        COUNTRY_CODE_TO_KOREAN.put("KR", "한국");
        COUNTRY_CODE_TO_KOREAN.put("JP", "일본");
        COUNTRY_CODE_TO_KOREAN.put("CN", "중국");
        COUNTRY_CODE_TO_KOREAN.put("US", "미국");
        COUNTRY_CODE_TO_KOREAN.put("TH", "태국");
        COUNTRY_CODE_TO_KOREAN.put("VN", "베트남");
        COUNTRY_CODE_TO_KOREAN.put("MY", "말레이시아");
        COUNTRY_CODE_TO_KOREAN.put("SG", "싱가포르");
        COUNTRY_CODE_TO_KOREAN.put("PH", "필리핀");
        COUNTRY_CODE_TO_KOREAN.put("ID", "인도네시아");
        COUNTRY_CODE_TO_KOREAN.put("GB", "영국");
        COUNTRY_CODE_TO_KOREAN.put("FR", "프랑스");
        COUNTRY_CODE_TO_KOREAN.put("DE", "독일");
        COUNTRY_CODE_TO_KOREAN.put("IT", "이탈리아");
        COUNTRY_CODE_TO_KOREAN.put("ES", "스페인");
        COUNTRY_CODE_TO_KOREAN.put("CA", "캐나다");
        COUNTRY_CODE_TO_KOREAN.put("AU", "호주");
        COUNTRY_CODE_TO_KOREAN.put("NZ", "뉴질랜드");
        // 필요에 따라 더 추가
    }

    // 한국 지역명 표준화
    private static final Map<String, String> KOREAN_REGION_STANDARD = new HashMap<>();
    static {
        KOREAN_REGION_STANDARD.put("서울", "서울특별시");
        KOREAN_REGION_STANDARD.put("부산", "부산광역시");
        KOREAN_REGION_STANDARD.put("인천", "인천광역시");
        KOREAN_REGION_STANDARD.put("대구", "대구광역시");
        KOREAN_REGION_STANDARD.put("광주", "광주광역시");
        KOREAN_REGION_STANDARD.put("대전", "대전광역시");
        KOREAN_REGION_STANDARD.put("울산", "울산광역시");
        KOREAN_REGION_STANDARD.put("세종", "세종특별자치시");
        KOREAN_REGION_STANDARD.put("경기", "경기도");
        KOREAN_REGION_STANDARD.put("강원", "강원도");
        KOREAN_REGION_STANDARD.put("충북", "충청북도");
        KOREAN_REGION_STANDARD.put("충남", "충청남도");
        KOREAN_REGION_STANDARD.put("전북", "전라북도");
        KOREAN_REGION_STANDARD.put("전남", "전라남도");
        KOREAN_REGION_STANDARD.put("경북", "경상북도");
        KOREAN_REGION_STANDARD.put("경남", "경상남도");
        KOREAN_REGION_STANDARD.put("제주", "제주특별자치도");
    }

    // 싱글톤 인스턴스
    private static LocationUtils instance;

    // 싱글톤 인스턴스 가져오기
    public static synchronized LocationUtils getInstance(Context context) {
        if (instance == null) {
            instance = new LocationUtils(context);
        }
        return instance;
    }

    // 생성자를 public으로 변경
    public LocationUtils(Context context) {
        this.context = context.getApplicationContext();

        // Places API 초기화
        if (!Places.isInitialized()) {
            try {
                // 리소스에서 API 키 가져오기 시도
                String apiKey = context.getString(R.string.google_maps_api_key);

                // 키가 기본값 또는 비어있는지 확인
                if (apiKey.equals("YOUR_API_KEY_HERE") || apiKey.isEmpty()) {
                    // 리소스에서 API 키를 가져올 수 없으면 하드코딩된 키 사용
                    apiKey = DEFAULT_API_KEY;
                }

                Places.initialize(context, apiKey, Locale.getDefault());
            } catch (Exception e) {
                // 리소스에서 API 키를 가져올 수 없는 경우 하드코딩된 키 사용
                Log.e(TAG, "API 키를 가져오는 중 오류 발생, 기본 키 사용", e);
                Places.initialize(context, DEFAULT_API_KEY, Locale.getDefault());
            }
        }

        try {
            placesClient = Places.createClient(context);
        } catch (Exception e) {
            Log.e(TAG, "Places 클라이언트 생성 중 오류", e);
            placesClient = null;
        }

        // Geocoder 초기화
        geocoder = new Geocoder(context, Locale.getDefault());
    }

    /**
     * 위치에서 지역 정보 가져오기
     */
    public String getRegionFromLocation(Location location) {
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);

                logAddressDetails(addr, location);

                StringBuilder regionBuilder = new StringBuilder();

                // 도/광역시
                if (addr.getAdminArea() != null) {
                    regionBuilder.append(addr.getAdminArea()).append(" ");
                }

                // 시/군/구
                if (addr.getLocality() != null) {
                    regionBuilder.append(addr.getLocality()).append(" ");
                } else if (addr.getSubAdminArea() != null) {
                    regionBuilder.append(addr.getSubAdminArea()).append(" ");
                }

                // 동
                if (addr.getSubLocality() != null) {
                    regionBuilder.append(addr.getSubLocality()).append(" ");
                } else if (addr.getThoroughfare() != null) {
                    regionBuilder.append(addr.getThoroughfare()).append(" ");
                }

                // 도로명 + 번지
                if (addr.getThoroughfare() != null) {
                    regionBuilder.append(addr.getThoroughfare()).append(" ");
                }
                if (addr.getFeatureName() != null) {
                    regionBuilder.append(addr.getFeatureName());
                }

                return regionBuilder.toString().trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "주소 변환 중 오류", e);
        }
        return "지역 정보 없음";
    }

    /**
     * 주소 세부 정보 로깅
     */
    private void logAddressDetails(Address addr, Location location) {
        Log.d(TAG, "🌍 위도: " + location.getLatitude() + ", 경도: " + location.getLongitude());
        Log.d(TAG, "🗺️ getCountryCode(): " + addr.getCountryCode());
        Log.d(TAG, "🗺️ getCountryName(): " + addr.getCountryName());
        Log.d(TAG, "🗺️ getAdminArea(): " + addr.getAdminArea());
        Log.d(TAG, "🗺️ getSubAdminArea(): " + addr.getSubAdminArea());
        Log.d(TAG, "🗺️ getLocality(): " + addr.getLocality());
        Log.d(TAG, "🗺️ getSubLocality(): " + addr.getSubLocality());
        Log.d(TAG, "🗺️ getThoroughfare(): " + addr.getThoroughfare());
        Log.d(TAG, "🗺️ getFeatureName(): " + addr.getFeatureName());
    }

    /**
     * 위도/경도 좌표로부터 상세 주소 정보 가져오기 (Geocoder 활용)
     */
    public CompletableFuture<Address> getAddressFromLocation(double latitude, double longitude) {
        CompletableFuture<Address> future = new CompletableFuture<>();

        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);

                // 로그 출력
                Location location = new Location("");
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                logAddressDetails(address, location);

                future.complete(address);
            } else {
                future.complete(null);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address from location", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 위도/경도 좌표로부터 가장 가까운 장소 정보 가져오기 (Places API 활용)
     */
    public CompletableFuture<Place> getNearestPlace(double latitude, double longitude) {
        CompletableFuture<Place> future = new CompletableFuture<>();

        // 위치 권한 확인
        if (!hasLocationPermission()) {
            Log.e(TAG, "위치 권한이 없습니다.");
            future.completeExceptionally(new SecurityException("위치 권한이 없습니다."));
            return future;
        }

        // Places API 클라이언트가 초기화되지 않았으면 예외 처리
        if (placesClient == null) {
            Log.e(TAG, "Places API 클라이언트가 초기화되지 않았습니다.");
            future.completeExceptionally(new IllegalStateException("Places API 클라이언트가 초기화되지 않았습니다."));
            return future;
        }

        // Places API 요청 필드 설정
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.ADDRESS_COMPONENTS,
                Place.Field.LAT_LNG,
                Place.Field.TYPES);

        try {
            // Places API를 사용하여 현재 위치 주변 장소 검색
            FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);

            placesClient.findCurrentPlace(request)
                    .addOnSuccessListener(response -> {
                        // 확률 순으로 정렬된 장소 목록에서 첫 번째 항목 선택
                        if (!response.getPlaceLikelihoods().isEmpty()) {
                            PlaceLikelihood likelihood = response.getPlaceLikelihoods().get(0);
                            future.complete(likelihood.getPlace());
                        } else {
                            future.complete(null);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error finding current place", e);
                        future.completeExceptionally(e);
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "위치 권한이 필요합니다.", e);
            future.completeExceptionally(e);
        } catch (Exception e) {
            Log.e(TAG, "Places API 호출 중 오류", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 위치 권한이 있는지 확인
     */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Geocoder를 사용하여, 위치 정보로 Photo 객체를 업데이트
     */
    public CompletableFuture<Photo> enrichPhotoWithLocationInfo(Photo photo) {
        if (photo.latitude == null || photo.longitude == null) {
            return CompletableFuture.completedFuture(photo);
        }

        return getAddressFromLocation(photo.latitude, photo.longitude)
                .thenApply(address -> {
                    if (address != null) {
                        // 국가 정보
                        String countryCode = address.getCountryCode();
                        String countryName = address.getCountryName();

                        // 한글 국가명 설정
                        if (countryCode != null && COUNTRY_CODE_TO_KOREAN.containsKey(countryCode)) {
                            photo.locationDo = COUNTRY_CODE_TO_KOREAN.get(countryCode);
                        } else {
                            photo.locationDo = countryName;
                        }

                        // 한국인 경우 특별 처리
                        if ("KR".equals(countryCode)) {
                            // 행정구역 정보 (한국)
                            String adminArea = address.getAdminArea(); // 시/도
                            String locality = address.getLocality();   // 시/군/구
                            String subLocality = address.getSubLocality(); // 동/읍/면

                            // 시/도 표준화
                            if (adminArea != null) {
                                for (Map.Entry<String, String> entry : KOREAN_REGION_STANDARD.entrySet()) {
                                    if (adminArea.contains(entry.getKey())) {
                                        photo.locationDo = entry.getValue();
                                        break;
                                    }
                                }
                            }

                            // 시/군/구 설정
                            photo.locationSi = locality;

                            // 동/읍/면 설정
                            photo.locationGu = subLocality;

                            // 도로명 설정
                            photo.locationStreet = address.getThoroughfare();
                        } else {
                            // 해외인 경우
                            photo.locationSi = address.getLocality(); // 도시
                            photo.locationGu = address.getSubLocality(); // 구역
                            photo.locationStreet = address.getThoroughfare(); // 도로
                        }

                        // 전체 주소 설정
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                            sb.append(address.getAddressLine(i));
                            if (i < address.getMaxAddressLineIndex()) sb.append(", ");
                        }
                        photo.fullAddress = sb.toString();
                    }
                    return photo;
                })
                .exceptionally(e -> {
                    Log.e(TAG, "Error enriching photo with location info", e);
                    return photo;
                });
    }

    /**
     * 사진이 국내(한국) 위치인지 확인 (Photo 객체 기반)
     */
    public boolean isDomesticLocation(Photo photo) {
        // 위치 정보가 없으면 판단 불가
        if (photo.locationDo == null && photo.locationSi == null && photo.locationGu == null) {
            return false;
        }

        // 명시적으로 한국/대한민국으로 설정된 경우
        if (photo.locationDo != null &&
                (photo.locationDo.equals("한국") ||
                        photo.locationDo.equals("대한민국") ||
                        photo.locationDo.equals("Korea") ||
                        photo.locationDo.equals("South Korea"))) {
            return true;
        }

        // 주요 한국 지역명 포함 여부 확인
        if (photo.locationDo != null) {
            for (String standardName : KOREAN_REGION_STANDARD.values()) {
                if (photo.locationDo.contains(standardName)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 위치 문자열이 국내(한국) 위치인지 확인
     */
    public CompletableFuture<Boolean> isDomesticLocation(String locationString) {
        // 위치 문자열이 없으면 판단 불가
        if (locationString == null || locationString.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        // 명시적으로 한국/대한민국으로 설정된 경우
        if (locationString.contains("한국") ||
                locationString.contains("대한민국") ||
                locationString.contains("Korea") ||
                locationString.contains("South Korea")) {
            return CompletableFuture.completedFuture(true);
        }

        // 주요 한국 지역명 포함 여부 확인
        for (String standardName : KOREAN_REGION_STANDARD.values()) {
            if (locationString.contains(standardName)) {
                return CompletableFuture.completedFuture(true);
            }
        }

        for (String shortName : KOREAN_REGION_STANDARD.keySet()) {
            if (locationString.contains(shortName)) {
                return CompletableFuture.completedFuture(true);
            }
        }

        // 해외 국가명이 있는지 확인
        for (String koreanName : COUNTRY_CODE_TO_KOREAN.values()) {
            if (!koreanName.equals("한국") && locationString.contains(koreanName)) {
                return CompletableFuture.completedFuture(false);
            }
        }

        // 기본적으로 모르면 false 반환
        return CompletableFuture.completedFuture(false);
    }

    /**
     * 국가명 한글화
     */
    public String getTranslatedCountryName(String countryName) {
        if (countryName == null) return "기타 국가";

        // 이미 한글인 경우 그대로 반환
        for (String koreanName : COUNTRY_CODE_TO_KOREAN.values()) {
            if (countryName.equals(koreanName)) {
                return countryName;
            }
        }

        // 영문 국가명을 한글로 변환
        for (Map.Entry<String, String> entry : COUNTRY_CODE_TO_KOREAN.entrySet()) {
            String englishName = getEnglishCountryName(entry.getKey());
            if (countryName.contains(englishName)) {
                return entry.getValue();
            }
        }

        return countryName;
    }

    /**
     * ISO 국가 코드에서 영문 국가명 조회
     */
    private String getEnglishCountryName(String countryCode) {
        Locale locale = new Locale("", countryCode);
        return locale.getDisplayCountry(Locale.ENGLISH);
    }
}