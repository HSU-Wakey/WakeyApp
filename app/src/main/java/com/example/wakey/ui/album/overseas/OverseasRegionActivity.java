package com.example.wakey.ui.album.overseas;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.repository.PhotoRepository;
import com.example.wakey.ui.album.common.AlbumDetailActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class OverseasRegionActivity extends AppCompatActivity {
    private static final String TAG = "OverseasRegionActivity";

    private TextView titleTextView;
    private HorizontalScrollView yearScrollView;
    private LinearLayout yearTabContainer;
    private RecyclerView regionsRecyclerView;
    private PhotoRepository photoRepository;

    private String regionName;         // 표시용 국가명 (한글)
    private String regionOriginalName; // 원본 국가명 (검색용)
    private String currentYearFilter = "전체"; // 현재 선택된 년도 필터

    private List<String> availableYears = new ArrayList<>();
    private Map<String, TextView> yearTabMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_region);

        photoRepository = new PhotoRepository(this);

        // Get data from intent
        regionName = getIntent().getStringExtra("REGION_NAME");
        regionOriginalName = getIntent().getStringExtra("REGION_ORIGINAL_NAME");

        if (regionOriginalName == null) {
            regionOriginalName = regionName; // 원본 이름이 없으면 표시용 이름 사용
        }

        initViews();
        setupListeners();
        loadAvailableYearsAndRegions();
    }

    private void initViews() {
        titleTextView = findViewById(R.id.regionTitle);
        titleTextView.setText(regionName);

        yearScrollView = findViewById(R.id.yearScrollView);
        yearTabContainer = yearScrollView.findViewById(R.id.yearTabContainer);

        regionsRecyclerView = findViewById(R.id.regionsRecyclerView);
        regionsRecyclerView.setLayoutManager(new GridLayoutManager(this, 1)); // 1 column for full-width cards

        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        // Listener for tabs will be dynamically added
    }

    private void loadAvailableYearsAndRegions() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<Photo> allPhotos = db.photoDao().getAllPhotos();

            // Collect unique years for the specific region
            Set<String> yearsSet = new HashSet<>();
            yearsSet.add("전체");

            for (Photo photo : allPhotos) {
                // 선택한 국가의 사진만 필터링
                boolean inSelectedRegion = checkIfPhotoInSelectedRegion(photo);

                // If photo is in the selected region and has a date
                if (inSelectedRegion && photo.dateTaken != null && photo.dateTaken.length() >= 4) {
                    yearsSet.add(photo.dateTaken.substring(0, 4));
                }
            }

            availableYears = new ArrayList<>(yearsSet);
            Collections.sort(availableYears, Collections.reverseOrder());

            runOnUiThread(() -> {
                createYearTabs();
                loadRegions();
            });
        });
    }

    private void createYearTabs() {
        yearTabContainer.removeAllViews();
        yearTabMap.clear();

        // 전체 탭 추가
        TextView allTab = new TextView(this);
        allTab.setText("전체");
        allTab.setTextSize(14);
        allTab.setPadding(40, 10, 40, 10);
        allTab.setBackground(getResources().getDrawable(R.drawable.selector_year_tab));
        allTab.setTextColor(Color.WHITE);
        allTab.setSelected(true);
        allTab.setOnClickListener(v -> {
            // 전체 탭 클릭 시 처리
            currentYearFilter = "전체";
            updateYearTabSelection("전체");
            loadRegions();
        });
        yearTabContainer.addView(allTab);
        yearTabMap.put("전체", allTab);

        // 동적으로 생성된 년도 탭 추가
        for (String year : availableYears) {
            if ("전체".equals(year)) continue; // "전체" 중복 방지

            TextView tab = new TextView(this);
            tab.setText(year);
            tab.setTextSize(14);
            tab.setPadding(40, 10, 40, 10);
            tab.setBackground(getResources().getDrawable(R.drawable.selector_year_tab));
            tab.setTextColor(Color.GRAY);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.rightMargin = 16;
            tab.setLayoutParams(params);

            tab.setOnClickListener(v -> {
                currentYearFilter = year; // Update current year filter
                updateYearTabSelection(year);
                loadRegions();
            });

            yearTabContainer.addView(tab);
            yearTabMap.put(year, tab);
        }
    }

    private void updateYearTabSelection(String selectedYear) {
        for (Map.Entry<String, TextView> entry : yearTabMap.entrySet()) {
            TextView tab = entry.getValue();
            String year = entry.getKey();

            if (year.equals(selectedYear)) {
                tab.setSelected(true);
                tab.setTextColor(Color.WHITE);
            } else {
                tab.setSelected(false);
                tab.setTextColor(Color.GRAY);
            }
        }
    }

    private void loadRegions() {
        photoRepository.getPhotosForRegion(regionName, false).thenAccept(cityGroups -> {
            // 도시별 사진 그룹에서 목록 생성
            List<OverseasRegionItem> regionItems = new ArrayList<>();

            for (Map.Entry<String, List<Photo>> entry : cityGroups.entrySet()) {
                String cityName = entry.getKey();
                List<Photo> photos = entry.getValue();

                // 첫 번째 사진을 썸네일로 사용
                String thumbnailPath = photos.isEmpty() ? null : photos.get(0).filePath;

                // 최신 사진의 날짜 추출 (있는 경우)
                String formattedDate = "";
                for (Photo photo : photos) {
                    if (photo.dateTaken != null && photo.dateTaken.length() >= 7) {
                        String year = photo.dateTaken.substring(0, 4);
                        String month = photo.dateTaken.substring(5, 7);
                        formattedDate = year + "년 " + Integer.parseInt(month) + "월";
                        break; // 첫 번째 유효한 날짜 사용
                    }
                }

                OverseasRegionItem item = new OverseasRegionItem(
                        cityName,
                        cityName.toLowerCase().replaceAll("\\s+", "_"),
                        formattedDate,
                        thumbnailPath,
                        photos.size()
                );

                regionItems.add(item);
            }

            // 도시명으로 정렬
            Collections.sort(regionItems, Comparator.comparing(OverseasRegionItem::getName));

            runOnUiThread(() -> {
                OverseasRegionAdapter adapter = new OverseasRegionAdapter(regionItems, item -> {
                    Intent intent = new Intent(OverseasRegionActivity.this, AlbumDetailActivity.class);
                    intent.putExtra("REGION_NAME", item.getName());
                    intent.putExtra("REGION_CODE", item.getCode());
                    intent.putExtra("PARENT_REGION", regionName);

                    // 년도 필터 전달
                    if (!"전체".equals(currentYearFilter)) {
                        intent.putExtra("YEAR_FILTER", currentYearFilter);
                    }

                    startActivity(intent);
                });

                regionsRecyclerView.setAdapter(adapter);
            });
        }).exceptionally(e -> {
            Log.e(TAG, "Error loading cities for " + regionName, e);
            return null;
        });
    }

    /**
     * 선택된 국가에 속하는 사진인지 확인
     */
    private boolean checkIfPhotoInSelectedRegion(Photo photo) {
        // 일본 선택 시
        if (regionName.equals("일본") || regionOriginalName.equals("Japan")) {
            return (photo.locationDo != null && (photo.locationDo.contains("Japan") || photo.locationDo.contains("일본"))) ||
                    (photo.locationSi != null && (
                            photo.locationSi.contains("Tokyo") ||
                                    photo.locationSi.contains("Osaka") ||
                                    photo.locationSi.contains("Kyoto") ||
                                    photo.locationSi.contains("Fukuoka") ||
                                    photo.locationSi.contains("도쿄") ||
                                    photo.locationSi.contains("오사카") ||
                                    photo.locationSi.contains("교토") ||
                                    photo.locationSi.contains("후쿠오카")
                    ));
        }

        // 태국 선택 시
        if (regionName.equals("태국") || regionOriginalName.equals("Thailand")) {
            return (photo.locationDo != null && (photo.locationDo.contains("Thailand") || photo.locationDo.contains("태국"))) ||
                    (photo.locationSi != null && (
                            photo.locationSi.contains("Bangkok") ||
                                    photo.locationSi.contains("Chiang Mai") ||
                                    photo.locationSi.contains("Phuket") ||
                                    photo.locationSi.contains("방콕") ||
                                    photo.locationSi.contains("치앙마이") ||
                                    photo.locationSi.contains("푸켓")
                    ));
        }

        // 미국 선택 시
        if (regionName.equals("미국") || regionOriginalName.equals("USA")) {
            return (photo.locationDo != null && (
                    photo.locationDo.contains("USA") ||
                            photo.locationDo.contains("United States") ||
                            photo.locationDo.contains("America") ||
                            photo.locationDo.contains("미국")
            )) ||
                    (photo.locationSi != null && (
                            photo.locationSi.contains("New York") ||
                                    photo.locationSi.contains("Los Angeles") ||
                                    photo.locationSi.contains("Chicago") ||
                                    photo.locationSi.contains("San Francisco") ||
                                    photo.locationSi.contains("뉴욕") ||
                                    photo.locationSi.contains("로스앤젤레스") ||
                                    photo.locationSi.contains("시카고") ||
                                    photo.locationSi.contains("샌프란시스코")
                    ));
        }

        // 스크린샷의 특정 국가/지역
        if (regionName.equals("반텐") || regionOriginalName.equals("Banten")) {
            return (photo.locationDo != null && photo.locationDo.contains("Banten")) ||
                    (photo.locationSi != null && photo.locationSi.contains("Banten"));
        }

        if (regionName.equals("창") || regionOriginalName.equals("Chang")) {
            return (photo.locationDo != null && photo.locationDo.contains("Chang")) ||
                    (photo.locationSi != null && photo.locationSi.contains("Chang"));
        }

        if (regionName.equals("치앙마이") || regionOriginalName.equals("Chiang")) {
            return (photo.locationDo != null && (photo.locationDo.contains("Chiang") || photo.locationDo.contains("치앙마이"))) ||
                    (photo.locationSi != null && (photo.locationSi.contains("Chiang") || photo.locationSi.contains("치앙마이")));
        }

        if (regionName.equals("데라") || regionOriginalName.equals("Daerah")) {
            return (photo.locationDo != null && photo.locationDo.contains("Daerah")) ||
                    (photo.locationSi != null && photo.locationSi.contains("Daerah"));
        }

        // 기본 검사 - locationDo에 국가명이 포함되어 있는지
        return (photo.locationDo != null && (
                photo.locationDo.contains(regionName) ||
                        photo.locationDo.contains(regionOriginalName)));
    }

    /**
     * 사진에서 도시명 추출
     */
    private String extractCityName(Photo photo) {
        // 1. locationSi 필드에서 추출 (도시명)
        if (photo.locationSi != null && !photo.locationSi.isEmpty()) {
            // Si 필드에서 도시명 추출 (일반적으로 가장 신뢰할 수 있는 도시 정보)

            // 일본 도시 표준화
            if (regionName.equals("일본")) {
                if (photo.locationSi.contains("Tokyo") || photo.locationSi.contains("도쿄")) return "Tokyo";
                if (photo.locationSi.contains("Osaka") || photo.locationSi.contains("오사카")) return "Osaka";
                if (photo.locationSi.contains("Kyoto") || photo.locationSi.contains("교토")) return "Kyoto";
                if (photo.locationSi.contains("Fukuoka") || photo.locationSi.contains("후쿠오카")) return "Fukuoka";
                if (photo.locationSi.contains("Sapporo") || photo.locationSi.contains("삿포로")) return "Sapporo";
                if (photo.locationSi.contains("Nara") || photo.locationSi.contains("나라")) return "Nara";
                // 더 많은 일본 도시 추가 가능
            }

            // 태국 도시 표준화
            if (regionName.equals("태국")) {
                if (photo.locationSi.contains("Bangkok") || photo.locationSi.contains("방콕")) return "Bangkok";
                if (photo.locationSi.contains("Chiang Mai") || photo.locationSi.contains("치앙마이")) return "Chiang Mai";
                if (photo.locationSi.contains("Phuket") || photo.locationSi.contains("푸켓")) return "Phuket";
                if (photo.locationSi.contains("Pattaya") || photo.locationSi.contains("파타야")) return "Pattaya";
                // 더 많은 태국 도시 추가 가능
            }

            // 미국 도시 표준화
            if (regionName.equals("미국")) {
                if (photo.locationSi.contains("New York") || photo.locationSi.contains("뉴욕")) return "New York";
                if (photo.locationSi.contains("Los Angeles") || photo.locationSi.contains("LA") ||
                        photo.locationSi.contains("로스앤젤레스")) return "Los Angeles";
                if (photo.locationSi.contains("Chicago") || photo.locationSi.contains("시카고")) return "Chicago";
                if (photo.locationSi.contains("San Francisco") || photo.locationSi.contains("샌프란시스코")) return "San Francisco";
                // 더 많은 미국 도시 추가 가능
            }

            return photo.locationSi;
        }

        // 2. locationGu 필드에서 추출 (구/동 정보가 있는 경우)
        if (photo.locationGu != null && !photo.locationGu.isEmpty()) {
            return photo.locationGu;
        }

        // 3. 기타 정보
        if (photo.locationStreet != null && !photo.locationStreet.isEmpty()) {
            return photo.locationStreet;
        }

        return "기타 지역";
    }

    /**
     * 도시명 한글화 (필요한 경우)
     */
    private String translateCityName(String englishName) {
        // 일본 도시
        if (englishName.equals("Tokyo")) return "도쿄";
        if (englishName.equals("Osaka")) return "오사카";
        if (englishName.equals("Kyoto")) return "교토";
        if (englishName.equals("Fukuoka")) return "후쿠오카";
        if (englishName.equals("Sapporo")) return "삿포로";
        if (englishName.equals("Nara")) return "나라";

        // 태국 도시
        if (englishName.equals("Bangkok")) return "방콕";
        if (englishName.equals("Chiang Mai")) return "치앙마이";
        if (englishName.equals("Phuket")) return "푸켓";
        if (englishName.equals("Pattaya")) return "파타야";

        // 미국 도시
        if (englishName.equals("New York")) return "뉴욕";
        if (englishName.equals("Los Angeles")) return "로스앤젤레스";
        if (englishName.equals("Chicago")) return "시카고";
        if (englishName.equals("San Francisco")) return "샌프란시스코";

        // 이미 한글이면 그대로 반환
        return englishName;
    }

    /**
     * 도시 정보 저장용 내부 클래스
     */
    private static class CityInfo {
        private String displayName;     // 표시용 도시명 (한글)
        private String originalName;    // 원본 도시명
        private String thumbnailPath;
        private int photoCount;
        private String lastPhotoDate;

        public CityInfo(String displayName, String originalName, String thumbnailPath) {
            this.displayName = displayName;
            this.originalName = originalName;
            this.thumbnailPath = thumbnailPath;
            this.photoCount = 0;
        }
    }

    /**
     * 리전 아이템 모델 클래스
     */
    public static class OverseasRegionItem {
        private String name;
        private String code;
        private String date;
        private String thumbnailUrl;
        private int photoCount;

        public OverseasRegionItem(String name, String code, String date, String thumbnailUrl, int photoCount) {
            this.name = name;
            this.code = code;
            this.date = date;
            this.thumbnailUrl = thumbnailUrl;
            this.photoCount = photoCount;
        }

        public String getName() { return name; }
        public String getCode() { return code; }
        public String getDate() { return date; }
        public String getThumbnailUrl() { return thumbnailUrl; }
        public int getPhotoCount() { return photoCount; }
    }
}