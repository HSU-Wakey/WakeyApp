package com.example.wakey.data.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.room.Room;

import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.ImageMeta;
import com.example.wakey.tflite.BeitClassifier;
import com.example.wakey.data.util.ExifUtil;
import com.example.wakey.util.FileUtils;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.LocationUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ImageRepository {
    private final BeitClassifier beitClassifier;
    private final Context context;
    private final AppDatabase db;
    private final PhotoRepository photoRepository;

    public ImageRepository(Context context) {
        this.context = context;
        try {
            this.beitClassifier = new BeitClassifier(context);
        } catch (Exception e) {
            throw new RuntimeException("모델 로드 실패", e);
        }
        db = Room.databaseBuilder(context, AppDatabase.class, "AppDatabase").build();
        photoRepository = PhotoRepository.getInstance(context);
    }

    public ImageMeta classifyImage(Uri uri, Bitmap bitmap) {
        List<Pair<String, Float>> predictions = beitClassifier.classifyImage(bitmap);
        String region = null;
        Location location = ImageUtils.getExifLocation(context, uri);
        if (location != null) {
            region = LocationUtils.getRegionFromLocation(context, location);
        }
        return new ImageMeta(uri.toString(), region, predictions);
    }

    public void savePhotoToDB(Uri uri, ImageMeta meta) {
        new Thread(() -> {
            try {
                String filePath = uri.toString();

                // 중복 검사
                if (photoRepository.isPhotoAlreadyExists(filePath)) {
                    Log.d("ImageRepository", "⚠️ 중복 사진 → 저장 생략됨: " + filePath);
                    return;
                }

                String detectedObjects = meta.getPredictions().toString(); // List<Pair<String, Float>> -> String 변환
                String dateTaken = ImageUtils.getExifDateTaken(context, uri);
                Log.d("ImageRepository", "🕒 원본 dateTaken: " + dateTaken);

                // 포맷이 없거나 깨진 경우 대비: 현재 시간으로 설정
                if (dateTaken == null || dateTaken.isEmpty()) {
                    dateTaken = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date());
                }
                // "yyyy:MM:dd HH:mm:ss" 포맷일 경우 → 변환
                else if (dateTaken.contains(":")) {
                    try {
                        Date parsed = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).parse(dateTaken);
                        dateTaken = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(parsed);
                    } catch (Exception e) {
                        Log.e("ImageRepository", "❌ dateTaken 포맷 변환 실패: " + dateTaken);
                    }
                }

                Log.d("ImageRepository", "✅ 저장될 최종 dateTaken: " + dateTaken);

                String locationDo = null;
                String locationSi = null;
                String locationGu = null;
                String locationStreet = null;
                Double latitude = null;
                Double longitude = null;

                // ExifUtil을 사용해서 GPS 추출
                double[] latLng = ExifUtil.getLatLngFromExif(FileUtils.getPath(context, uri)); // 절대 경로 필요
                if (latLng != null) {
                    latitude = latLng[0];
                    longitude = latLng[1];

                    // 위도/경도로 주소 파싱
                    List<Address> addresses = new Geocoder(context, Locale.KOREA)
                            .getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        locationDo = addr.getAdminArea();
                        locationSi = addr.getLocality();
                        locationGu = addr.getSubLocality() != null ? addr.getSubLocality() : addr.getThoroughfare();

                        // 도로명 + 번지 통합
                        String thoroughfare = addr.getThoroughfare() != null ? addr.getThoroughfare() : "";
                        String featureName = addr.getFeatureName() != null ? addr.getFeatureName() : "";
                        locationStreet = (thoroughfare + " " + featureName).trim();
                    }
                }

                // DB 저장
                Photo photo = new Photo(
                        filePath,
                        dateTaken,
                        locationDo,
                        locationSi,
                        locationGu,
                        locationStreet,
                        "",             // caption
                        latitude,
                        longitude,
                        detectedObjects,
                        meta.getPredictions()
                );


                db.photoDao().insertPhoto(photo);
                Log.d("ImageRepository", "📥 Photo saved to DB with date: " + dateTaken);
            } catch (Exception e) {
                Log.e("ImageRepository", "🛑 사진 저장 중 오류 발생", e);
            }
        }).start();
    }

    public void printAllPhotos() {
        new Thread(() -> {
            List<Photo> photos = db.photoDao().getAllPhotos();
            for (Photo photo : photos) {
                Log.d("DB_CHECK", "🗂️ ID: " + photo.id +
                        ", filePath: " + photo.filePath +
                        ", date: " + photo.dateTaken +
                        ", Do: " + photo.locationDo +
                        ", Si: " + photo.locationSi +
                        ", Gu: " + photo.locationGu +
                        ", Street: " + photo.locationStreet);
            }
        }).start();
    }

    public void clearAllPhotos() {
        new Thread(() -> {
            db.photoDao().deleteAllPhotos();
            Log.d("ImageRepository", "🗑️ All photos deleted from DB");
        }).start();
    }

    public void close() {
        beitClassifier.close();
    }
}
