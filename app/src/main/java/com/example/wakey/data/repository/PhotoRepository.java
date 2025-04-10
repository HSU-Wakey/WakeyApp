package com.example.wakey.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;

import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.PhotoInfo;
import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class PhotoRepository {
    private static final String TAG = "PhotoRepository";
    private static PhotoRepository instance;

    private final Context context;
    private final AppDatabase appDatabase;

    public static PhotoRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PhotoRepository(context.getApplicationContext());
        }
        return instance;
    }

    public PhotoRepository(Context context) {
        this.context = context;
        this.appDatabase = Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "AppDatabase"
        ).fallbackToDestructiveMigration().build();
    }

    public boolean isPhotoAlreadyExists(String filePath) {
        Photo existing = appDatabase.photoDao().getPhotoByFilePath(filePath);
        return existing != null;
    }

    public void removeDuplicatePhotos() {
        new Thread(() -> {
            appDatabase.photoDao().deleteDuplicatePhotos();
            Log.d(TAG, "🧹 중복된 사진 삭제 완료");
        }).start();
    }

    public List<Photo> getPhotosWithObjects() {
        return appDatabase.photoDao().getPhotosWithObjects();
    }

    public List<PhotoInfo> getPhotosForDate(String dateString) {
        Log.d(TAG, "📆 날짜 기반 사진 조회 요청: " + dateString);

        List<Photo> photoList = appDatabase.photoDao().getPhotosForDate(dateString);
        Log.d("DB_CHECK", "📦 DB에서 사진 개수: " + (photoList != null ? photoList.size() : 0));
        Log.d(TAG, "📸 불러온 Photo 개수: " + (photoList != null ? photoList.size() : 0));

        for (Photo photo : photoList) {
            Log.d(TAG, "✅ dateTaken in DB = " + photo.dateTaken);
        }

        List<PhotoInfo> photoInfoList = new ArrayList<>();
        for (Photo photo : photoList) {
            Log.d(TAG, "🖼️ 파일: " + photo.filePath + " / 날짜: " + photo.dateTaken + " / 객체: " + photo.detectedObjects);

            // ✅ 위경도 로그 및 필터링
            Log.d("LATLNG_CHECK", "📍 위도: " + photo.latitude + ", 경도: " + photo.longitude);
            LatLng latLng = null;
            if (photo.latitude != null && photo.longitude != null &&
                    (photo.latitude != 0.0 || photo.longitude != 0.0)) {
                latLng = new LatLng(photo.latitude, photo.longitude);
                Log.d("LATLNG_CHECK", "✅ 유효한 LatLng 생성됨: " + latLng.toString());
            } else {
                Log.w("LATLNG_CHECK", "⚠️ 유효하지 않은 위치 → null 처리됨");
            }

            // ✅ 주소 조합 (null-safe)
            String doStr = photo.locationDo != null ? photo.locationDo : "";
            String guStr = photo.locationGu != null ? photo.locationGu : "";
            String streetStr = photo.locationStreet != null ? photo.locationStreet : "";
            String address = (doStr + " " + guStr + " " + streetStr).trim();

            Log.d(TAG, "🏠 주소: " + address);

            PhotoInfo info = new PhotoInfo(
                    photo.filePath,
                    parseDate(photo.dateTaken),
                    latLng,
                    null,
                    null,
                    address,
                    photo.caption,
                    parseDetectedObjects(photo.detectedObjects)
            );
            photoInfoList.add(info);
        }

        return photoInfoList;
    }

    public List<PhotoInfo> getAllPhotos() {
        List<String> dates = getAvailableDates();
        Log.d(TAG, "📅 DB에 존재하는 날짜 리스트: " + dates);
        return getPhotosForDateRange(dates);
    }

    private List<PhotoInfo> getPhotosForDateRange(List<String> dates) {
        List<PhotoInfo> allPhotos = new ArrayList<>();
        for (String date : dates) {
            Log.d(TAG, "🔍 날짜별 검색 시도 중: " + date);
            allPhotos.addAll(getPhotosForDate(date));
        }
        return allPhotos;
    }

    public List<String> getAvailableDates() {
        List<String> dates = appDatabase.photoDao().getAvailableDates();
        Log.d(TAG, "📆 [확인용] DB에서 추출된 날짜들 = " + dates);

        List<Photo> all = appDatabase.photoDao().getAllPhotos();
        for (Photo photo : all) {
            Log.d(TAG, "📂 [전체 DB 사진 로그] filePath = " + photo.filePath + ", dateTaken = " + photo.dateTaken);
        }

        return dates != null ? dates : new ArrayList<>();
    }

    private Date parseDate(String dateString) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            return format.parse(dateString);
        } catch (ParseException e) {
            Log.e(TAG, "❌ 날짜 파싱 실패: " + dateString, e);
            return new Date();
        }
    }

    private List<String> parseDetectedObjects(String objectsString) {
        if (objectsString == null || objectsString.isEmpty()) return new ArrayList<>();
        return Arrays.asList(objectsString.split(","));
    }

    public AppDatabase getAppDatabase() {
        return appDatabase;
    }
}
