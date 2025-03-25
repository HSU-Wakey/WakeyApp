// service/CaptionService.java
package com.example.wakey.service;

import android.location.Address;
import android.text.TextUtils;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.PlaceData;
import com.example.wakey.data.util.DateUtil;

import java.util.Calendar;
import java.util.List;

public class CaptionService {

    // 싱글톤 패턴
    private static CaptionService instance;

    public static CaptionService getInstance() {
        if (instance == null) {
            instance = new CaptionService();
        }
        return instance;
    }

    private CaptionService() {}

    // EXIF, 위치, POI 데이터로 캡션 생성
    public String generateCaption(PhotoInfo photo, Address address, List<PlaceData> nearbyPlaces) {
        StringBuilder caption = new StringBuilder();

        // 1. 날짜와 시간 정보
        String dateStr = DateUtil.getFormattedDateWithDay(photo.getDateTaken());
        String timeStr = DateUtil.formatTime(photo.getDateTaken());

        caption.append(dateStr).append(" ").append(timeStr).append("에 ");

        // 2. 위치 정보
        String locationName = extractMeaningfulLocationName(address);
        if (!TextUtils.isEmpty(locationName)) {
            caption.append(locationName).append("에서 ");
        }

        // 3. 활동 정보 (시간 기반 추론)
        String activity = inferActivityType(photo, address);
        caption.append(activity).append(" 중에 ");

        // 4. 주변 POI 정보 통합
        if (nearbyPlaces != null && !nearbyPlaces.isEmpty()) {
            PlaceData topPlace = nearbyPlaces.get(0);  // 가장 관련성 높은 장소
            caption.append("근처의 ").append(topPlace.getName()).append("이(가) 있는 곳에서 ");
        }

        // 5. 촬영 정보 (선택적)
        if (photo.getDeviceModel() != null) {
            caption.append(photo.getDeviceModel()).append("으로 ");
        }

        caption.append("촬영한 사진입니다.");

        return caption.toString();
    }

    // 의미 있는 위치명 추출
    public String extractMeaningfulLocationName(Address address) {
        if (address == null) return "";

        // 장소명, 지역명, 주소 등에서 의미 있는 이름 추출
        if (address.getFeatureName() != null && !address.getFeatureName().equals("Unnamed Road")) {
            return address.getFeatureName();
        }

        StringBuilder locationBuilder = new StringBuilder();

        // 국가 > 행정구역 > 지역 순으로 구체적인 정보 선택
        if (address.getLocality() != null) {
            locationBuilder.append(address.getLocality());
        } else if (address.getSubLocality() != null) {
            locationBuilder.append(address.getSubLocality());
        } else if (address.getAdminArea() != null) {
            locationBuilder.append(address.getAdminArea());
        }

        return locationBuilder.toString();
    }

    // 활동 유형 추론
    public String inferActivityType(PhotoInfo photo, Address address) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(photo.getDateTaken());
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // 시간대 기반 활동 추론
        if (hour >= 7 && hour < 10) {
            return "아침 식사";
        } else if (hour >= 12 && hour < 14) {
            return "점심 식사";
        } else if (hour >= 18 && hour < 21) {
            return "저녁 식사";
        }

        // 장소 유형 기반 활동 추론
        if (address != null) {
            String featureName = address.getFeatureName();
            if (featureName != null) {
                if (featureName.contains("공원") || featureName.contains("Park")) {
                    return "공원 관광";
                } else if (featureName.contains("박물관") || featureName.contains("Museum")) {
                    return "박물관 관람";
                } else if (featureName.contains("카페") || featureName.contains("Cafe")) {
                    return "카페 방문";
                } else if (featureName.contains("센터") || featureName.contains("Center")) {
                    return "관광명소 방문";
                } else if (featureName.contains("호텔") || featureName.contains("Hotel")) {
                    return "숙소 체크";
                }
            }
        }

        return "여행";  // 기본값
    }
}