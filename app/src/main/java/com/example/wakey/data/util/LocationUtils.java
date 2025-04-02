package com.example.wakey.data.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

import java.util.List;
import java.util.Locale;

public class LocationUtils {
    public static String getRegionFromLocation(Context context, Location location) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.KOREA);
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);

                Log.d("LocationUtils", "🌍 위도: " + location.getLatitude() + ", 경도: " + location.getLongitude());
                Log.d("LocationUtils", "🗺️ getAdminArea(): " + addr.getAdminArea());
                Log.d("LocationUtils", "🗺️ getSubAdminArea(): " + addr.getSubAdminArea());
                Log.d("LocationUtils", "🗺️ getLocality(): " + addr.getLocality());
                Log.d("LocationUtils", "🗺️ getSubLocality(): " + addr.getSubLocality());
                Log.d("LocationUtils", "🗺️ getThoroughfare(): " + addr.getThoroughfare());
                Log.d("LocationUtils", "🗺️ getFeatureName(): " + addr.getFeatureName());

                StringBuilder regionBuilder = new StringBuilder();

                // 도/광역시
                if (addr.getAdminArea() != null) {
                    regionBuilder.append(addr.getAdminArea()).append(" ");
                }

                // 시/군/구 (getLocality가 더 정확할 수 있음)
                if (addr.getLocality() != null) {
                    regionBuilder.append(addr.getLocality()).append(" ");
                } else if (addr.getSubAdminArea() != null) {
                    regionBuilder.append(addr.getSubAdminArea()).append(" ");
                }

                // 동 (우선순위: SubLocality > Thoroughfare)
                if (addr.getSubLocality() != null) {
                    regionBuilder.append(addr.getSubLocality());
                } else if (addr.getThoroughfare() != null) {
                    regionBuilder.append(addr.getThoroughfare());
                }

                return regionBuilder.toString().trim();
            }
        } catch (Exception e) {
            Log.e("LocationUtils", "주소 변환 중 오류", e);
        }
        return "지역 정보 없음";
    }

}
