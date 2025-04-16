package com.example.wakey.ui.album.domestic;

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

public class DomesticRegionActivity extends AppCompatActivity {
    private static final String TAG = "DomesticRegionActivity";

    private TextView titleTextView;
    private HorizontalScrollView yearScrollView;
    private LinearLayout yearTabContainer;
    private RecyclerView regionsRecyclerView;
    private PhotoRepository photoRepository;

    private String regionName; // 시/도 이름
    private String regionLevel; // 계층(city, district)
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
        regionLevel = getIntent().getStringExtra("REGION_LEVEL");

        if (regionLevel == null) {
            regionLevel = "city"; // 기본값은 시/도 레벨
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
                // 국내 지역으로 필터링
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
        photoRepository.getPhotosForRegion(regionName, true).thenAccept(subRegionGroups -> {
            // 서브 지역별 사진 그룹에서 목록 생성
            List<DomesticRegionItem> regionItems = new ArrayList<>();

            for (Map.Entry<String, List<Photo>> entry : subRegionGroups.entrySet()) {
                String subRegionName = entry.getKey();
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

                DomesticRegionItem item = new DomesticRegionItem(
                        subRegionName,
                        subRegionName.toLowerCase().replaceAll("\\s+", "_"),
                        formattedDate,
                        thumbnailPath,
                        photos.size()
                );

                regionItems.add(item);
            }

            // 지역명으로 정렬
            Collections.sort(regionItems, Comparator.comparing(DomesticRegionItem::getName));

            runOnUiThread(() -> {
                DomesticRegionAdapter adapter = new DomesticRegionAdapter(regionItems, item -> {
                    Intent intent = new Intent(DomesticRegionActivity.this, AlbumDetailActivity.class);
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
            Log.e(TAG, "Error loading regions for " + regionName, e);
            return null;
        });
    }

    /**
     * 선택된 상위 지역에 속하는 사진인지 확인
     */
    private boolean checkIfPhotoInSelectedRegion(Photo photo) {
        if ("city".equals(regionLevel)) {
            // 시/도 레벨에서는 해당 시/도에 속한 사진만 필터링
            boolean isDomestic = isDomesticPhoto(photo);

            if (!isDomestic) {
                return false; // 국내 사진이 아니면 제외
            }

            // 1. 도/시 필드에서 직접 일치 확인
            if (photo.locationDo != null && containsRegion(photo.locationDo, regionName)) {
                return true;
            }

            // 2. Si 필드에서 시/도 확인
            if (photo.locationSi != null) {
                // 특별시/광역시는 Si 필드에도 있을 수 있음
                if (containsRegion(photo.locationSi, regionName)) {
                    return true;
                }

                // locationSi에 도 이름이 포함된 경우 (예: 경기도 수원시)
                if (regionName.endsWith("도") && photo.locationSi.contains(regionName)) {
                    return true;
                }

                // 약식 도 이름으로 확인 (경기도 -> 경기)
                String shortName = convertToShortProvinceName(regionName);
                if (shortName != null && photo.locationSi.contains(shortName)) {
                    return true;
                }
            }

            return false;
        } else {
            // 구/군 레벨에서는 해당 구/군에 속한 사진만 필터링
            if (photo.locationGu != null && containsRegion(photo.locationGu, regionName)) {
                return true;
            }

            // 구 정보가 없는 경우, 시에서 확인
            if (photo.locationSi != null && containsRegion(photo.locationSi, regionName)) {
                return true;
            }

            return false;
        }
    }

    /**
     * 지역명 포함 여부를 확인 (정확한 매칭을 위한 검사)
     */
    private boolean containsRegion(String source, String region) {
        // 정확히 일치하는 경우
        if (source.equals(region)) {
            return true;
        }

        // 한글 지역명 포맷이 다양할 수 있으므로 다양한 패턴 확인
        // 1. 약식명 확인 (서울특별시 -> 서울)
        String shortRegion = extractShortName(region);
        if (shortRegion != null && !shortRegion.equals(region)) {
            if (source.contains(shortRegion)) {
                return true;
            }
        }

        // 2. 표준명 확인 (서울 -> 서울특별시)
        String standardRegion = convertToStandardName(region);
        if (standardRegion != null && !standardRegion.equals(region)) {
            if (source.contains(standardRegion)) {
                return true;
            }
        }

        // 단순 부분 문자열 검사
        return source.contains(region);
    }

    /**
     * 긴 지역명에서 짧은 형태 추출 (서울특별시 -> 서울)
     */
    private String extractShortName(String fullName) {
        if (fullName.contains("특별시")) {
            return fullName.replace("특별시", "").trim();
        } else if (fullName.contains("광역시")) {
            return fullName.replace("광역시", "").trim();
        } else if (fullName.contains("특별자치시")) {
            return fullName.replace("특별자치시", "").trim();
        } else if (fullName.contains("특별자치도")) {
            return fullName.replace("특별자치도", "").trim();
        } else if (fullName.endsWith("도")) {
            return fullName.substring(0, fullName.length() - 1).trim();
        } else if (fullName.endsWith("시")) {
            return fullName.substring(0, fullName.length() - 1).trim();
        } else if (fullName.endsWith("군")) {
            return fullName.substring(0, fullName.length() - 1).trim();
        } else if (fullName.endsWith("구")) {
            return fullName.substring(0, fullName.length() - 1).trim();
        }

        return fullName;
    }

    /**
     * 짧은 지역명을 표준 형식으로 변환 (서울 -> 서울특별시)
     */
    private String convertToStandardName(String shortName) {
        switch (shortName) {
            case "서울": return "서울특별시";
            case "부산": return "부산광역시";
            case "인천": return "인천광역시";
            case "대구": return "대구광역시";
            case "광주": return "광주광역시";
            case "대전": return "대전광역시";
            case "울산": return "울산광역시";
            case "세종": return "세종특별자치시";
            case "경기": return "경기도";
            case "강원": return "강원도";
            case "충북": return "충청북도";
            case "충남": return "충청남도";
            case "전북": return "전라북도";
            case "전남": return "전라남도";
            case "경북": return "경상북도";
            case "경남": return "경상남도";
            case "제주": return "제주특별자치도";
            default: return shortName;
        }
    }

    /**
     * 도 이름을 약식으로 변환 (경기도 -> 경기)
     */
    private String convertToShortProvinceName(String fullName) {
        if (fullName.equals("경기도")) return "경기";
        if (fullName.equals("강원도")) return "강원";
        if (fullName.equals("충청북도")) return "충북";
        if (fullName.equals("충청남도")) return "충남";
        if (fullName.equals("전라북도")) return "전북";
        if (fullName.equals("전라남도")) return "전남";
        if (fullName.equals("경상북도")) return "경북";
        if (fullName.equals("경상남도")) return "경남";
        if (fullName.equals("제주특별자치도")) return "제주";

        return null;
    }

    /**
     * 국내 사진인지 확인
     */
    private boolean isDomesticPhoto(Photo photo) {
        // 위치 정보가 없으면 건너뜀
        if (photo.locationDo == null && photo.locationSi == null && photo.locationGu == null) {
            return false;
        }

        // 명시적으로 해외 국가명이 포함된 경우 제외
        if (photo.locationDo != null) {
            String[] foreignCountries = {
                    "일본", "중국", "대만", "홍콩", "태국", "싱가포르", "말레이시아",
                    "미국", "캐나다", "영국", "프랑스", "독일", "이탈리아", "스페인"
                    // 더 많은 해외 국가를 추가할 수 있음
            };

            for (String country : foreignCountries) {
                if (photo.locationDo.contains(country)) {
                    return false;
                }
            }
        }

        // 국내 지역명이 포함된 경우 국내로 판단
        String[] koreanRegions = {
                "서울", "부산", "인천", "대구", "광주", "대전", "울산", "세종",
                "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
                "경기도", "강원도", "충청북도", "충청남도", "전라북도", "전라남도", "경상북도", "경상남도", "제주도",
                "특별시", "광역시", "특별자치시", "특별자치도"
        };

        // 지역 정보에서 한국 지역명 확인
        for (String region : koreanRegions) {
            if ((photo.locationDo != null && photo.locationDo.contains(region)) ||
                    (photo.locationSi != null && photo.locationSi.contains(region)) ||
                    (photo.locationGu != null && photo.locationGu.contains(region))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 현재 선택된 지역 아래의 하위 지역 키 결정
     */
    private String determineLocationKey(Photo photo) {
        if ("city".equals(regionLevel)) {
            // 시/도 레벨에서는 구/군 정보 사용
            if (photo.locationGu != null && !photo.locationGu.isEmpty()) {
                return photo.locationGu;
            } else if (photo.locationStreet != null && !photo.locationStreet.isEmpty()) {
                return photo.locationStreet;
            } else {
                return "기타 지역";
            }
        } else {
            // 이미 구/군 레벨이면 더 세부적인 정보 사용
            if (photo.locationStreet != null && !photo.locationStreet.isEmpty()) {
                return photo.locationStreet;
            } else {
                return "기타 지역";
            }
        }
    }

    /**
     * 문자열이 숫자로만 이루어져 있는지 확인
     */
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.matches("\\d+");
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

    /**
     * 리전 아이템 모델 클래스
     */
    public static class DomesticRegionItem {
        private String name;
        private String code;
        private String date;
        private String thumbnailUrl;
        private int photoCount;

        public DomesticRegionItem(String name, String code, String date, String thumbnailUrl, int photoCount) {
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