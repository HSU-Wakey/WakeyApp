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
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.LocationUtils;

import java.util.List;
import java.util.Locale;

public class ImageRepository {
    private final ImageClassifier imageClassifier;
    private final Context context;
    private final AppDatabase db;

    public ImageRepository(Context context) {
        this.context = context;
        try {
            this.imageClassifier = new ImageClassifier(context);
        } catch (Exception e) {
            throw new RuntimeException("모델 로드 실패", e);
        }
        db = Room.databaseBuilder(context, AppDatabase.class, "smart_album_db").build();
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
                Location loc = ImageUtils.getExifLocation(context, uri);
                String detectedObjects = meta.getPredictions().toString();
                String dateTaken = ImageUtils.getExifDateTaken(context, uri);

                String locationDo = null;
                String locationSi = null;
                String locationGu = null;
                String locationStreet = null;
                Double latitude = null;
                Double longitude = null;

                if (loc != null) {
                    latitude = loc.getLatitude();
                    longitude = loc.getLongitude();

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

                Photo photo = new Photo(
                        uri.toString(),
                        dateTaken,
                        locationDo,
                        locationSi,
                        locationGu,
                        locationStreet,
                        latitude,
                        longitude,
                        detectedObjects,
                        null // caption
                );
                db.photoDao().insertPhoto(photo);
                Log.d("ImageRepository", "📥 Photo saved to DB with date: " + dateTaken);
            } catch (Exception e) {
                e.printStackTrace();
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
