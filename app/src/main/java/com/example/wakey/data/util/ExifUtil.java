package com.example.wakey.data.util;

import android.util.Log;

import com.example.wakey.data.model.PhotoInfo;
import com.google.android.gms.maps.model.LatLng;

import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.util.Date;

public class ExifUtil {
    private static final String TAG = "ExifUtil";

    public static PhotoInfo extractPhotoInfo(String filePath, long dateTakenMillis) {
        try {
            ExifInterface exifInterface = new ExifInterface(filePath);
            float[] latLong = new float[2];
            boolean hasLatLong = exifInterface.getLatLong(latLong);

            LatLng latLng = hasLatLong ? new LatLng(latLong[0], latLong[1]) : null;
            return new PhotoInfo(filePath, new Date(dateTakenMillis), latLng);
        } catch (IOException e) {
            Log.e(TAG, "EXIF 추출 오류: " + filePath, e);
            return null;
        }
    }
}