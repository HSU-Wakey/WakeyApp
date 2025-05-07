package com.example.wakey.util;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;

public class ImageUtils {
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
    private static final String TAG = "ImageUtils";

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

    public static Bitmap loadBitmapFromUri(Context context, Uri uri) {
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ✅ MediaStore에서 모든 이미지 URI 가져오기
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
}
