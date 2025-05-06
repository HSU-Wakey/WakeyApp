package com.example.wakey.util;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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

}
