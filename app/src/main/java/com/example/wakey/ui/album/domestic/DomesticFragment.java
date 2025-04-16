package com.example.wakey.ui.album.domestic;

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

public class DomesticFragment extends Fragment {

    private RecyclerView locationsRecyclerView;
    private DomesticLocationAdapter locationAdapter;
    private List<DomesticLocationItem> locations = new ArrayList<>();
    private PhotoRepository photoRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_domestic, container, false);

        photoRepository = new PhotoRepository(requireContext());

        locationsRecyclerView = view.findViewById(R.id.locationsRecyclerView);
        locationsRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        locationAdapter = new DomesticLocationAdapter(locations, item -> {
            // Navigate to DomesticRegionActivity
            Intent intent = new Intent(getActivity(), DomesticRegionActivity.class);
            intent.putExtra("REGION_TYPE", "domestic");
            intent.putExtra("REGION_NAME", item.getName());
            intent.putExtra("REGION_LEVEL", "city"); // 시/도 단위를 전달
            startActivity(intent);
        });

        locationsRecyclerView.setAdapter(locationAdapter);

        loadDomesticLocations();

        return view;
    }

    private void loadDomesticLocations() {
        photoRepository.getDomesticPhotos().thenAccept(regionGroups -> {
            // 지역별 사진 그룹에서 목록 생성
            List<DomesticLocationItem> items = new ArrayList<>();

            for (Map.Entry<String, List<Photo>> entry : regionGroups.entrySet()) {
                String regionName = entry.getKey();
                List<Photo> photos = entry.getValue();

                // 첫 번째 사진을 썸네일로 사용
                String thumbnailPath = photos.isEmpty() ? null : photos.get(0).filePath;

                DomesticLocationItem item = new DomesticLocationItem(
                        regionName,
                        thumbnailPath,
                        photos.size()
                );

                items.add(item);
            }

            // 지역명으로 정렬
            Collections.sort(items, Comparator.comparing(DomesticLocationItem::getName));

            // UI 업데이트는 메인 스레드에서
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    locations.clear();
                    locations.addAll(items);
                    locationAdapter.notifyDataSetChanged();
                });
            }
        }).exceptionally(e -> {
            Log.e("DomesticFragment", "Error loading domestic locations", e);
            return null;
        });
    }

    /**
     * 확실하게 국내 위치인지 확인 (한국에서 찍은 사진인지)
     */
    private boolean isDomesticLocation(Photo photo) {
        // 1. 명시적으로 해외 국가명이 포함된 경우 제외
        if (photo.locationDo != null) {
            // 일본 관련 키워드
            if (photo.locationDo.contains("Japan") ||
                    photo.locationDo.contains("일본") ||
                    photo.locationDo.contains("훗카이도") ||
                    photo.locationDo.contains("홋카이도") ||
                    photo.locationDo.contains("도쿄") ||
                    photo.locationDo.contains("오사카") ||
                    photo.locationDo.contains("후쿠오카")) {
                return false;
            }

            // 기타 아시아 국가
            if (photo.locationDo.contains("China") || photo.locationDo.contains("중국") ||
                    photo.locationDo.contains("Taiwan") || photo.locationDo.contains("대만") ||
                    photo.locationDo.contains("Thailand") || photo.locationDo.contains("태국") ||
                    photo.locationDo.contains("Vietnam") || photo.locationDo.contains("베트남") ||
                    photo.locationDo.contains("Hong Kong") || photo.locationDo.contains("홍콩") ||
                    photo.locationDo.contains("Singapore") || photo.locationDo.contains("싱가포르") ||
                    photo.locationDo.contains("Malaysia") || photo.locationDo.contains("말레이시아") ||
                    photo.locationDo.contains("Indonesia") || photo.locationDo.contains("인도네시아")) {
                return false;
            }

            // 서양 국가
            if (photo.locationDo.contains("USA") || photo.locationDo.contains("미국") ||
                    photo.locationDo.contains("United States") ||
                    photo.locationDo.contains("UK") || photo.locationDo.contains("영국") ||
                    photo.locationDo.contains("France") || photo.locationDo.contains("프랑스") ||
                    photo.locationDo.contains("Germany") || photo.locationDo.contains("독일") ||
                    photo.locationDo.contains("Italy") || photo.locationDo.contains("이탈리아") ||
                    photo.locationDo.contains("Spain") || photo.locationDo.contains("스페인") ||
                    photo.locationDo.contains("Canada") || photo.locationDo.contains("캐나다") ||
                    photo.locationDo.contains("Australia") || photo.locationDo.contains("호주")) {
                return false;
            }
        }

        // 확실한 도시명으로 해외인지 확인
        if (photo.locationSi != null) {
            // 해외 주요 도시명
            if (photo.locationSi.contains("Tokyo") || photo.locationSi.contains("도쿄") ||
                    photo.locationSi.contains("Osaka") || photo.locationSi.contains("오사카") ||
                    photo.locationSi.contains("Bangkok") || photo.locationSi.contains("방콕") ||
                    photo.locationSi.contains("Shanghai") || photo.locationSi.contains("상하이") ||
                    photo.locationSi.contains("Beijing") || photo.locationSi.contains("베이징") ||
                    photo.locationSi.contains("New York") || photo.locationSi.contains("뉴욕") ||
                    photo.locationSi.contains("Los Angeles") || photo.locationSi.contains("로스앤젤레스") ||
                    photo.locationSi.contains("Paris") || photo.locationSi.contains("파리") ||
                    photo.locationSi.contains("London") || photo.locationSi.contains("런던")) {
                return false;
            }
        }

        // 2. 명시적으로 한국/대한민국 포함 여부 확인
        if ((photo.locationDo != null && (photo.locationDo.contains("한국") || photo.locationDo.contains("대한민국"))) ||
                (photo.locationSi != null && (photo.locationSi.contains("한국") || photo.locationSi.contains("대한민국")))) {
            return true;
        }

        // 3. 주요 한국 지역명 확인
        String[] koreanRegions = {
                "서울", "부산", "인천", "대구", "광주", "대전", "울산", "세종",
                "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
                "서울특별시", "부산광역시", "인천광역시", "대구광역시", "광주광역시", "대전광역시", "울산광역시",
                "세종특별자치시", "경기도", "강원도", "충청북도", "충청남도", "전라북도", "전라남도",
                "경상북도", "경상남도", "제주특별자치도"
        };

        for (String region : koreanRegions) {
            if ((photo.locationDo != null && photo.locationDo.contains(region)) ||
                    (photo.locationSi != null && photo.locationSi.contains(region)) ||
                    (photo.locationGu != null && photo.locationGu.contains(region))) {
                return true;
            }
        }

        // 4. 구/군 패턴이 있는지 확인 (한국 특유의 행정구역)
        if (photo.locationGu != null &&
                (photo.locationGu.endsWith("구") || photo.locationGu.endsWith("군") || photo.locationGu.endsWith("동"))) {
            return true;
        }

        return false; // 위 조건에 해당하지 않으면 국내가 아닌 것으로 처리
    }

    /**
     * 사진에서 시/도 단위 이름 추출
     */
    private String extractCityName(Photo photo) {
        // 표준 시/도 이름 매핑
        Map<String, String> standardNames = new HashMap<>();
        standardNames.put("서울", "서울특별시");
        standardNames.put("부산", "부산광역시");
        standardNames.put("인천", "인천광역시");
        standardNames.put("대구", "대구광역시");
        standardNames.put("광주", "광주광역시");
        standardNames.put("대전", "대전광역시");
        standardNames.put("울산", "울산광역시");
        standardNames.put("세종", "세종특별자치시");
        standardNames.put("경기", "경기도");
        standardNames.put("강원", "강원도");
        standardNames.put("충북", "충청북도");
        standardNames.put("충남", "충청남도");
        standardNames.put("전북", "전라북도");
        standardNames.put("전남", "전라남도");
        standardNames.put("경북", "경상북도");
        standardNames.put("경남", "경상남도");
        standardNames.put("제주", "제주특별자치도");

        // 1. locationDo에서 확인
        if (photo.locationDo != null && !photo.locationDo.isEmpty()) {
            // 표준 이름 매핑에서 확인
            for (Map.Entry<String, String> entry : standardNames.entrySet()) {
                if (photo.locationDo.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // 이미 표준형이면 그대로 반환
            for (String standardName : standardNames.values()) {
                if (photo.locationDo.contains(standardName)) {
                    return standardName;
                }
            }

            // 그 외에는 그대로 반환
            return photo.locationDo;
        }

        // 2. locationSi에서 시/도 추출 시도
        if (photo.locationSi != null && !photo.locationSi.isEmpty()) {
            // 표준 이름 확인
            for (Map.Entry<String, String> entry : standardNames.entrySet()) {
                if (photo.locationSi.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            // 표준 이름에 해당하지 않는 시 이름은 그대로 반환
            return photo.locationSi;
        }

        // 3. locationGu가 있는 경우, 추정해서 도 반환
        if (photo.locationGu != null && !photo.locationGu.isEmpty()) {
            // 주요 도시들의 구 패턴
            if (photo.locationGu.contains("강남") || photo.locationGu.contains("종로") ||
                    photo.locationGu.contains("마포") || photo.locationGu.contains("서초") ||
                    photo.locationGu.contains("용산") || photo.locationGu.contains("영등포")) {
                return "서울특별시";
            }

            if (photo.locationGu.contains("연수") || photo.locationGu.contains("계양") ||
                    photo.locationGu.contains("부평") || photo.locationGu.contains("남동")) {
                return "인천광역시";
            }

            if (photo.locationGu.contains("수영") || photo.locationGu.contains("해운대") ||
                    photo.locationGu.contains("동래") || photo.locationGu.contains("부산진")) {
                return "부산광역시";
            }

            // 그외 지역은 "기타 지역"으로 표시
            return "기타 지역";
        }

        return null; // 적절한 지역명을 찾지 못함
    }

    public static class DomesticLocationItem {
        private String name;
        private String thumbnailPath;
        private int photoCount;

        public DomesticLocationItem(String name, String thumbnailPath, int photoCount) {
            this.name = name;
            this.thumbnailPath = thumbnailPath;
            this.photoCount = photoCount;
        }

        public String getName() { return name; }
        public String getThumbnailPath() { return thumbnailPath; }
        public int getPhotoCount() { return photoCount; }
    }
}