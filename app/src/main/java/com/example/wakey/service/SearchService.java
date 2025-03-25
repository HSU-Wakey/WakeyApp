// service/SearchService.java
package com.example.wakey.service;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.SearchHistoryItem;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.PhotoRepository;
import com.example.wakey.data.repository.SearchHistoryRepository;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.data.util.ImageCaptureUtil;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class SearchService {
    private static SearchService instance;

    private final Context context;
    private final PhotoRepository photoRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    private SearchService(Context context) {
        this.context = context;
        this.photoRepository = PhotoRepository.getInstance(context);
        this.searchHistoryRepository = SearchHistoryRepository.getInstance(context);
    }

    public static SearchService getInstance(Context context) {
        if (instance == null) {
            instance = new SearchService(context.getApplicationContext());
        }
        return instance;
    }

    // 검색 수행
    public SearchResult search(String query, GoogleMap map) {
        // 검색 결과 초기화
        SearchResult result = new SearchResult();

        // 날짜 검색 패턴
        Pattern datePattern = Pattern.compile("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}");

        if (datePattern.matcher(query).matches()) {
            // 날짜 검색
            String normalizedDate = query.replaceAll("[-./]", "-");

            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date searchDate = format.parse(normalizedDate);

                if (searchDate != null) {
                    result.setDate(searchDate);
                    result.setSuccess(true);

                    // 검색 기록에 추가
                    addToSearchHistory(query, map);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } else {
            // 위치 검색
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(query, 1);

                if (!addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    LatLng location = new LatLng(address.getLatitude(), address.getLongitude());

                    result.setLocation(location);
                    result.setLocationName(address.getFeatureName());
                    result.setSuccess(true);

                    // 검색 기록에 추가
                    addToSearchHistory(query, map);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    // 검색 기록에 추가
    private void addToSearchHistory(String query, GoogleMap map) {
        // 맵 이미지 캡처
        String imagePath = ImageCaptureUtil.captureMapView(context, map, map.getCameraPosition().target);

        // 검색 기록 항목 생성
        SearchHistoryItem item = new SearchHistoryItem(
                query,
                imagePath,
                System.currentTimeMillis()
        );

        // 저장소에 추가
        searchHistoryRepository.addSearchHistory(item);
    }

    // 인기 검색어 목록 가져오기 (실제 앱에서는 더 복잡한 로직 필요)
    public List<String> getPopularSearchTerms() {
        List<String> terms = new ArrayList<>();
        terms.add("광주광역시 학술대회");
        terms.add("피자");
        terms.add("2025년 여행");
        return terms;
    }

    // 검색 결과 클래스
    public static class SearchResult {
        private boolean success = false;
        private Date date;
        private LatLng location;
        private String locationName;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        public LatLng getLocation() {
            return location;
        }

        public void setLocation(LatLng location) {
            this.location = location;
        }

        public String getLocationName() {
            return locationName;
        }

        public void setLocationName(String locationName) {
            this.locationName = locationName;
        }

        public boolean isDateSearch() {
            return date != null;
        }

        public boolean isLocationSearch() {
            return location != null;
        }
    }
}