package com.example.wakey.ui.album;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class AlbumRegionActivity extends AppCompatActivity {
    private static final String TAG = "AlbumRegionActivity";

    private TextView titleTextView;
    private HorizontalScrollView yearScrollView;
    private LinearLayout yearTabContainer;
    private RecyclerView regionsRecyclerView;

    private String regionType;
    private String regionName;
    private String currentYearFilter = "전체"; // 현재 선택된 년도 필터

    private List<String> availableYears = new ArrayList<>();
    private Map<String, TextView> yearTabMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_region);

        // Get data from intent
        regionType = getIntent().getStringExtra("REGION_TYPE");
        regionName = getIntent().getStringExtra("REGION_NAME");

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
                // Filter by region
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
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<Photo> allPhotos = db.photoDao().getAllPhotos();

            Map<String, SubRegionInfo> subRegionMap = new HashMap<>();
            Log.d(TAG, "Current year filter: " + currentYearFilter);

            for (Photo photo : allPhotos) {
                // 지역 필터링 로직 개선
                boolean inSelectedRegion = checkIfPhotoInSelectedRegion(photo);

                // 선택된 지역이 아니면 건너뜀
                if (!inSelectedRegion) {
                    continue;
                }

                // 연도 필터링 로직
                if (!"전체".equals(currentYearFilter)) {
                    if (photo.dateTaken == null || !photo.dateTaken.startsWith(currentYearFilter)) {
                        continue; // Skip photos that don't match the selected year
                    }
                }

                // 지역별 하위 구분 결정
                String locationKey = determineLocationKey(photo);

                if (!subRegionMap.containsKey(locationKey)) {
                    subRegionMap.put(locationKey, new SubRegionInfo(locationKey, photo.filePath));
                }

                SubRegionInfo info = subRegionMap.get(locationKey);
                info.photoCount++;

                if (photo.dateTaken != null &&
                        (info.lastPhotoDate == null ||
                                photo.dateTaken.compareTo(info.lastPhotoDate) > 0)) {
                    info.lastPhotoDate = photo.dateTaken;
                }
            }

            // 어댑터를 위한 RegionItem 리스트로 변환
            List<RegionAdapter.RegionItem> regionItems = new ArrayList<>();
            for (Map.Entry<String, SubRegionInfo> entry : subRegionMap.entrySet()) {
                SubRegionInfo info = entry.getValue();
                String formattedDate = "";

                if (info.lastPhotoDate != null && info.lastPhotoDate.length() >= 7) {
                    String year = info.lastPhotoDate.substring(0, 4);
                    String month = info.lastPhotoDate.substring(5, 7);
                    formattedDate = year + "년 " + Integer.parseInt(month) + "월";
                }

                regionItems.add(new RegionAdapter.RegionItem(
                        info.name,
                        info.name.toLowerCase().replaceAll("\\s+", "_"),
                        formattedDate,
                        info.thumbnailPath
                ));
            }

            Log.d(TAG, "Found " + regionItems.size() + " regions for filter: " + currentYearFilter);

            runOnUiThread(() -> {
                RegionAdapter adapter = new RegionAdapter(regionItems, item -> {
                    Intent intent = new Intent(AlbumRegionActivity.this, AlbumDetailActivity.class);
                    intent.putExtra("REGION_NAME", item.getName());
                    intent.putExtra("REGION_CODE", item.getCode());

                    // Pass the year filter to AlbumDetailActivity
                    if (!"전체".equals(currentYearFilter)) {
                        intent.putExtra("YEAR_FILTER", currentYearFilter);
                    }

                    startActivity(intent);
                });

                regionsRecyclerView.setAdapter(adapter);
            });
        });
    }

    private boolean checkIfPhotoInSelectedRegion(Photo photo) {
        // 국내/해외 구분에 따른 필터링 로직
        if ("domestic".equals(regionType)) {
            // 서울특별시 케이스 처리 (Do와 Si 모두 확인)
            return (regionName.equals(photo.locationDo) ||
                    regionName.equals(photo.locationSi) ||
                    regionName.equals(photo.locationGu));
        } else if ("world".equals(regionType)) {
            return regionName.equals(photo.locationDo);
        }
        return false;
    }

    private String determineLocationKey(Photo photo) {
        // 지역 키 결정 로직
        if ("domestic".equals(regionType)) {
            // 구 → 동 순서로 키 결정
            return photo.locationGu != null && !photo.locationGu.isEmpty()
                    ? photo.locationGu
                    : (photo.locationStreet != null ? photo.locationStreet : "기타");
        } else if ("world".equals(regionType)) {
            return photo.locationSi != null && !photo.locationSi.isEmpty()
                    ? photo.locationSi
                    : "기타";
        }
        return "기타";
    }

    /**
     * 지역 정보 저장용 내부 클래스
     */
    private static class SubRegionInfo {
        private String name;
        private String thumbnailPath;
        private int photoCount;
        private String lastPhotoDate;

        public SubRegionInfo(String name, String thumbnailPath) {
            this.name = name;
            this.thumbnailPath = thumbnailPath;
            this.photoCount = 0;
        }
    }
}