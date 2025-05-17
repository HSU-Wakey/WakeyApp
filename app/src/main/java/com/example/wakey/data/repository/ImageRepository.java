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
import com.example.wakey.tflite.Yolov8Detector;
import com.example.wakey.util.FileUtils;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.LocationUtils;

import java.io.IOException;
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
        // 2. 벡터 추출 (CLIP)
        float[] embeddingVector = null;

        try {
            ClipImageEncoder encoder = new ClipImageEncoder(context); // 매번 새로 생성
            embeddingVector = encoder.getImageEncoding(bitmap);       // 임베딩 추출
            encoder.close(); // 꼭 해줘야 메모리 누수 방지
        } catch (IOException e) {
            Log.e("ImageRepository", "❌ CLIP 모델 로딩 실패", e);
        }

        Log.d("ImageRepository", "🧬 CLIP 임베딩 벡터 길이: " + (embeddingVector != null ? embeddingVector.length : -1));
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
            // LocationUtils 인스턴스 메서드 사용
            region = locationUtils.getRegionFromLocation(location);
        }

        return new ImageMeta(uri.toString(), region, top3Predictions, embeddingVector);
    }

    public Photo savePhotoToDB(Uri uri, ImageMeta meta) {
        final Photo[] result = new Photo[1];  // 결과를 저장할 배열

        Thread thread = new Thread(() -> {
            try {
                String filePath = uri.toString();

                // 중복 검사
                if (photoRepository.isPhotoAlreadyExists(filePath)) {
                    Log.d("ImageRepository", "⚠️ 중복 사진 → 저장 생략됨: " + filePath);
                    result[0] = null;  // 중복인 경우 null 반환
                    return;
                }

                List<Pair<String, Float>> detectedPairs = meta.getPredictions();  // ✅ 변경
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
                String countryName = null;

                // ExifUtil을 사용해서 GPS 추출
                double[] latLng = ExifUtil.getLatLngFromExif(FileUtils.getPath(context, uri)); // 절대 경로 필요
                if (latLng != null) {
                    latitude = latLng[0];
                    longitude = latLng[1];

                    // 위도/경도로 주소 파싱
                    Geocoder geocoder = new Geocoder(context, Locale.KOREA);
                    List<Address> addresses = null;
                    try {
                        addresses = geocoder.getFromLocation(latitude, longitude, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            Address addr = addresses.get(0);
                            locationDo = addr.getAdminArea();
                            locationSi = addr.getLocality();
                            locationGu = addr.getSubLocality() != null ? addr.getSubLocality() : addr.getThoroughfare();

                            // 도로명 + 번지 통합
                            String thoroughfare = addr.getThoroughfare() != null ? addr.getThoroughfare() : "";
                            String featureName = addr.getFeatureName() != null ? addr.getFeatureName() : "";
                            locationStreet = (thoroughfare + " " + featureName).trim();
                            countryName = addr.getCountryName();

                        }
                    } catch (Exception e) {
                        Log.e("ImageRepository", "❌ 주소 변환 실패", e);
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
                photo.country = countryName;


                // embeddingVector가 있을 경우 photo에 설정
                float[] embeddingVector = meta.getEmbeddingVector();
                if (embeddingVector != null) {
                    photo.setEmbeddingVector(embeddingVector); // ✅ 이 라인 없으면 저장 안 됨
                }

                Log.d("ImageRepository", "📥 저장될 객체 정보: " + detectedPairs);

                // 실제 Room에 저장
                db.photoDao().insertPhoto(photo);

                // 저장된 Photo 객체 다시 가져오기
                Photo savedPhoto = db.photoDao().getPhotoByPath(photo.filePath);
                result[0] = savedPhoto;

                Log.d("ImageRepository", "📥 Photo saved to DB with date: " + dateTaken);
                Log.d("ImageRepository", "📥 Photo saved to DB with 벡터 길이: " +
                        (embeddingVector != null ? embeddingVector.length : 0));
            } catch (Exception e) {
                Log.e("ImageRepository", "🛑 사진 저장 중 오류 발생", e);
                result[0] = null;
            }
        });

        thread.start();
        try {
            thread.join();  // 스레드가 완료될 때까지 기다림
        } catch (InterruptedException e) {
            Log.e("ImageRepository", "Thread interrupted", e);
            return null;
        }

        return result[0];

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

    // 앱이 종료될 때 리소스 정리
    public void close() {
        if (imageClassifier != null) {
            imageClassifier.close();
        }
        if (yolov8Detector != null) {
            yolov8Detector.close();
        }
        // db 인스턴스도 닫아주기
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
}