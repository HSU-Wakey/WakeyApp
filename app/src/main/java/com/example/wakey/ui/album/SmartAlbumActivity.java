package com.example.wakey.ui.album;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.ui.album.diary.DiaryAdapter;
import com.example.wakey.ui.album.diary.DiaryDetailActivity;
import com.example.wakey.ui.album.diary.DiaryRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class SmartAlbumActivity extends AppCompatActivity {
    private static final String TAG = "SmartAlbumActivity";
    private static final int REQUEST_CREATE_DIARY = 1001;

    private TextView tabDomestic, tabWorld, tabRecord;
    private RecyclerView locationsRecyclerView;
    private LinearLayout emptyDiaryState;

    private LocationAdapter locationAdapter;
    private DiaryAdapter diaryAdapter;
    private DiaryRepository diaryRepository;

    private List<LocationItem> locations = new ArrayList<>();
    private List<DiaryItem> diaryItems = new ArrayList<>();
    private int currentTabIndex = 0; // 0: domestic, 1: world, 2: record

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_album);

        diaryRepository = DiaryRepository.getInstance(this);

        initViews();
        setupListeners();
        loadPhotosByLocation();
    }

    private void initViews() {
        tabDomestic = findViewById(R.id.tabDomestic);
        tabWorld = findViewById(R.id.tabWorld);
        tabRecord = findViewById(R.id.tabRecord);

        locationsRecyclerView = findViewById(R.id.locationsRecyclerView);
        emptyDiaryState = findViewById(R.id.emptyDiaryState);

        // Default to grid layout for domestic and world tabs
        locationsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        // Initialize both adapters but only set one active based on tab
        locationAdapter = new LocationAdapter(locations, item -> {
            // Navigate to AlbumRegionActivity for domestic and world tabs
            Intent intent = new Intent(SmartAlbumActivity.this, AlbumRegionActivity.class);
            intent.putExtra("REGION_TYPE", currentTabIndex == 0 ? "domestic" : "world");
            intent.putExtra("REGION_NAME", item.getName());
            startActivity(intent);
        });

        diaryAdapter = new DiaryAdapter(diaryItems, item -> {
            // Open diary detail activity in view mode
            Intent intent = new Intent(SmartAlbumActivity.this, DiaryDetailActivity.class);
            intent.putExtra("DIARY_TITLE", item.getTitle());
            intent.putExtra("DIARY_DATE_RANGE", item.getDateRange());
            intent.putExtra("DIARY_CONTENT", diaryRepository.getDiaryContent(item.getTitle()));
            intent.putExtra("DIARY_RATING", item.getHeartCount());
            intent.putExtra("DIARY_THUMBNAIL", item.getThumbnailPath());
            startActivity(intent);
        });

        // Set the initial adapter
        locationsRecyclerView.setAdapter(locationAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());

        tabDomestic.setOnClickListener(v -> {
            updateTabSelection(0);
            loadPhotosByLocation();
        });

        tabWorld.setOnClickListener(v -> {
            updateTabSelection(1);
            loadPhotosByLocation();
        });

        tabRecord.setOnClickListener(v -> {
            updateTabSelection(2);
            loadDiaryEntries();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CREATE_DIARY && resultCode == RESULT_OK && data != null) {
            // Create a new diary item from the returned data
            String title = data.getStringExtra("DIARY_TITLE");
            String dateRange = data.getStringExtra("DIARY_DATE_RANGE");
            String thumbnailPath = data.getStringExtra("DIARY_THUMBNAIL");
            String content = data.getStringExtra("DIARY_CONTENT");
            int heartCount = data.getIntExtra("DIARY_RATING", 0);

            // 사용자가 이미지를 선택하지 않았다면 기본 이미지 사용
            if (thumbnailPath == null || thumbnailPath.isEmpty()) {
                thumbnailPath = "android.resource://" + getPackageName() + "/" + R.drawable.placeholder_image;
            }

            DiaryItem newDiary = new DiaryItem(title, dateRange, thumbnailPath, heartCount);

            // DiaryRepository에 추가
            diaryRepository.addDiary(newDiary);

            // UI 업데이트
            loadDiaryEntries();

            // Hide empty state if it was visible
            if (diaryItems.size() > 0) {
                emptyDiaryState.setVisibility(View.GONE);
                locationsRecyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateTabSelection(int tabIndex) {
        currentTabIndex = tabIndex;

        // Reset all tabs
        tabDomestic.setBackgroundResource(R.drawable.tab_unselected_bg);
        tabDomestic.setTextColor(getResources().getColor(R.color.tab_unselected_text));

        tabWorld.setBackgroundResource(R.drawable.tab_unselected_bg);
        tabWorld.setTextColor(getResources().getColor(R.color.tab_unselected_text));

        tabRecord.setBackgroundResource(R.drawable.tab_unselected_bg);
        tabRecord.setTextColor(getResources().getColor(R.color.tab_unselected_text));

        // Set selected tab
        switch (tabIndex) {
            case 0:
                tabDomestic.setBackgroundResource(R.drawable.tab_selected_bg);
                tabDomestic.setTextColor(getResources().getColor(R.color.white));

                // Switch to grid layout for domestic view
                locationsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
                locationsRecyclerView.setAdapter(locationAdapter);
                break;

            case 1:
                tabWorld.setBackgroundResource(R.drawable.tab_selected_bg);
                tabWorld.setTextColor(getResources().getColor(R.color.white));

                // Switch to grid layout for world view
                locationsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
                locationsRecyclerView.setAdapter(locationAdapter);
                break;

            case 2:
                tabRecord.setBackgroundResource(R.drawable.tab_selected_bg);
                tabRecord.setTextColor(getResources().getColor(R.color.white));

                // Switch to linear layout for diary view
                locationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                locationsRecyclerView.setAdapter(diaryAdapter);
                break;
        }
    }

    private void loadPhotosByLocation() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<Photo> allPhotos = db.photoDao().getAllPhotos();

            Map<String, List<Photo>> locationGroups = new HashMap<>();

            for (Photo photo : allPhotos) {
                String locationKey = null;

                // Group based on current tab
                if (currentTabIndex == 0) { // Domestic - Group by city (Si)
                    if (photo.locationDo != null && !photo.locationDo.isEmpty()) {
                        if (isDomesticLocation(photo.locationDo)) {
                            locationKey = photo.locationSi != null && !photo.locationSi.isEmpty()
                                    ? photo.locationSi : photo.locationDo;
                        }
                    }
                } else if (currentTabIndex == 1) { // World - Group by country (Do)
                    if (photo.locationDo != null && !photo.locationDo.isEmpty()) {
                        if (!isDomesticLocation(photo.locationDo)) {
                            // Extract country name from locationDo
                            locationKey = extractCountryName(photo.locationDo);
                        }
                    }
                }

                if (locationKey != null) {
                    if (!locationGroups.containsKey(locationKey)) {
                        locationGroups.put(locationKey, new ArrayList<>());
                    }
                    locationGroups.get(locationKey).add(photo);
                }
            }

            // Convert to location items for adapter
            List<LocationItem> locationItems = new ArrayList<>();
            for (Map.Entry<String, List<Photo>> entry : locationGroups.entrySet()) {
                String locationName = entry.getKey();
                List<Photo> photos = entry.getValue();

                // Find the first photo with a valid thumbnail
                String thumbnailPath = null;
                if (!photos.isEmpty()) {
                    thumbnailPath = photos.get(0).filePath;
                }

                LocationItem item = new LocationItem(
                        locationName,
                        thumbnailPath,
                        photos.size()
                );

                locationItems.add(item);
            }

            // Sort location items by name
            Collections.sort(locationItems, Comparator.comparing(LocationItem::getName));

            runOnUiThread(() -> {
                locations.clear();
                locations.addAll(locationItems);
                locationAdapter.notifyDataSetChanged();
            });
        });
    }

    /**
     * Extract country name from location string
     * This is a simplified implementation. In a real app, you might need more sophisticated logic.
     */
    private String extractCountryName(String location) {
        // 기본 국가명 추출 (공백이 있으면 첫 단어)
        String countryName = location;
        if (location.contains(" ")) {
            countryName = location.split(" ")[0];
        }

        // 영어 국가명을 한국어로 매핑
        Map<String, String> countryTranslations = new HashMap<>();
        // 아시아
        countryTranslations.put("Japan", "일본");
        countryTranslations.put("China", "중국");
        countryTranslations.put("Korea", "대한민국");
        countryTranslations.put("South Korea", "대한민국");
        countryTranslations.put("Taiwan", "대만");
        countryTranslations.put("Hong Kong", "홍콩");
        countryTranslations.put("Thailand", "태국");
        countryTranslations.put("Singapore", "싱가포르");
        countryTranslations.put("Malaysia", "말레이시아");
        countryTranslations.put("Indonesia", "인도네시아");
        countryTranslations.put("Vietnam", "베트남");
        countryTranslations.put("Philippines", "필리핀");
        countryTranslations.put("Cambodia", "캄보디아");
        countryTranslations.put("Laos", "라오스");
        countryTranslations.put("Myanmar", "미얀마");
        countryTranslations.put("Mongolia", "몽골");
        countryTranslations.put("India", "인도");
        countryTranslations.put("Nepal", "네팔");
        countryTranslations.put("Bhutan", "부탄");
        countryTranslations.put("Sri Lanka", "스리랑카");
        countryTranslations.put("Bangladesh", "방글라데시");
        countryTranslations.put("Pakistan", "파키스탄");
        countryTranslations.put("Kazakhstan", "카자흐스탄");
        countryTranslations.put("Uzbekistan", "우즈베키스탄");
        countryTranslations.put("Kyrgyzstan", "키르기스스탄");
        countryTranslations.put("Tajikistan", "타지키스탄");
        countryTranslations.put("Turkmenistan", "투르크메니스탄");
        countryTranslations.put("Afghanistan", "아프가니스탄");
        countryTranslations.put("Iran", "이란");
        countryTranslations.put("Iraq", "이라크");
        countryTranslations.put("Turkey", "터키");
        countryTranslations.put("Syria", "시리아");
        countryTranslations.put("Lebanon", "레바논");
        countryTranslations.put("Israel", "이스라엘");
        countryTranslations.put("Jordan", "요르단");
        countryTranslations.put("Saudi Arabia", "사우디아라비아");
        countryTranslations.put("Qatar", "카타르");
        countryTranslations.put("UAE", "아랍에미리트");
        countryTranslations.put("United Arab Emirates", "아랍에미리트");
        countryTranslations.put("Oman", "오만");
        countryTranslations.put("Yemen", "예멘");
        countryTranslations.put("Bahrain", "바레인");
        countryTranslations.put("Kuwait", "쿠웨이트");

        // 유럽
        countryTranslations.put("UK", "영국");
        countryTranslations.put("United Kingdom", "영국");
        countryTranslations.put("England", "영국");
        countryTranslations.put("France", "프랑스");
        countryTranslations.put("Germany", "독일");
        countryTranslations.put("Italy", "이탈리아");
        countryTranslations.put("Spain", "스페인");
        countryTranslations.put("Portugal", "포르투갈");
        countryTranslations.put("Netherlands", "네덜란드");
        countryTranslations.put("Belgium", "벨기에");
        countryTranslations.put("Luxembourg", "룩셈부르크");
        countryTranslations.put("Switzerland", "스위스");
        countryTranslations.put("Austria", "오스트리아");
        countryTranslations.put("Sweden", "스웨덴");
        countryTranslations.put("Norway", "노르웨이");
        countryTranslations.put("Denmark", "덴마크");
        countryTranslations.put("Finland", "핀란드");
        countryTranslations.put("Iceland", "아이슬란드");
        countryTranslations.put("Ireland", "아일랜드");
        countryTranslations.put("Greece", "그리스");
        countryTranslations.put("Russia", "러시아");
        countryTranslations.put("Ukraine", "우크라이나");
        countryTranslations.put("Poland", "폴란드");
        countryTranslations.put("Czech Republic", "체코");
        countryTranslations.put("Czechia", "체코");
        countryTranslations.put("Slovakia", "슬로바키아");
        countryTranslations.put("Hungary", "헝가리");
        countryTranslations.put("Romania", "루마니아");
        countryTranslations.put("Bulgaria", "불가리아");
        countryTranslations.put("Croatia", "크로아티아");
        countryTranslations.put("Serbia", "세르비아");
        countryTranslations.put("Slovenia", "슬로베니아");
        countryTranslations.put("Bosnia and Herzegovina", "보스니아헤르체고비나");
        countryTranslations.put("Montenegro", "몬테네그로");
        countryTranslations.put("North Macedonia", "북마케도니아");
        countryTranslations.put("Albania", "알바니아");
        countryTranslations.put("Estonia", "에스토니아");
        countryTranslations.put("Latvia", "라트비아");
        countryTranslations.put("Lithuania", "리투아니아");
        countryTranslations.put("Belarus", "벨라루스");
        countryTranslations.put("Moldova", "몰도바");
        countryTranslations.put("Malta", "몰타");
        countryTranslations.put("Cyprus", "키프로스");

        // 북미
        countryTranslations.put("USA", "미국");
        countryTranslations.put("United States", "미국");
        countryTranslations.put("United States of America", "미국");
        countryTranslations.put("Canada", "캐나다");
        countryTranslations.put("Mexico", "멕시코");
        countryTranslations.put("Cuba", "쿠바");
        countryTranslations.put("Jamaica", "자메이카");
        countryTranslations.put("Haiti", "아이티");
        countryTranslations.put("Dominican Republic", "도미니카 공화국");
        countryTranslations.put("Puerto Rico", "푸에르토리코");
        countryTranslations.put("Bahamas", "바하마");
        countryTranslations.put("Panama", "파나마");
        countryTranslations.put("Costa Rica", "코스타리카");
        countryTranslations.put("Nicaragua", "니카라과");
        countryTranslations.put("Honduras", "온두라스");
        countryTranslations.put("El Salvador", "엘살바도르");
        countryTranslations.put("Guatemala", "과테말라");
        countryTranslations.put("Belize", "벨리즈");

        // 남미
        countryTranslations.put("Brazil", "브라질");
        countryTranslations.put("Argentina", "아르헨티나");
        countryTranslations.put("Chile", "칠레");
        countryTranslations.put("Colombia", "콜롬비아");
        countryTranslations.put("Peru", "페루");
        countryTranslations.put("Venezuela", "베네수엘라");
        countryTranslations.put("Ecuador", "에콰도르");
        countryTranslations.put("Bolivia", "볼리비아");
        countryTranslations.put("Paraguay", "파라과이");
        countryTranslations.put("Uruguay", "우루과이");
        countryTranslations.put("Guyana", "가이아나");
        countryTranslations.put("Suriname", "수리남");
        countryTranslations.put("French Guiana", "프랑스령 기아나");

        // 오세아니아
        countryTranslations.put("Australia", "호주");
        countryTranslations.put("New Zealand", "뉴질랜드");
        countryTranslations.put("Papua New Guinea", "파푸아뉴기니");
        countryTranslations.put("Fiji", "피지");
        countryTranslations.put("Solomon Islands", "솔로몬 제도");
        countryTranslations.put("Vanuatu", "바누아투");
        countryTranslations.put("Samoa", "사모아");
        countryTranslations.put("Tonga", "통가");

        // 아프리카
        countryTranslations.put("Egypt", "이집트");
        countryTranslations.put("South Africa", "남아프리카공화국");
        countryTranslations.put("Morocco", "모로코");
        countryTranslations.put("Tunisia", "튀니지");
        countryTranslations.put("Algeria", "알제리");
        countryTranslations.put("Libya", "리비아");
        countryTranslations.put("Sudan", "수단");
        countryTranslations.put("Ethiopia", "에티오피아");
        countryTranslations.put("Kenya", "케냐");
        countryTranslations.put("Tanzania", "탄자니아");
        countryTranslations.put("Uganda", "우간다");
        countryTranslations.put("Rwanda", "르완다");
        countryTranslations.put("Nigeria", "나이지리아");
        countryTranslations.put("Ghana", "가나");
        countryTranslations.put("Senegal", "세네갈");
        countryTranslations.put("Mali", "말리");
        countryTranslations.put("Congo", "콩고");
        countryTranslations.put("Madagascar", "마다가스카르");
        countryTranslations.put("Namibia", "나미비아");
        countryTranslations.put("Zimbabwe", "짐바브웨");
        countryTranslations.put("Botswana", "보츠와나");
        countryTranslations.put("Cameroon", "카메룬");

        // 이미 한국어면 그대로 반환
        for (String koreanName : countryTranslations.values()) {
            if (countryName.equals(koreanName)) {
                return countryName;
            }
        }

        // 영어 국가명이면 한국어로 변환
        return countryTranslations.getOrDefault(countryName, countryName);
    }

    private void loadDiaryEntries() {
        diaryItems.clear();
        diaryItems.addAll(diaryRepository.getDiaries());
        diaryAdapter.notifyDataSetChanged();

        // 빈 화면 상태 업데이트
        if (diaryItems.isEmpty() && currentTabIndex == 2) {
            emptyDiaryState.setVisibility(View.VISIBLE);
            locationsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyDiaryState.setVisibility(View.GONE);
            locationsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Helper method to determine if a location is domestic (Korean)
     */
    private boolean isDomesticLocation(String location) {
        // 1. 국가명이 명시적으로 포함된 경우 확인
        String[] foreignCountries = {
                // 아시아
                "일본", "중국", "대만", "홍콩", "태국", "싱가포르", "말레이시아", "인도네시아",
                "베트남", "필리핀", "캄보디아", "라오스", "미얀마", "몽골", "인도", "네팔",
                "부탄", "스리랑카", "방글라데시", "파키스탄", "카자흐스탄", "우즈베키스탄",
                "키르기스스탄", "타지키스탄", "투르크메니스탄", "아프가니스탄",

                // 중동
                "이란", "이라크", "터키", "시리아", "레바논", "이스라엘", "요르단",
                "사우디아라비아", "카타르", "아랍에미리트", "오만", "예멘", "바레인", "쿠웨이트",

                // 유럽
                "영국", "프랑스", "독일", "이탈리아", "스페인", "포르투갈", "네덜란드",
                "벨기에", "룩셈부르크", "스위스", "오스트리아", "스웨덴", "노르웨이",
                "덴마크", "핀란드", "아이슬란드", "아일랜드", "그리스", "러시아", "우크라이나",
                "폴란드", "체코", "슬로바키아", "헝가리", "루마니아", "불가리아", "크로아티아",
                "세르비아", "슬로베니아", "보스니아헤르체고비나", "몬테네그로", "북마케도니아",
                "알바니아", "에스토니아", "라트비아", "리투아니아", "벨라루스", "몰도바",
                "몰타", "키프로스",

                // 북미
                "미국", "캐나다", "멕시코", "쿠바", "자메이카", "아이티", "도미니카 공화국",
                "푸에르토리코", "바하마", "파나마", "코스타리카", "니카라과", "온두라스",
                "엘살바도르", "과테말라", "벨리즈",

                // 남미
                "브라질", "아르헨티나", "칠레", "콜롬비아", "페루", "베네수엘라", "에콰도르",
                "볼리비아", "파라과이", "우루과이", "가이아나", "수리남", "프랑스령 기아나",

                // 오세아니아
                "호주", "뉴질랜드", "파푸아뉴기니", "피지", "솔로몬 제도", "바누아투",
                "사모아", "통가",

                // 아프리카
                "이집트", "남아프리카공화국", "모로코", "튀니지", "알제리", "리비아", "수단",
                "에티오피아", "케냐", "탄자니아", "우간다", "르완다", "나이지리아", "가나",
                "세네갈", "말리", "콩고", "마다가스카르", "나미비아", "짐바브웨", "보츠와나", "카메룬"
        };

        // 외국 국가명이 있으면 국내가 아님
        for (String country : foreignCountries) {
            if (location.startsWith(country) || location.contains(" " + country + " ")) {
                return false;
            }
        }

        // 2. 명시적으로 한국/대한민국 포함 여부 확인
        if (location.contains("한국") || location.contains("대한민국")) {
            return true;
        }

        // 3. 주요 한국 지역명 확인 (시/도 단위만 확인하여 오류 줄임)
        String[] koreanRegions = {
                "서울특별시", "서울시", "서울", "부산광역시", "부산시", "부산",
                "인천광역시", "인천시", "인천", "대구광역시", "대구시", "대구",
                "광주광역시", "광주시", "광주", "대전광역시", "대전시", "대전",
                "울산광역시", "울산시", "울산", "세종특별자치시", "세종시", "세종",
                "경기도", "경기", "강원도", "강원", "충청북도", "충북", "충청남도", "충남",
                "전라북도", "전북", "전라남도", "전남", "경상북도", "경북", "경상남도", "경남",
                "제주특별자치도", "제주도", "제주"};

        // 시/도명이 단독으로 나타나는 경우(첫 단어이거나 띄어쓰기로 구분)만 국내로 판단
        for (String region : koreanRegions) {
            if (location.equals(region) || location.startsWith(region + " ")) {
                return true;
            }
        }

        // 기본값: 애매하면 국내가 아닌 것으로 처리
        return false;
    }

    /**
     * Location item model class
     */
    public static class LocationItem {
        private String name;
        private String thumbnailPath;
        private int photoCount;

        public LocationItem(String name, String thumbnailPath, int photoCount) {
            this.name = name;
            this.thumbnailPath = thumbnailPath;
            this.photoCount = photoCount;
        }

        public String getName() { return name; }
        public String getThumbnailPath() { return thumbnailPath; }
        public int getPhotoCount() { return photoCount; }
    }

    /**
     * Diary item model class
     */
    public static class DiaryItem {
        private String title;
        private String dateRange;
        private String thumbnailPath;
        private int heartCount;

        public DiaryItem(String title, String dateRange, String thumbnailPath, int heartCount) {
            this.title = title;
            this.dateRange = dateRange;
            this.thumbnailPath = thumbnailPath;
            this.heartCount = heartCount;
        }

        public String getTitle() { return title; }
        public String getDateRange() { return dateRange; }
        public String getThumbnailPath() { return thumbnailPath; }
        public int getHeartCount() { return heartCount; }
    }
}