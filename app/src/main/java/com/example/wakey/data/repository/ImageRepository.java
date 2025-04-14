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
import com.example.wakey.tflite.ImageClassifier;
import com.example.wakey.data.util.ExifUtil;
import com.example.wakey.util.FileUtils;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.LocationUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ImageRepository {
    private final ImageClassifier imageClassifier;
    private final Context context;
    private final AppDatabase db;
    private final PhotoRepository photoRepository;

    public ImageRepository(Context context) {
        this.context = context;
        try {
            this.imageClassifier = new ImageClassifier(context);
        } catch (Exception e) {
            throw new RuntimeException("모델 로드 실패", e);
        }
        db = Room.databaseBuilder(context, AppDatabase.class, "AppDatabase").build();
        photoRepository = PhotoRepository.getInstance(context);
    }

    public ImageMeta classifyImage(Uri uri, Bitmap bitmap) {
        List<Pair<String, Float>> predictions = imageClassifier.classifyImage(bitmap);
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
                String absolutePath = FileUtils.getPath(context, uri);

                // 중복 검사 (절대 경로 기준)
                if (photoRepository.isPhotoAlreadyExists(absolutePath)) {
                    Log.d("ImageRepository", "⚠️ 중복 사진 → 저장 생략됨: " + absolutePath);
                    Log.d("DB_CHECK", "⚠️ 이미 존재 → 저장 안함: " + uri.toString());
                    return;
                }

                String detectedObjects = meta.getPredictions().toString();
                String dateTaken = ImageUtils.getExifDateTaken(context, uri);
                Log.d("ImageRepository", "🕒 원본 dateTaken: " + dateTaken);

                if (dateTaken == null || dateTaken.isEmpty()) {
                    dateTaken = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date());
                } else if (dateTaken.contains(":")) {
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

                double[] latLng = ExifUtil.getLatLngFromExif(FileUtils.getPath(context, uri));
                if (latLng != null) {
                    latitude = latLng[0];
                    longitude = latLng[1];

                    List<Address> addresses = new Geocoder(context, Locale.KOREA)
                            .getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        locationDo = addr.getAdminArea();
                        locationSi = addr.getLocality();
                        locationGu = addr.getSubLocality() != null ? addr.getSubLocality() : addr.getThoroughfare();
                        String thoroughfare = addr.getThoroughfare() != null ? addr.getThoroughfare() : "";
                        String featureName = addr.getFeatureName() != null ? addr.getFeatureName() : "";
                        locationStreet = (thoroughfare + " " + featureName).trim();
                    }
                }

                Photo photo = new Photo(
                        absolutePath,
                        dateTaken,
                        locationDo,
                        locationSi,
                        locationGu,
                        locationStreet,
                        "",
                        latitude,
                        longitude,
                        detectedObjects,
                        meta.getPredictions()
                );

                db.photoDao().insertPhoto(photo);
                Log.d("ImageRepository", "📥 Photo saved to DB with date: " + dateTaken);
                Log.d("DB_CHECK", "✅ DB에 저장됨: " + uri.toString());
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
        imageClassifier.close();
    }
}