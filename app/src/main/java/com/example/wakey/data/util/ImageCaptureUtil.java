// data/util/ImageCaptureUtil.java
package com.example.wakey.data.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageCaptureUtil {

    public static String captureMapView(Context context, GoogleMap map, LatLng location) {
        try {
            // 맵 이미지 캡처 (GoogleMap API는 스크린샷을 직접 지원하지 않아 대안 필요)
            // 참고: 실제 구현에서는 GoogleMap.snapshot() 메소드 사용 가능

            // 임시 파일 생성
            File cacheDir = context.getCacheDir();
            File imageFile = new File(cacheDir, "search_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg");

            // 맵 스크린샷 캡처
            map.snapshot(bitmap -> {
                try {
                    FileOutputStream out = new FileOutputStream(imageFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 검색 결과 화면 캡처 (결과 리스트나 타임라인)
    public static String captureSearchResultView(Context context, View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    view.getWidth(),
                    view.getHeight(),
                    Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

            // 임시 파일 생성
            File cacheDir = context.getCacheDir();
            File imageFile = new File(cacheDir, "search_result_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg");

            FileOutputStream out = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}