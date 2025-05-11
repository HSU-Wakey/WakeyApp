package com.example.wakey.data.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

import androidx.room.Room;

import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.ImageMeta;
import com.example.wakey.tflite.ImageClassifier;
import com.example.wakey.tflite.Yolov8Detector;
import com.example.wakey.data.util.ExifUtil;
import com.example.wakey.tflite.ClipImageEncoder;
import com.example.wakey.util.FileUtils;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.LocationUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ImageRepository {
    private final ImageClassifier imageClassifier;
    private final Yolov8Detector yolov8Detector;
    private final Context context;
    private final AppDatabase db;
    private final PhotoRepository photoRepository;
    private final ClipImageEncoder clipImageEncoder;
    private final LocationUtils locationUtils;

    public ImageRepository(Context context) {
        this.context = context;
        try {
            this.clipImageEncoder = new ClipImageEncoder(context);
            this.imageClassifier = new ImageClassifier(context);
            this.yolov8Detector = new Yolov8Detector(context);
        } catch (Exception e) {
            throw new RuntimeException("모델 로드 실패", e);
        }
        db = Room.databaseBuilder(context, AppDatabase.class, "AppDatabase").build();
        photoRepository = PhotoRepository.getInstance(context);
        locationUtils = LocationUtils.getInstance(context);
    }

    public ImageMeta classifyImage(Uri uri, Bitmap bitmap) {
        float[] embeddingVector = clipImageEncoder.getImageEncoding(bitmap);
        Long nameOfImage = System.currentTimeMillis();

        // 전체 이미지 분류
        List<Pair<String, Float>> globalPredictions = imageClassifier.classifyImage(bitmap);
        Log.d("ImageRepository", "🌍 전체 이미지 분류 결과: " + globalPredictions);

        // 누적용 map
        Map<String, Float> scoreMap = new HashMap<>();
        for (Pair<String, Float> p : globalPredictions) {
            scoreMap.put(p.first, scoreMap.getOrDefault(p.first, 0f) + p.second);
        }

        // YOLO 객체 탐지
        List<RectF> detectedBoxes = yolov8Detector.detect(bitmap);
        Log.d("ImageRepository", "🔍 YOLOv8 감지 객체 수: " + detectedBoxes.size());

//        // 박스 시각화 저장
//        Bitmap boxedBitmap = ImageUtils.drawBoxesOnBitmap(bitmap, detectedBoxes);
//        ImageUtils.saveBitmapToJpeg(context, boxedBitmap, "yolo_result_" + nameOfImage + ".jpg");

        // Crop 영역 분류 및 누적
        int index = 0;
        for (RectF box : detectedBoxes) {
            try {
                Bitmap cropped = Bitmap.createBitmap(
                        bitmap,
                        Math.max((int) box.left, 0),
                        Math.max((int) box.top, 0),
                        Math.min((int) box.width(), bitmap.getWidth() - (int) box.left),
                        Math.min((int) box.height(), bitmap.getHeight() - (int) box.top)
                );
                List<Pair<String, Float>> cropResults = imageClassifier.classifyImage(cropped);
                Log.d("ImageRepository", "📦 객체 " + index + " 박스: " + box.toString());
                Log.d("ImageRepository", "   └ 분류 결과: " + cropResults);

                for (Pair<String, Float> p : cropResults) {
                    scoreMap.put(p.first, scoreMap.getOrDefault(p.first, 0f) + p.second);
                }
                index++;
            } catch (Exception e) {
                Log.e("ImageRepository", "❌ 객체 " + index + " Crop 실패", e);
            }
        }

        // 합산 결과 Top-3 정렬
        List<Pair<String, Float>> mergedPredictions = new ArrayList<>();
        for (Map.Entry<String, Float> entry : scoreMap.entrySet()) {
            mergedPredictions.add(new Pair<>(entry.getKey(), entry.getValue()));
        }
        mergedPredictions.sort((a, b) -> Float.compare(b.second, a.second));
        List<Pair<String, Float>> top3Predictions = mergedPredictions.subList(0, Math.min(3, mergedPredictions.size()));
        Log.d("ImageRepository", "🥇 최종 Top-3 예측 결과: " + top3Predictions);

        // 지역 정보
        String region = null;
        Location location = ImageUtils.getExifLocation(context, uri);
        if (location != null) {
            region = locationUtils.getRegionFromLocation(location);
        }

        return new ImageMeta(uri.toString(), region, top3Predictions, embeddingVector);
    }

    public void savePhotoToDB(Uri uri, ImageMeta meta) {
        new Thread(() -> {
            try {
                String filePath = uri.toString();
                if (photoRepository.isPhotoAlreadyExists(filePath)) {
                    Log.d("ImageRepository", "⚠️ 중복 사진 → 저장 생략됨: " + filePath);
                    return;
                }

                List<Pair<String, Float>> detectedPairs = meta.getPredictions();  // ✅ 변경
                String dateTaken = ImageUtils.getExifDateTaken(context, uri);

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

                String locationDo = null, locationSi = null, locationGu = null, locationStreet = null;
                Double latitude = null, longitude = null;

                double[] latLng = ExifUtil.getLatLngFromExif(FileUtils.getPath(context, uri));
                if (latLng != null) {
                    latitude = latLng[0];
                    longitude = latLng[1];

                    Geocoder geocoder = new Geocoder(context, Locale.KOREA);
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
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

                // ✅ 기본 생성자 사용 후 set 메서드 활용
                Photo photo = new Photo();
                photo.filePath = filePath;
                photo.dateTaken = dateTaken;
                photo.locationDo = locationDo;
                photo.locationSi = locationSi;
                photo.locationGu = locationGu;
                photo.locationStreet = locationStreet;
                photo.caption = "";
                photo.latitude = latitude;
                photo.longitude = longitude;
                photo.setDetectedObjectPairs(detectedPairs);  // ✅ 핵심 변경점

                float[] embeddingVector = meta.getEmbeddingVector();
                if (embeddingVector != null) {
                    photo.setEmbeddingVector(embeddingVector);
                }

                Log.d("ImageRepository", "🧬 저장 전 벡터 길이: " + (embeddingVector != null ? embeddingVector.length : 0));
                Log.d("ImageRepository", "📥 저장될 객체 정보: " + detectedPairs);

                db.photoDao().insertPhoto(photo);
                Log.d("ImageRepository", "✅ DB 저장 완료");

            } catch (Exception e) {
                Log.e("ImageRepository", "🛑 DB 저장 실패", e);
            }
        }).start();
    }


    public void deleteAllPhotos() {
        new Thread(() -> {
            try {
                db.photoDao().deleteAllPhotos();
                Log.d("ImageRepository", "🗑️ DB 내 모든 사진 삭제 완료");
            } catch (Exception e) {
                Log.e("ImageRepository", "🛑 DB 전체 삭제 실패", e);
            }
        }).start();
    }

    public void close() {
        imageClassifier.close();
        yolov8Detector.close();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
}
