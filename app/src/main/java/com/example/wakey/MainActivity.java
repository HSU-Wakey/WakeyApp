package com.example.wakey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.util.PlaceHelper;
import com.example.wakey.ui.map.PhotoClusterItem;
import com.example.wakey.ui.timeline.TimelineAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.exifinterface.media.ExifInterface;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private GoogleMap mMap; // 구글 맵 객체
    private PlacesClient placesClient; // 장소 API 클라이언트
    private FusedLocationProviderClient fusedLocationClient; // 위치 정보 제공자

    private TextView dateTextView; // 날짜 표시 텍스트뷰
    private ImageButton mapButton; // 맵 옵션 버튼
    private ImageButton searchButton; // 검색 버튼
    private ImageButton prevDateBtn; // 이전 날짜 버튼
    private ImageButton nextDateBtn; // 다음 날짜 버튼

    private Calendar currentSelectedDate; // 현재 선택된 날짜
    private Map<String, List<PhotoInfo>> dateToPhotosMap; // 날짜별 사진 정보 맵
    private Map<String, List<LatLng>> dateToRouteMap; // 날짜별 경로 맵

    // 하단 시트 컴포넌트
    private BottomSheetBehavior<View> bottomSheetBehavior; // 하단 시트 동작 제어
    private RecyclerView timelineRecyclerView; // 타임라인 목록
    private TimelineAdapter timelineAdapter; // 타임라인 어댑터
    private List<TimelineItem> timelineItems = new ArrayList<>(); // 타임라인 항목 리스트
    private TextView bottomSheetDateTextView; // 하단 시트 날짜 표시
    private TabLayout tabLayout; // 탭 레이아웃

    // 클러스터링 컴포넌트
    private ClusterManager<PhotoClusterItem> clusterManager; // 마커 클러스터 관리자
    private boolean clusteringEnabled = true; // 클러스터링 활성화 여부

    // POI 컴포넌트
    private boolean showPOIs = false; // POI 표시 여부
    private static final int POI_SEARCH_RADIUS = 300; // POI 검색 반경(미터)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 컴포넌트 초기화
        dateTextView = findViewById(R.id.dateTextView);
        mapButton = findViewById(R.id.mapButton);
        searchButton = findViewById(R.id.searchButton);
        prevDateBtn = findViewById(R.id.prevDateBtn);
        nextDateBtn = findViewById(R.id.nextDateBtn);

        // 현재 날짜 초기화
        currentSelectedDate = Calendar.getInstance();
        updateDateDisplay();

        // 맵 데이터 초기화
        dateToPhotosMap = new HashMap<>();
        dateToRouteMap = new HashMap<>();

        // 구글 맵 프래그먼트 설정
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 구글 Places API 초기화
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_api_key));
        }
        placesClient = Places.createClient(this);

        // 위치 서비스 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 하단 시트 설정
        setupBottomSheet();

        // 클릭 리스너 설정
        setupClickListeners();

        // 권한 요청
        requestLocationPermission();
    }

    /**
     * 하단 시트(타임라인) 설정
     */
    private void setupBottomSheet() {
        // 하단 시트 초기화
        View bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // 타임라인 리사이클러뷰 설정
        timelineRecyclerView = findViewById(R.id.timelineRecyclerView);
        timelineRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        timelineAdapter = new TimelineAdapter(timelineItems);
        timelineRecyclerView.setAdapter(timelineAdapter);

        // 하단 시트의 날짜 텍스트뷰 설정
        bottomSheetDateTextView = findViewById(R.id.bottom_sheet_date);

        // 탭 레이아웃 설정
        tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // 탭 선택 처리 (향후 구현 예정)
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 필요시 구현
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 필요시 구현
            }
        });

        // 하단 시트 상태 변경 콜백
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // 상태 변경 처리 (필요시 구현)
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // 슬라이드 애니메이션 처리 (필요시 구현)
            }
        });

        // 타임라인 항목 클릭 리스너 설정
        timelineAdapter.setOnTimelineItemClickListener(new TimelineAdapter.OnTimelineItemClickListener() {
            @Override
            public void onTimelineItemClick(TimelineItem item, int position) {
                // 선택한 위치로 카메라 이동
                if (mMap != null && item.getLatLng() != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(item.getLatLng(), 15));
                }
            }
        });
    }

    /**
     * 클릭 리스너 설정
     */
    private void setupClickListeners() {
        // 날짜 텍스트뷰 클릭 - 캘린더 열기
        dateTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        // 맵 버튼 클릭 - 맵 옵션 열기
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 맵 옵션 대화상자 표시
                showMapOptionsDialog();
            }
        });

        // 검색 버튼 클릭 - 검색 인터페이스 열기
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 검색 대화상자 표시
                showSearchDialog();
            }
        });

        // 이전 날짜 버튼 클릭
        prevDateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedDate.add(Calendar.DAY_OF_MONTH, -1);
                updateDateDisplay();
                loadPhotosForDate(getFormattedDate());
            }
        });

        // 다음 날짜 버튼 클릭
        nextDateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedDate.add(Calendar.DAY_OF_MONTH, 1);
                updateDateDisplay();
                loadPhotosForDate(getFormattedDate());
            }
        });
    }

    /**
     * 맵 옵션 대화상자 표시
     */
    private void showMapOptionsDialog() {
        final String[] options = {
                "클러스터링 토글",
                "POI 표시 토글",
                "지도 유형 변경",
                "교통 정보 표시",
                "대중교통 표시",
                "자전거 경로 표시"
        };

        final boolean[] states = {
                clusteringEnabled,
                showPOIs,
                mMap.getMapType() != GoogleMap.MAP_TYPE_NORMAL,
                false,
                false,
                false
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("지도 옵션");

        builder.setMultiChoiceItems(options, states, (dialog, which, isChecked) -> {
            switch (which) {
                case 0: // 클러스터링
                    clusteringEnabled = isChecked;
                    break;
                case 1: // POI
                    showPOIs = isChecked;
                    break;
                case 2: // 지도 유형
                    // 별도 처리
                    break;
                case 3: // 교통 정보
                    mMap.setTrafficEnabled(isChecked);
                    break;
                case 4: // 대중교통
                    boolean isTransitShown = mMap.isIndoorEnabled();
                    mMap.setIndoorEnabled(!isTransitShown);
                    break;
                case 5: // 자전거
                    // Google Directions API로 구현 가능
                    break;
            }
        });

        builder.setPositiveButton("확인", (dialog, which) -> {
            // 새 설정으로 사진 다시 로드
            loadPhotosForDate(getFormattedDate());
        });

        builder.setNeutralButton("지도 유형", (dialog, which) -> {
            // 지도 유형 선택 대화상자 표시
            showMapTypeDialog();
        });

        builder.show();
    }

    /**
     * 지도 유형 선택 대화상자 표시
     */
    private void showMapTypeDialog() {
        final String[] mapTypes = {
                "일반",
                "위성",
                "지형",
                "하이브리드"
        };

        int currentType = 0;
        switch (mMap.getMapType()) {
            case GoogleMap.MAP_TYPE_NORMAL:
                currentType = 0;
                break;
            case GoogleMap.MAP_TYPE_SATELLITE:
                currentType = 1;
                break;
            case GoogleMap.MAP_TYPE_TERRAIN:
                currentType = 2;
                break;
            case GoogleMap.MAP_TYPE_HYBRID:
                currentType = 3;
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("지도 유형 선택");
        builder.setSingleChoiceItems(mapTypes, currentType, (dialog, which) -> {
            switch (which) {
                case 0:
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    break;
                case 1:
                    mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    break;
                case 2:
                    mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                    break;
                case 3:
                    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    break;
            }
            dialog.dismiss();
        });

        builder.show();
    }

    /**
     * 검색 대화상자 표시
     */
    private void showSearchDialog() {
        // 검색 대화상자 레이아웃 로드
        View searchView = getLayoutInflater().inflate(R.layout.dialog_search, null);

        // 검색 EditText 참조 가져오기
        final EditText searchEditText = searchView.findViewById(R.id.searchEditText);

        // 대화상자 생성
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("검색");
        builder.setView(searchView);

        builder.setPositiveButton("검색", (dialog, which) -> {
            // 검색어 가져오기
            String query = searchEditText.getText().toString().trim();

            if (!query.isEmpty()) {
                // 검색 수행
                performSearch(query);
            }
        });

        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    /**
     * 검색 수행
     * @param query 검색어
     */
    private void performSearch(String query) {
        // 날짜로 검색
        if (query.matches("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}")) {
            // 날짜 형식 정규화
            query = query.replaceAll("[-./]", "-");

            // 날짜 파싱 시도
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = inputFormat.parse(query);

                if (date != null) {
                    // 검색한 날짜로 현재 날짜 설정
                    currentSelectedDate.setTime(date);
                    updateDateDisplay();
                    loadPhotosForDate(getFormattedDate());
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 위치로 검색 (Geocoder 사용)
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(query, 1);

            if (!addresses.isEmpty()) {
                // 첫 번째 결과 가져오기
                Address address = addresses.get(0);
                LatLng position = new LatLng(address.getLatitude(), address.getLongitude());

                // 위치로 카메라 이동
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 14));

                // 위치에 마커 추가
                mMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(query));

                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 날짜나 위치 검색 실패
        Toast.makeText(this, "검색 결과 없음: " + query, Toast.LENGTH_SHORT).show();
    }

    /**
     * 날짜 선택 대화상자 표시
     */
    private void showDatePickerDialog() {
        // 캘린더 빌더 생성
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();

        // 커스텀 테마 적용
        builder.setTheme(R.style.CustomMaterialCalendarTheme);

        // 제목 설정 및 현재 날짜로 초기화
        builder.setTitleText("Wakey Wakey");
        builder.setSelection(currentSelectedDate.getTimeInMillis());

        // DatePicker 생성
        MaterialDatePicker<Long> materialDatePicker = builder.build();

        // 날짜 선택 리스너 설정
        materialDatePicker.addOnPositiveButtonClickListener(selection -> {
            // 선택한 날짜로 설정
            currentSelectedDate.setTimeInMillis(selection);
            updateDateDisplay();
            loadPhotosForDate(getFormattedDate());
        });

        // 대화상자 표시
        materialDatePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    /**
     * 날짜 표시 업데이트
     */
    private void updateDateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
        String formattedDate = dateFormat.format(currentSelectedDate.getTime());
        dateTextView.setText(formattedDate);

        // 하단 시트 날짜도 업데이트
        if (bottomSheetDateTextView != null) {
            bottomSheetDateTextView.setText(formattedDate);
        }
    }

    /**
     * 형식화된 날짜 문자열 가져오기 (yyyy-MM-dd)
     */
    private String getFormattedDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(currentSelectedDate.getTime());
    }

    /**
     * 위치 권한 요청
     */
    private void requestLocationPermission() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_MEDIA_IMAGES
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // 모든 권한이 이미 허용된 경우 사진 스캔
            scanPhotosWithGeoData();
        }
    }

    /**
     * 권한 요청 결과 처리
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // 권한 허용, 사진 스캔
                scanPhotosWithGeoData();
            } else {
                Toast.makeText(this, "앱 사용에 필요한 권한이 필요합니다", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 구글 맵 준비 완료 콜백
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // 권한 확인
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 현재 위치 버튼 활성화
            mMap.setMyLocationEnabled(true);

            // 클러스터 매니저 설정
            setupClusterManager();

            // 현재 위치로 카메라 이동
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10));
                            }
                        }
                    });

            // 현재 날짜의 사진 로드
            loadPhotosForDate(getFormattedDate());
        }
    }

    /**
     * 사진 마커 클러스터 매니저 설정
     */
    private void setupClusterManager() {
        // 클러스터 매니저 초기화
        clusterManager = new ClusterManager<>(this, mMap);

        // 마커 클릭 리스너로 클러스터 매니저 설정
        mMap.setOnMarkerClickListener(clusterManager);

        // 카메라 이동 완료 리스너로 클러스터 매니저 설정
        mMap.setOnCameraIdleListener(clusterManager);

        // 클릭 리스너 설정
        clusterManager.setOnClusterClickListener(new ClusterManager.OnClusterClickListener<PhotoClusterItem>() {
            @Override
            public boolean onClusterClick(Cluster<PhotoClusterItem> cluster) {
                // 클러스터 클릭 시 줌인하여 항목 보기
                LatLng clusterPosition = cluster.getPosition();
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        clusterPosition, mMap.getCameraPosition().zoom + 2));
                return true;
            }
        });

        clusterManager.setOnClusterItemClickListener(new ClusterManager.OnClusterItemClickListener<PhotoClusterItem>() {
            @Override
            public boolean onClusterItemClick(PhotoClusterItem item) {
                // 클러스터 항목 클릭 시 하단 시트 표시
                PhotoInfo photoInfo = (PhotoInfo) item.getTag();
                if (photoInfo != null) {
                    // 하단 시트 확장
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                    // 해당 타임라인 항목 강조
                    highlightTimelineItem(photoInfo);
                }
                return false;
            }
        });
    }

    /**
     * 사진에 해당하는 타임라인 항목 강조
     * @param photoInfo 사진 정보
     */
    private void highlightTimelineItem(PhotoInfo photoInfo) {
        // 일치하는 타임라인 항목 찾기
        for (int i = 0; i < timelineItems.size(); i++) {
            TimelineItem item = timelineItems.get(i);
            if (item.getPhotoPath() != null && item.getPhotoPath().equals(photoInfo.getFilePath())) {
                // 해당 위치로 스크롤
                timelineRecyclerView.smoothScrollToPosition(i);
                break;
            }
        }
    }

    /**
     * 위치 정보가 있는 사진 스캔
     */
    private void scanPhotosWithGeoData() {
        // 기존 데이터 초기화
        dateToPhotosMap.clear();
        dateToRouteMap.clear();

        // MediaStore에서 검색할 열 정의
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATA
        };

        // 모든 이미지에 대해 MediaStore 쿼리
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC")) {

            if (cursor != null) {
                int dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int dateTakenColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);

                while (cursor.moveToNext()) {
                    String filePath = cursor.getString(dataColumnIndex);
                    long dateTakenMillis = cursor.getLong(dateTakenColumnIndex);

                    // EXIF 데이터에서 GPS 정보 추출 시도
                    try {
                        ExifInterface exifInterface = new ExifInterface(filePath);
                        float[] latLong = new float[2];
                        boolean hasLatLong = exifInterface.getLatLong(latLong);

                        if (hasLatLong) {
                            // YYYY-MM-DD 형식의 날짜 문자열 생성
                            Date dateTaken = new Date(dateTakenMillis);
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            String dateString = dateFormat.format(dateTaken);

                            // PhotoInfo 객체 생성
                            PhotoInfo photoInfo = new PhotoInfo(
                                    filePath,
                                    dateTaken,
                                    new LatLng(latLong[0], latLong[1])
                            );

                            // 맵에 추가
                            if (!dateToPhotosMap.containsKey(dateString)) {
                                dateToPhotosMap.put(dateString, new ArrayList<>());
                                dateToRouteMap.put(dateString, new ArrayList<>());
                            }

                            dateToPhotosMap.get(dateString).add(photoInfo);
                            dateToRouteMap.get(dateString).add(photoInfo.getLatLng());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // 현재 날짜의 사진 로드
        loadPhotosForDate(getFormattedDate());
    }

    /**
     * 날짜에 해당하는 사진 로드
     * @param dateString 날짜 문자열 (yyyy-MM-dd)
     */
    private void loadPhotosForDate(String dateString) {
        if (mMap == null) return;

        // 현재 마커 및 클러스터 초기화
        mMap.clear();
        if (clusterManager != null) {
            clusterManager.clearItems();
        }

        // 타임라인 항목 초기화
        timelineItems.clear();

        // 해당 날짜에 사진이 있으면 마커 추가
        if (dateToPhotosMap.containsKey(dateString)) {
            List<PhotoInfo> photos = dateToPhotosMap.get(dateString);
            List<LatLng> route = dateToRouteMap.get(dateString);

            if (clusteringEnabled && clusterManager != null) {
                // 사진을 클러스터 항목으로 추가
                for (PhotoInfo photo : photos) {
                    // 시간 형식화
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    String timeString = timeFormat.format(photo.getDateTaken());

                    // 클러스터 항목 생성
                    PhotoClusterItem item = new PhotoClusterItem(
                            photo.getLatLng(),
                            timeString,
                            "Photo",
                            photo
                    );

                    // 클러스터 매니저에 추가
                    clusterManager.addItem(item);

                    // 타임라인용 장소 정보 가져오기
                    getPlaceInfoForPhoto(photo);
                }

                // 클러스터링 업데이트 강제 실행
                clusterManager.cluster();
            } else {
                // 클러스터링이 비활성화된 경우 일반 마커 추가
                for (PhotoInfo photo : photos) {
                    // 맵에 마커 추가
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(photo.getLatLng())
                            .title("Photo");

                    Marker marker = mMap.addMarker(markerOptions);
                    if (marker != null) {
                        marker.setTag(photo);
                    }

                    // 타임라인용 장소 정보 가져오기
                    getPlaceInfoForPhoto(photo);
                }
            }

            // 여러 지점이 있는 경우 향상된 경로 그리기
            if (route != null && route.size() > 1) {
                drawEnhancedRoute(route);

                // 전체 경로를 보여주기 위해 카메라 이동
                if (!route.isEmpty()) {
                    LatLng firstPoint = route.get(0);
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 12));
                }
            }

            // 활성화된 경우 사진 위치 근처의 POI 검색
            if (showPOIs && !photos.isEmpty()) {
                // 첫 번째 사진을 검색 중심으로 사용
                LatLng searchCenter = photos.get(0).getLatLng();
                searchNearbyPlaces(searchCenter);
            }
        } else {
            Toast.makeText(this, "이 날짜에 사진이 없습니다", Toast.LENGTH_SHORT).show();
        }

        // 타임라인 어댑터 업데이트
        if (timelineAdapter != null) {
            timelineAdapter.updateItems(timelineItems);
        }
    }

    /**
     * 향상된 스타일링으로 경로 그리기
     * @param points 경로의 LatLng 지점 목록
     */
    private void drawEnhancedRoute(List<LatLng> points) {
        if (points.size() < 2) return;

        // 주요 경로선
        PolylineOptions routeOptions = new PolylineOptions()
                .addAll(points)
                .width(8)  // 이전보다 넓게
                .color(getResources().getColor(R.color.route_color))
                .geodesic(true);  // 지구 곡률 따라 그리기

        // 맵에 경로 추가
        mMap.addPolyline(routeOptions);

        // 입체감을 위한 경로 그림자
        PolylineOptions shadowOptions = new PolylineOptions()
                .addAll(points)
                .width(12)
                .color(Color.argb(50, 0, 0, 0))  // 반투명 검정
                .geodesic(true)
                .zIndex(-1);  // 주요 경로 아래에 그리기

        mMap.addPolyline(shadowOptions);

        // 시작과 끝 마커 추가
        if (!points.isEmpty()) {
            // 시작 마커
            MarkerOptions startMarker = new MarkerOptions()
                    .position(points.get(0))
                    .title("시작")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            mMap.addMarker(startMarker);

            // 끝 마커
            MarkerOptions endMarker = new MarkerOptions()
                    .position(points.get(points.size() - 1))
                    .title("끝")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            mMap.addMarker(endMarker);
        }
    }

    /**
     * Places API를 사용하여 주변 장소 검색
     * @param location 검색할 위치
     */
    private void searchNearbyPlaces(LatLng location) {
        if (placesClient == null) return;

        PlaceHelper.getNearbyPlaces(placesClient, location, POI_SEARCH_RADIUS,
                new PlaceHelper.OnPlaceFoundListener() {
                    @Override
                    public void onPlaceFound(String name, String address, LatLng latLng, List<Place.Type> types) {
                        // 장소에 마커 추가
                        if (mMap != null && latLng != null && !types.isEmpty()) {
                            // 장소 유형에 맞는 아이콘 가져오기
                            BitmapDescriptor icon = PlaceHelper.getPlaceTypeIcon(MainActivity.this, types.get(0));

                            // 맵에 마커 추가
                            MarkerOptions markerOptions = new MarkerOptions()
                                    .position(latLng)
                                    .title(name)
                                    .snippet(address)
                                    .icon(icon);

                            mMap.addMarker(markerOptions);

                            // 장소에 대한 타임라인 항목 생성
                            // 사용자가 방문한 시간을 모르므로 현재 시간 사용
                            TimelineItem item = new TimelineItem(
                                    new Date(),
                                    name,
                                    null,  // 사진 없음
                                    latLng,
                                    PlaceHelper.getPlaceTypeCaption(types.get(0))
                            );

                            // 타임라인에 추가하고 시간순 정렬
                            timelineItems.add(item);
                            Collections.sort(timelineItems, (o1, o2) -> o1.getTime().compareTo(o2.getTime()));

                            // 어댑터 변경 알림
                            if (timelineAdapter != null) {
                                timelineAdapter.notifyDataSetChanged();
                            }
                        }
                    }

                    @Override
                    public void onPlaceSearchComplete() {
                        // 모든 장소 찾음
                        if (timelineAdapter != null) {
                            timelineAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onPlaceSearchError(Exception e) {
                        // 검색 오류 처리
                        Toast.makeText(MainActivity.this,
                                "장소 검색 오류: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 사진에 대한 장소 정보 가져오기
     * @param photo 사진 정보
     */
    private void getPlaceInfoForPhoto(final PhotoInfo photo) {
        // Geocoder로 사진 위치의 장소명 가져오기
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try {
            LatLng location = photo.getLatLng();
            List<Address> addresses = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String locationName = "";

                // 의미 있는 이름 가져오기 (특징명, 지역명 또는 주소줄)
                if (address.getFeatureName() != null && !address.getFeatureName().equals("Unnamed Road")) {
                    locationName = address.getFeatureName();
                } else if (address.getLocality() != null) {
                    locationName = address.getLocality();
                } else if (address.getAddressLine(0) != null) {
                    locationName = address.getAddressLine(0);
                }

                // 위치 기반 설명 생성
                String description = "";
                if (address.getLocality() != null) {
                    description = address.getLocality() + "에서 찍은 사진";
                } else if (address.getSubLocality() != null) {
                    description = address.getSubLocality() + "에서 찍은 사진";
                } else {
                    description = "이 장소에서 찍은 사진";
                }

                // 타임라인 항목 생성
                TimelineItem item = new TimelineItem(
                        photo.getDateTaken(),
                        locationName,
                        photo.getFilePath(),
                        photo.getLatLng(),
                        description
                );

                // 타임라인 항목에 추가하고 시간순 정렬
                timelineItems.add(item);
                Collections.sort(timelineItems, (o1, o2) -> o1.getTime().compareTo(o2.getTime()));

                // 어댑터 변경 알림
                if (timelineAdapter != null) {
                    timelineAdapter.notifyDataSetChanged();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 사진 메타데이터 저장 클래스
     */
    private static class PhotoInfo {
        private String filePath; // 파일 경로
        private Date dateTaken;  // 촬영 일시
        private LatLng latLng;   // 위치 좌표

        public PhotoInfo(String filePath, Date dateTaken, LatLng latLng) {
            this.filePath = filePath;
            this.dateTaken = dateTaken;
            this.latLng = latLng;
        }

        public String getFilePath() {
            return filePath;
        }

        public Date getDateTaken() {
            return dateTaken;
        }

        public LatLng getLatLng() {
            return latLng;
        }
    }
}