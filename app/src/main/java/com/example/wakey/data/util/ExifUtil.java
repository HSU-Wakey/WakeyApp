package com.example.wakey.data.util;

import android.util.Log;

import com.example.wakey.data.model.PhotoInfo;
import com.google.android.gms.maps.model.LatLng;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.util.Date;

public class ExifUtil {
    private static final String TAG = "ExifUtil";

    /**
     * EXIF에서 사진의 위치와 촬영일자 정보를 추출하여 PhotoInfo 생성
     */
    public static PhotoInfo extractPhotoInfo(String filePath, long dateTakenMillis) {
        try {
            ExifInterface exifInterface = new ExifInterface(filePath);
            float[] latLong = new float[2];
            boolean hasLatLong = exifInterface.getLatLong(latLong);

            LatLng latLng = null;
            if (hasLatLong && !(latLong[0] == 0f && latLong[1] == 0f)) {
                latLng = new LatLng(latLong[0], latLong[1]);
            } else {
                Log.w(TAG, "📍 유효하지 않은 위치 정보: lat=" + latLong[0] + ", lng=" + latLong[1]);
            }

            return new PhotoInfo(filePath, new Date(dateTakenMillis), latLng);
        } catch (IOException e) {
            Log.e(TAG, "❌ EXIF 추출 오류: " + filePath, e);
            return null;
        }
    }

    /**
     * GPS 위치만 따로 추출하는 메서드 (ImageRepository 등에서 사용)
     */
    public static double[] getLatLngFromExif(String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);

            // ✅ GPS 관련 태그 직접 출력
            String latStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String lngStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String lngRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);

            Log.d(TAG, "🧭 TAG_GPS_LATITUDE = " + latStr);
            Log.d(TAG, "🧭 TAG_GPS_LATITUDE_REF = " + latRef);
            Log.d(TAG, "🧭 TAG_GPS_LONGITUDE = " + lngStr);
            Log.d(TAG, "🧭 TAG_GPS_LONGITUDE_REF = " + lngRef);

            float[] latLong = new float[2];
            boolean hasLatLong = exif.getLatLong(latLong);

            if (hasLatLong && !(latLong[0] == 0f && latLong[1] == 0f)) {
                Log.d(TAG, "✅ GPS 좌표 추출 성공: " + latLong[0] + ", " + latLong[1]);
                return new double[]{latLong[0], latLong[1]};
            } else {
                Log.w(TAG, "📍 GPS 파싱 실패 또는 0.0 좌표: " + filePath);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ EXIF GPS 추출 실패: " + filePath, e);
            return null;
        }
    }

}
