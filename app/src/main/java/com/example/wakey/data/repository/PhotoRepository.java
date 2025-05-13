package com.example.wakey.data.repository;

import android.content.Context;
import android.location.Address;
import android.util.Log;
import android.util.Pair;

import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.util.LocationUtils;
import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class PhotoRepository {
    private static final String TAG = "PhotoRepository";

    // 싱글톤 패턴 구현
    private static PhotoRepository instance;

    private Context context;
    private AppDatabase database;
    private LocationUtils locationUtils;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // 생성자를 private으로 변경
    public PhotoRepository(Context context) {
        this.context = context.getApplicationContext(); // 애플리케이션 컨텍스트 사용
        this.database = AppDatabase.getInstance(context);
        this.locationUtils = new LocationUtils(context);
    }

    // 싱글톤 getInstance 메소드 추가
    public static synchronized PhotoRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PhotoRepository(context);
        }
        return instance;
    }

    /**
     * 국내 사진 목록 가져오기 (Google Geocoder 활용)
     */
    public CompletableFuture<Map<String, List<Photo>>> getDomesticPhotos() {
        CompletableFuture<Map<String, List<Photo>>> result = new CompletableFuture<>();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Photo> allPhotos = database.photoDao().getAllPhotos();
                Map<String, List<Photo>> regionGroups = new HashMap<>();

                // 사진 위치별로 필터링 및 그룹화
                for (Photo photo : allPhotos) {
                    // 위치 정보가 없는 사진은 건너뜀
                    if (photo.latitude == null || photo.longitude == null) {
                        continue;
                    }

                    // 국내 위치인지 확인
                    boolean isDomestic = locationUtils.isDomesticLocation(photo);

                    if (isDomestic) {
                        // 지역 그룹 키 결정
                        String regionKey = photo.locationDo != null ? photo.locationDo : "기타 지역";

                        if (!regionGroups.containsKey(regionKey)) {
                            regionGroups.put(regionKey, new ArrayList<>());
                        }

                        regionGroups.get(regionKey).add(photo);
                    }
                }

                result.complete(regionGroups);
            } catch (Exception e) {
                Log.e(TAG, "Error getting domestic photos", e);
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    /**
     * 해외 사진 목록 가져오기 (Google Geocoder 활용)
     */
    public CompletableFuture<Map<String, List<Photo>>> getOverseasPhotos() {
        CompletableFuture<Map<String, List<Photo>>> result = new CompletableFuture<>();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Photo> allPhotos = database.photoDao().getAllPhotos();
                Map<String, List<Photo>> countryGroups = new HashMap<>();

                for (Photo photo : allPhotos) {
                    if (photo.latitude == null || photo.longitude == null) continue;

                    boolean isDomestic = locationUtils.isDomesticLocation(photo);
                    if (!isDomestic) {
                        // ✅ 핵심: country 필드를 우선 사용
                        String rawCountry = (photo.country != null && !photo.country.isEmpty())
                                ? photo.country
                                : photo.locationDo; // fallback

                        if (rawCountry == null || rawCountry.isEmpty()) {
                            rawCountry = "기타 국가";
                        }

                        // ✅ 한글 번역 적용
                        String translated = locationUtils.getTranslatedCountryName(rawCountry);

                        countryGroups.computeIfAbsent(translated, k -> new ArrayList<>()).add(photo);
                    }
                }

                result.complete(countryGroups);
            } catch (Exception e) {
                Log.e(TAG, "Error getting overseas photos", e);
                result.completeExceptionally(e);
            }
        });

        return result;
    }


    /**
     * 특정 국가/지역의 사진 목록 가져오기
     */
    public CompletableFuture<Map<String, List<Photo>>> getPhotosForRegion(String regionName, boolean isDomestic) {
        CompletableFuture<Map<String, List<Photo>>> result = new CompletableFuture<>();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Photo> allPhotos = database.photoDao().getAllPhotos();
                Map<String, List<Photo>> subRegionGroups = new HashMap<>();

                // 사진 필터링 및 그룹화
                for (Photo photo : allPhotos) {
                    // 위치 정보가 없는 사진은 건너뜀
                    if (photo.latitude == null || photo.longitude == null) {
                        continue;
                    }

                    // 이 사진이 해당 지역에 속하는지 확인
                    boolean inRegion = false;

                    if (isDomestic) {
                        // 국내 지역인 경우 - locationDo로 비교
                        inRegion = regionName.equals(photo.locationDo);
                    } else {
                        // 해외 지역인 경우 - locationDo로 비교
                        inRegion = regionName.equals(photo.locationDo) ||
                                regionName.equals(locationUtils.getTranslatedCountryName(photo.locationDo));
                    }

                    if (inRegion) {
                        // 서브 지역 키 결정 (국내: 구/군, 해외: 도시)
                        String subRegionKey = isDomestic ?
                                (photo.locationGu != null ? photo.locationGu :
                                        (photo.locationSi != null ? photo.locationSi : "기타 지역")) :
                                (photo.locationSi != null ? photo.locationSi : "기타 지역");

                        if (!subRegionGroups.containsKey(subRegionKey)) {
                            subRegionGroups.put(subRegionKey, new ArrayList<>());
                        }

                        subRegionGroups.get(subRegionKey).add(photo);
                    }
                }

                result.complete(subRegionGroups);
            } catch (Exception e) {
                Log.e(TAG, "Error getting photos for region: " + regionName, e);
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    /**
     * 날짜별 사진 목록 가져오기
     */
    public List<PhotoInfo> getPhotosForDate(String dateString) {
        List<Photo> photos = database.photoDao().getPhotosForDate(dateString);
        List<PhotoInfo> photoInfos = new ArrayList<>();

        for (Photo photo : photos) {
            PhotoInfo photoInfo = convertToPhotoInfo(photo);
            photoInfos.add(photoInfo);
        }

        return photoInfos;
    }

    /**
     * 사진 정보 모델 변환 (Photo -> PhotoInfo)
     */
    private PhotoInfo convertToPhotoInfo(Photo photo) {
        // 위치 정보 처리
        LatLng latLng = null;
        if (photo.latitude != null && photo.longitude != null) {
            latLng = new LatLng(photo.latitude, photo.longitude);
        }

        // 날짜 처리
        Date dateTaken = null;
        try {
            if (photo.dateTaken != null && !photo.dateTaken.isEmpty()) {
                dateTaken = dateFormat.parse(photo.dateTaken);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date: " + photo.dateTaken, e);
        }

        // 객체 목록 처리
        List<String> objects = new ArrayList<>();
        if (photo.getDetectedObjectPairs() != null) {
            for (Pair<String, Float> pair : photo.getDetectedObjectPairs()) {
                objects.add(pair.first);
            }
        }

        // PhotoInfo 객체 생성
        PhotoInfo photoInfo = new PhotoInfo(
                photo.filePath,
                dateTaken,
                latLng,
                null, // placeId는 Photo 객체에 없음
                null, // placeName은 Photo 객체에 없음
                photo.fullAddress,
                photo.caption,
                objects,
                photo.detectedObjectPairs
        );

        // 추가 정보 설정
        photoInfo.setLocationDo(photo.locationDo);
        photoInfo.setLocationGu(photo.locationGu);
        photoInfo.setLocationStreet(photo.locationStreet);

        return photoInfo;
    }

    /**
     * 사진이 있는 날짜 목록 가져오기
     */
    public List<String> getAvailableDates() {
        return database.photoDao().getAvailableDates();
    }

    /**
     * 모든 사진 정보 가져오기
     */
    public List<PhotoInfo> getAllPhotos() {
        List<Photo> photos = database.photoDao().getAllPhotos();
        List<PhotoInfo> photoInfos = new ArrayList<>();

        for (Photo photo : photos) {
            PhotoInfo photoInfo = convertToPhotoInfo(photo);
            photoInfos.add(photoInfo);
        }

        return photoInfos;
    }

    /**
     * 객체 포함된 사진 목록 가져오기
     */
    public List<Photo> getPhotosWithObjects() {
        return database.photoDao().getPhotosWithObjects();
    }

    /**
     * 파일 경로로 사진 존재 여부 확인
     */
    public boolean isPhotoAlreadyExists(String filePath) {
        return database.photoDao().getPhotoByFilePath(filePath) != null;
    }

    /**
     * 사진 정보 저장
     */
    public long savePhoto(Photo photo) {
        // 위치 정보가 있는 경우, Google Geocoder를 사용하여 주소 정보 보강
        if (photo.latitude != null && photo.longitude != null) {
            try {
                Photo enrichedPhoto = locationUtils.enrichPhotoWithLocationInfo(photo).get();
                database.photoDao().insertPhoto(enrichedPhoto);
            } catch (Exception e) {
                Log.e(TAG, "Error enriching photo with location info", e);
                database.photoDao().insertPhoto(photo);
            }
        } else {
            database.photoDao().insertPhoto(photo);
        }

        // 성공적으로 저장된 경우 id 반환
        Photo savedPhoto = database.photoDao().getPhotoByFilePath(photo.filePath);
        return savedPhoto != null ? savedPhoto.id : -1;
    }

//    /**
//     * 사진 정보 갱신
//     */
//    public void updatePhoto(Photo photo) {
//        database.photoDao().updateFullAddress(photo.filePath, photo.fullAddress);
//        if (photo.detectedObjects != null) {
//            database.photoDao().updateDetectedObjectPairs(photo.filePath, photo.detectedObjects);
//        }
//    }

    /**
     * 사진 정보 삭제
     */
    public void deletePhoto(String filePath) {
        Photo photo = database.photoDao().getPhotoByFilePath(filePath);
        if (photo != null) {
            // 여기서는 PhotoDao에 delete 메소드가 없어서 구현할 수 없음
            // 실제로는 아래와 같이 구현해야 함
            // database.photoDao().deletePhoto(photo);
            Log.e(TAG, "deletePhoto method is not fully implemented due to missing DAO method");
        }
    }

    /**
     * PhotoInfo 객체에서 Photo 객체로 변환
     */
    public Photo convertToPhoto(PhotoInfo photoInfo) {
        Photo photo = new Photo();
        photo.filePath = photoInfo.getFilePath();

        // 날짜 변환
        if (photoInfo.getDateTaken() != null) {
            photo.dateTaken = dateFormat.format(photoInfo.getDateTaken());
        }

        // 위치 정보 변환
        if (photoInfo.getLatLng() != null) {
            photo.latitude = photoInfo.getLatLng().latitude;
            photo.longitude = photoInfo.getLatLng().longitude;
        }

        // 주소 정보 설정
        photo.locationDo = photoInfo.getLocationDo();
        photo.locationGu = photoInfo.getLocationGu();
        photo.locationStreet = photoInfo.getLocationStreet();
        photo.fullAddress = photoInfo.getAddress();

        // 설명 및 캡션
        photo.caption = photoInfo.getDescription();

        // 객체 목록 변환
        if (photoInfo.getDetectedObjectPairs() != null) {
            photo.setDetectedObjectPairs(photoInfo.getDetectedObjectPairs());
        }

        return photo;
    }

    /**
     * 모든 사진의 위치 정보 보강 (백그라운드 작업으로 실행)
     */
    public CompletableFuture<Void> enrichAllPhotosWithLocationInfo() {
        return CompletableFuture.runAsync(() -> {
            List<Photo> photos = database.photoDao().getAllPhotos();

            for (Photo photo : photos) {
                if (photo.latitude != null && photo.longitude != null) {
                    try {
                        Photo enrichedPhoto = locationUtils.enrichPhotoWithLocationInfo(photo).get();
//                        updatePhoto(enrichedPhoto);
                    } catch (Exception e) {
                        Log.e(TAG, "Error enriching photo with location info", e);
                    }
                }
            }
        });
    }

    /**
     * 특정 국가(country)의 사진 목록을 반환
     */
    public CompletableFuture<List<Photo>> getPhotosByCountry(String country) {
        return CompletableFuture.supplyAsync(() ->
                database.photoDao().getPhotosByCountry(country)
        );
    }

}