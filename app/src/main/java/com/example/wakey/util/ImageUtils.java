package com.example.wakey.util;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    /**
     * 경로에서 썸네일을 로드하고 EXIF 회전 정보를 적용하여 반환
     * (민서클린 브랜치에서 가져옴)
     */
    public static Bitmap loadThumbnailFromPath(Context context, String path) {
        try {
            Log.d("MapCheck", "📂 썸네일 로드 시도: " + path);
            if (path == null || path.isEmpty()) return null;

            Uri uri = Uri.parse(path);
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 8;
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            // 회전 정보 적용
            InputStream exifInput = context.getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(exifInput);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            exifInput.close();

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
            }

            if (!matrix.isIdentity()) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            Log.d("MapCheck", "✅ 썸네일 로드 및 회전 보정 완료");
            return bitmap;

        } catch (Exception e) {
            Log.e("MapCheck", "❌ 썸네일 로딩 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * EXIF 메타데이터에서 위치 정보 추출
     */
    public static Location getExifLocation(Context context, Uri uri) {
        try {
            InputStream stream = context.getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(stream);
            float[] latLong = new float[2];
            if (exif.getLatLong(latLong)) {
                Location loc = new Location("");
                loc.setLatitude(latLong[0]);
                loc.setLongitude(latLong[1]);
                return loc;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * EXIF 메타데이터에서 촬영 일자 추출
     */
    public static String getExifDateTaken(Context context, Uri uri) {
        try {
            InputStream stream = context.getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(stream);
            return exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * URI에서 비트맵 이미지 로드
     */
    public static Bitmap loadBitmapFromUri(Context context, Uri uri) {
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 기기의 모든 이미지 URI 목록 가져오기
     */
    public static List<Uri> getAllImageUris(Context context) {
        List<Uri> imageUris = new ArrayList<>();
        String[] projection = { MediaStore.Images.Media._ID };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    imageUris.add(uri);
                    Log.d(TAG, "📸 URI 불러옴: " + uri.toString()); // ✅ 로그 추가
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imageUris;
    }

    /**
     * 비트맵을 JPEG 파일로 저장
     * (마스터 브랜치에서 가져옴)
     */
    public static void saveBitmapToJpeg(Context context, Bitmap bitmap, String fileName) {
        File directory = new File(context.getExternalFilesDir(null), "yolo_debug");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file = new File(directory, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            Log.d("ImageUtils", "🖼️ YOLO 결과 저장됨: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e("ImageUtils", "❌ 이미지 저장 실패", e);
        }
    }

    /**
     * 비트맵에 사각형 박스 그리기
     * (마스터 브랜치에서 가져옴)
     */
    public static Bitmap drawBoxesOnBitmap(Bitmap original, List<RectF> boxes) {
        Bitmap mutable = original.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        for (RectF box : boxes) {
            canvas.drawRect(box, paint);
        }
        return mutable;
    }

    /**
     * 비트맵 이미지 회전 (EXIF 정보 기반)
     * 여러 메서드에서 공통으로 사용할 수 있도록 별도 메서드로 추출
     */
    public static Bitmap rotateBitmapByExif(Context context, Bitmap bitmap, Uri uri) {
        try {
            InputStream exifInput = context.getContentResolver().openInputStream(uri);
            if (exifInput == null) return bitmap;

            ExifInterface exif = new ExifInterface(exifInput);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            exifInput.close();

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                default: return bitmap; // 회전이 필요 없으면 원본 반환
            }

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            Log.e(TAG, "이미지 회전 실패: " + e.getMessage());
            return bitmap; // 오류 발생 시 원본 반환
        }
    }

    /**
     * EXIF 회전이 적용된 비트맵 로드
     * loadBitmapFromUri에 회전 기능 추가
     */
    public static Bitmap loadRotatedBitmapFromUri(Context context, Uri uri) {
        try {
            Bitmap bitmap = loadBitmapFromUri(context, uri);
            if (bitmap == null) return null;

            return rotateBitmapByExif(context, bitmap, uri);
        } catch (Exception e) {
            Log.e(TAG, "회전된 이미지 로드 실패: " + e.getMessage());
            return null;
        }
    }
}