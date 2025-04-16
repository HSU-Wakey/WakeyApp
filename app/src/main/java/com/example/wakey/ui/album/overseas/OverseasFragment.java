package com.example.wakey.ui.album.overseas;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.repository.PhotoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class OverseasFragment extends Fragment {

    private static final String TAG = "OverseasFragment";
    private RecyclerView locationsRecyclerView;
    private OverseasLocationAdapter locationAdapter;
    private PhotoRepository photoRepository;
    private List<OverseasLocationItem> locations = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_overseas, container, false);

        photoRepository = new PhotoRepository(requireContext());
        locationsRecyclerView = view.findViewById(R.id.locationsRecyclerView);
        locationsRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        locationAdapter = new OverseasLocationAdapter(locations, item -> {
            // Navigate to OverseasRegionActivity
            Intent intent = new Intent(getActivity(), OverseasRegionActivity.class);
            intent.putExtra("REGION_TYPE", "world");
            intent.putExtra("REGION_NAME", item.getName());
            intent.putExtra("REGION_ORIGINAL_NAME", item.getOriginalName());
            intent.putExtra("REGION_LEVEL", "country"); // 국가 단위로 전달
            startActivity(intent);
        });

        locationsRecyclerView.setAdapter(locationAdapter);

        loadOverseasLocations();

        return view;
    }

    private void loadOverseasLocations() {
        photoRepository.getOverseasPhotos().thenAccept(countryGroups -> {
            // 국가별 사진 그룹에서 목록 생성
            List<OverseasLocationItem> items = new ArrayList<>();

            for (Map.Entry<String, List<Photo>> entry : countryGroups.entrySet()) {
                String countryName = entry.getKey();
                List<Photo> photos = entry.getValue();

                // 첫 번째 사진을 썸네일로 사용
                String thumbnailPath = photos.isEmpty() ? null : photos.get(0).filePath;

                OverseasLocationItem item = new OverseasLocationItem(
                        countryName,              // 표시용 한글 국가명
                        countryName,              // 원본 국가명 (여기서는 같음)
                        thumbnailPath,
                        photos.size()
                );

                items.add(item);
            }

            // 국가명으로 정렬
            Collections.sort(items, Comparator.comparing(OverseasLocationItem::getName));

            // UI 업데이트는 메인 스레드에서
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    locations.clear();
                    locations.addAll(items);
                    locationAdapter.notifyDataSetChanged();
                });
            }
        }).exceptionally(e -> {
            Log.e("OverseasFragment", "Error loading overseas locations", e);
            return null;
        });
    }

    /**
     * 국내 위치인지 확인 (해외 사진 필터링 시 사용)
     */
    private boolean isDomesticLocation(Photo photo) {
        // 위치 정보가 없으면 건너뜀
        if (photo.locationDo == null && photo.locationSi == null && photo.locationGu == null) {
            return false;
        }

        // 명시적으로 한국/대한민국 포함 여부 확인
        if ((photo.locationDo != null && (photo.locationDo.contains("한국") || photo.locationDo.contains("대한민국"))) ||
                (photo.locationSi != null && (photo.locationSi.contains("한국") || photo.locationSi.contains("대한민국")))) {
            return true;
        }

        // 주요 한국 지역명 확인
        String[] koreanRegions = {
                "서울", "부산", "인천", "대구", "광주", "대전", "울산", "세종",
                "경기도", "강원도", "충청북도", "충청남도", "전라북도", "전라남도", "경상북도", "경상남도", "제주도",
                "서울특별시", "부산광역시", "인천광역시", "대구광역시", "광주광역시", "대전광역시", "울산광역시",
                "세종특별자치시"
        };

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
     * 위치 데이터에서 국가 원본 이름 추출
     */
    private String extractCountryName(Photo photo) {
        // 1. locationDo 필드에서 확인 (주로 국가명 포함)
        if (photo.locationDo != null && !photo.locationDo.isEmpty()) {
            // 기본 국가 확인 패턴
            if (photo.locationDo.contains("Japan") || photo.locationDo.contains("일본")) {
                return "Japan";
            }
            if (photo.locationDo.contains("China") || photo.locationDo.contains("중국")) {
                return "China";
            }
            if (photo.locationDo.contains("USA") || photo.locationDo.contains("United States") ||
                    photo.locationDo.contains("미국")) {
                return "USA";
            }
            if (photo.locationDo.contains("Thailand") || photo.locationDo.contains("태국")) {
                return "Thailand";
            }
            if (photo.locationDo.contains("Vietnam") || photo.locationDo.contains("베트남")) {
                return "Vietnam";
            }
            if (photo.locationDo.contains("Taiwan") || photo.locationDo.contains("대만")) {
                return "Taiwan";
            }
            if (photo.locationDo.contains("Hong Kong") || photo.locationDo.contains("홍콩")) {
                return "Hong Kong";
            }
            if (photo.locationDo.contains("Singapore") || photo.locationDo.contains("싱가포르")) {
                return "Singapore";
            }
            if (photo.locationDo.contains("Malaysia") || photo.locationDo.contains("말레이시아")) {
                return "Malaysia";
            }
            if (photo.locationDo.contains("Indonesia") || photo.locationDo.contains("인도네시아")) {
                return "Indonesia";
            }

            // 주요 국가들 더 추가 가능

            // 도시명으로 국가 추측
            if (photo.locationDo.contains("Tokyo") || photo.locationDo.contains("Osaka") ||
                    photo.locationDo.contains("Kyoto") || photo.locationDo.contains("Fukuoka") ||
                    photo.locationDo.contains("도쿄") || photo.locationDo.contains("오사카") ||
                    photo.locationDo.contains("교토") || photo.locationDo.contains("후쿠오카")) {
                return "Japan";
            }

            if (photo.locationDo.contains("Bangkok") || photo.locationDo.contains("Chiang Mai") ||
                    photo.locationDo.contains("Phuket") || photo.locationDo.contains("Pattaya") ||
                    photo.locationDo.contains("방콕") || photo.locationDo.contains("치앙마이") ||
                    photo.locationDo.contains("푸켓") || photo.locationDo.contains("파타야")) {
                return "Thailand";
            }

            // 원본 그대로 반환
            return photo.locationDo;
        }

        // 2. locationSi 필드에서 도시명으로 국가 추론
        if (photo.locationSi != null && !photo.locationSi.isEmpty()) {
            // 주요 도시로 국가 추론
            if (photo.locationSi.contains("Tokyo") || photo.locationSi.contains("Osaka") ||
                    photo.locationSi.contains("Kyoto") || photo.locationSi.contains("Fukuoka") ||
                    photo.locationSi.contains("도쿄") || photo.locationSi.contains("오사카") ||
                    photo.locationSi.contains("교토") || photo.locationSi.contains("후쿠오카")) {
                return "Japan";
            }

            if (photo.locationSi.contains("Bangkok") || photo.locationSi.contains("Chiang Mai") ||
                    photo.locationSi.contains("Phuket") || photo.locationSi.contains("Pattaya") ||
                    photo.locationSi.contains("방콕") || photo.locationSi.contains("치앙마이") ||
                    photo.locationSi.contains("푸켓") || photo.locationSi.contains("파타야")) {
                return "Thailand";
            }

            if (photo.locationSi.contains("New York") || photo.locationSi.contains("Los Angeles") ||
                    photo.locationSi.contains("Chicago") || photo.locationSi.contains("San Francisco") ||
                    photo.locationSi.contains("뉴욕") || photo.locationSi.contains("로스앤젤레스") ||
                    photo.locationSi.contains("시카고") || photo.locationSi.contains("샌프란시스코")) {
                return "USA";
            }

            if (photo.locationSi.contains("Beijing") || photo.locationSi.contains("Shanghai") ||
                    photo.locationSi.contains("Guangzhou") || photo.locationSi.contains("Shenzhen") ||
                    photo.locationSi.contains("베이징") || photo.locationSi.contains("상하이") ||
                    photo.locationSi.contains("광저우") || photo.locationSi.contains("선전")) {
                return "China";
            }

            // 그 외의 경우 도시명을 국가명으로 간주
            return photo.locationSi;
        }

        return "기타 국가";
    }

    /**
     * 국가 영문명을 한글로 변환
     */
    private String translateCountryName(String englishName) {
        // 주요 아시아 국가
        if (englishName.equals("Japan") || englishName.contains("Japan")) return "일본";
        if (englishName.equals("China") || englishName.contains("China")) return "중국";
        if (englishName.equals("Taiwan") || englishName.contains("Taiwan")) return "대만";
        if (englishName.equals("Hong Kong") || englishName.contains("Hong Kong")) return "홍콩";
        if (englishName.equals("Thailand") || englishName.contains("Thailand")) return "태국";
        if (englishName.equals("Singapore") || englishName.contains("Singapore")) return "싱가포르";
        if (englishName.equals("Malaysia") || englishName.contains("Malaysia")) return "말레이시아";
        if (englishName.equals("Indonesia") || englishName.contains("Indonesia")) return "인도네시아";
        if (englishName.equals("Vietnam") || englishName.contains("Vietnam")) return "베트남";
        if (englishName.equals("Philippines") || englishName.contains("Philippines")) return "필리핀";

        // 주요 서양 국가
        if (englishName.equals("USA") || englishName.contains("United States") ||
                englishName.contains("America")) return "미국";
        if (englishName.equals("UK") || englishName.contains("United Kingdom") ||
                englishName.contains("England")) return "영국";
        if (englishName.equals("France") || englishName.contains("France")) return "프랑스";
        if (englishName.equals("Germany") || englishName.contains("Germany")) return "독일";
        if (englishName.equals("Italy") || englishName.contains("Italy")) return "이탈리아";
        if (englishName.equals("Spain") || englishName.contains("Spain")) return "스페인";
        if (englishName.equals("Canada") || englishName.contains("Canada")) return "캐나다";
        if (englishName.equals("Australia") || englishName.contains("Australia")) return "호주";

        // 스크린샷에서 볼 수 있는 특정 국가/지역
        if (englishName.equals("Banten") || englishName.contains("Banten")) return "반텐";
        if (englishName.equals("Chang") || englishName.contains("Chang")) return "창";
        if (englishName.equals("Chiang") || englishName.contains("Chiang")) return "치앙마이";
        if (englishName.equals("Daerah") || englishName.contains("Daerah")) return "데라";

        // 이미 한글이면 그대로 반환
        return englishName;
    }

    /**
     * 국가 정보 저장용 내부 클래스
     */
    private static class CountryInfo {
        String koreanName;      // 한글 국가명
        String originalName;    // 원본 국가명 (검색용)
        String thumbnailPath;   // 대표 이미지 경로
        int photoCount;         // 사진 개수

        public CountryInfo(String koreanName, String originalName, String thumbnailPath) {
            this.koreanName = koreanName;
            this.originalName = originalName;
            this.thumbnailPath = thumbnailPath;
            this.photoCount = 0;
        }
    }

    public static class OverseasLocationItem {
        private String name;            // 한글 국가명
        private String originalName;    // 원본 국가명
        private String thumbnailPath;
        private int photoCount;

        public OverseasLocationItem(String name, String originalName, String thumbnailPath, int photoCount) {
            this.name = name;
            this.originalName = originalName;
            this.thumbnailPath = thumbnailPath;
            this.photoCount = photoCount;
        }

        public String getName() { return name; }
        public String getOriginalName() { return originalName; }
        public String getThumbnailPath() { return thumbnailPath; }
        public int getPhotoCount() { return photoCount; }
    }
}