// data/repository/PhotoRepository.java
package com.example.wakey.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.util.ExifUtil;
import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PhotoRepository {
    private static final String TAG = "PhotoRepository";
    private static PhotoRepository instance;

    private final Context context;
    private final Map<String, List<PhotoInfo>> dateToPhotosMap;
    private final Map<String, List<LatLng>> dateToRouteMap;

    // 싱글톤 인스턴스 얻기
    public static PhotoRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PhotoRepository(context.getApplicationContext());
        }
        return instance;
    }

    private PhotoRepository(Context context) {
        this.context = context;
        this.dateToPhotosMap = new HashMap<>();
        this.dateToRouteMap = new HashMap<>();
    }

    // 전체 사진 로드
    public void loadAllPhotos() {
        // 데이터 초기화
        dateToPhotosMap.clear();
        dateToRouteMap.clear();

        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATA
        };

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC")) {

            if (cursor != null) {
                int dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int dateTakenColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);

                while (cursor.moveToNext()) {
                    String filePath = cursor.getString(dataColumnIndex);
                    long dateTakenMillis = cursor.getLong(dateTakenColumnIndex);

                    // EXIF 추출은 유틸 클래스에 위임
                    PhotoInfo photoInfo = ExifUtil.extractPhotoInfo(filePath, dateTakenMillis);

                    if (photoInfo != null) {
                        // 날짜별로 저장
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        String dateString = dateFormat.format(photoInfo.getDateTaken());

                        if (!dateToPhotosMap.containsKey(dateString)) {
                            dateToPhotosMap.put(dateString, new ArrayList<>());
                            dateToRouteMap.put(dateString, new ArrayList<>());
                        }

                        dateToPhotosMap.get(dateString).add(photoInfo);
                        dateToRouteMap.get(dateString).add(photoInfo.getLatLng());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading photos", e);
        }
    }

    // 특정 날짜의 사진 가져오기
    public List<PhotoInfo> getPhotosForDate(String dateString) {
        return dateToPhotosMap.getOrDefault(dateString, new ArrayList<>());
    }

    // 특정 날짜의 경로 가져오기
    public List<LatLng> getRouteForDate(String dateString) {
        return dateToRouteMap.getOrDefault(dateString, new ArrayList<>());
    }

    public List<PhotoInfo> getAllPhotos() {
        List<String> dates = getAvailableDates();
        List<PhotoInfo> allPhotos = new ArrayList<>();

        for (String date : dates) {
            List<PhotoInfo> photosForDate = getPhotosForDate(date);
            if (photosForDate != null) {
                allPhotos.addAll(photosForDate);
            }
        }
        return allPhotos;
    }

    // 사진이 있는 날짜 목록 가져오기
    public List<String> getAvailableDates() {
        return new ArrayList<>(dateToPhotosMap.keySet());
    }
}