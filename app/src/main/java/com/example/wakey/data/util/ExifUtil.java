// data/util/ExifUtil.java
package com.example.wakey.data.util;

import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.example.wakey.data.model.PhotoInfo;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.Date;

public class ExifUtil {
    private static final String TAG = "ExifUtil";

    // 사진 정보 추출
    public static PhotoInfo extractPhotoInfo(String filePath, long dateTakenMillis) {
        try {
            ExifInterface exifInterface = new ExifInterface(filePath);
            float[] latLong = new float[2];
            boolean hasLatLong = exifInterface.getLatLong(latLong);

            if (!hasLatLong) {
                return null; // 위치 정보 없는 사진은 건너뜀
            }

            Date dateTaken = new Date(dateTakenMillis);

            // 확장된 메타데이터 추출
            String deviceModel = exifInterface.getAttribute(ExifInterface.TAG_MODEL);
            String focalLengthStr = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
            float focalLength = 0;
            if (focalLengthStr != null) {
                try {
                    // 분수 형태(예: "24/1")로 표현된 값 처리
                    if (focalLengthStr.contains("/")) {
                        String[] parts = focalLengthStr.split("/");
                        if (parts.length == 2) {
                            float numerator = Float.parseFloat(parts[0]);
                            float denominator = Float.parseFloat(parts[1]);
                            if (denominator != 0) {
                                focalLength = numerator / denominator;
                            }
                        }
                    } else {
                        focalLength = Float.parseFloat(focalLengthStr);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing focal length: " + focalLengthStr, e);
                }
            }

            String lensModel = exifInterface.getAttribute(ExifInterface.TAG_LENS_MODEL);
            String flash = exifInterface.getAttribute(ExifInterface.TAG_FLASH);
            boolean hasFlash = flash != null && !flash.equals("0");

            String apertureStr = exifInterface.getAttribute(ExifInterface.TAG_APERTURE_VALUE);
            float aperture = 0;
            if (apertureStr != null) {
                try {
                    if (apertureStr.contains("/")) {
                        String[] parts = apertureStr.split("/");
                        if (parts.length == 2) {
                            float numerator = Float.parseFloat(parts[0]);
                            float denominator = Float.parseFloat(parts[1]);
                            if (denominator != 0) {
                                aperture = numerator / denominator;
                            }
                        }
                    } else {
                        aperture = Float.parseFloat(apertureStr);
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing aperture: " + apertureStr, e);
                }
            }

            return new PhotoInfo(
                    filePath,
                    dateTaken,
                    new LatLng(latLong[0], latLong[1]),
                    deviceModel,
                    focalLength,
                    lensModel,
                    hasFlash,
                    aperture);
        } catch (IOException e) {
            Log.e(TAG, "Error extracting EXIF data", e);
            return null;
        }
    }
}