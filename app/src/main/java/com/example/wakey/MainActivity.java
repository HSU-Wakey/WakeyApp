package com.example.wakey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.PlaceData;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.ImageRepository;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.manager.ApiManager;
import com.example.wakey.manager.DataManager;
import com.example.wakey.manager.MapManager;
import com.example.wakey.manager.UIManager;
import com.example.wakey.util.ToastManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.data.model.ImageMeta;

/**
 * 메인 액티비티 클래스
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";

    // 권한 관련 상수 (권관상)
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // 매니저 인스턴스
    private MapManager mapManager;
    private UIManager uiManager;
    private DataManager dataManager;
    private ApiManager apiManager;

    // UI 컴포넌트
    private TextView dateTextView;
    private ImageButton mapButton;
    private ImageButton searchButton;
    private ImageButton prevDateBtn;
    private ImageButton nextDateBtn;
    private TextView bottomSheetDateTextView;

    // 구글 맵 관련 변수
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;

    // 지도 설정
    private boolean clusteringEnabled = true;
    private boolean showPOIs = false;
    private ImageRepository imageRepository;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI 컴포넌트 초기화
        initUI();

        // 매니저 초기화
        initManagers();

        // 구글 맵 프래그먼트 설정
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 위치 서비스 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 클릭 리스너 설정
        setupClickListeners();

        // 권한 요청
        requestLocationPermission();

        imageRepository = new ImageRepository(this);
    }

    /**
     * UI 컴포넌트 초기화
     */
    private void initUI() {
        dateTextView = findViewById(R.id.dateTextView);
        mapButton = findViewById(R.id.mapButton);
        searchButton = findViewById(R.id.searchButton);
        prevDateBtn = findViewById(R.id.prevDateBtn);
        nextDateBtn = findViewById(R.id.nextDateBtn);
        bottomSheetDateTextView = findViewById(R.id.bottom_sheet_date);
    }

    /**
     * 매니저 초기화
     */
    private void initManagers() {
        // 각 매니저 인스턴스 가져오기
        mapManager = MapManager.getInstance(this);
        uiManager = UIManager.getInstance(this);
        dataManager = DataManager.getInstance(this);
        apiManager = ApiManager.getInstance(this);

        // 매니저 초기화
        mapManager.init(this, new MapManager.OnMarkerClickListener() {
            @Override
            public void onMarkerClick(PhotoInfo photoInfo) {
                // 타임라인에서 해당 항목 찾기
                uiManager.highlightTimelineItem(photoInfo.getFilePath());
                uiManager.setBottomSheetState(UIManager.BOTTOM_SHEET_EXPANDED);
            }

            @Override
            public void onClusterClick(LatLng position) {
                // 클러스터 주변 타임라인 항목 표시
                uiManager.setBottomSheetState(UIManager.BOTTOM_SHEET_HALF_EXPANDED);
            }

            @Override
            public void onPlaceMarkerClick(String placeId) {
                // 장소 세부정보 표시
                uiManager.showPlaceDetails(placeId);
            }
        });

        uiManager.init(this, getSupportFragmentManager(), dateTextView, bottomSheetDateTextView,
                // 날짜 변경 리스너
                formattedDate -> loadDataForDate(formattedDate),
                // 검색 수행 리스너
                query -> performSearch(query));

        dataManager.init(this);
        apiManager.init(this);

        // 바텀 시트 설정
        View bottomSheetView = findViewById(R.id.bottom_sheet);
        uiManager.setupBottomSheet(bottomSheetView, new UIManager.OnTimelineItemClickListener() {
            @Override
            public void onTimelineItemClick(TimelineItem item, int position) {
                if (item.getLatLng() != null) {
                    mapManager.moveCamera(item.getLatLng(), 15f);
                }

                if (item.getPhotoPath() != null) {
                    uiManager.showPhotoDetail(item);
                }
            }
        });
    }

    /**
     * 클릭 리스너 설정
     */
    private void setupClickListeners() {
        // 날짜 선택
        dateTextView.setOnClickListener(v -> uiManager.showDatePickerDialog());

        // 맵 버튼 - bottomSheet 상태 토글
        mapButton.setOnClickListener(v -> {
            // 새로운 토글 방식으로 바텀 시트 상태 변경
            uiManager.toggleBottomSheetState();

            // bottomSheet가 보이는 상태이면 데이터 로드
            int currentState = uiManager.getCurrentBottomSheetState();
            if (currentState != UIManager.BOTTOM_SHEET_HIDDEN) {
                loadDataForDate(uiManager.getFormattedDate());
            }
        });

        // 검색 버튼
        searchButton.setOnClickListener(v -> uiManager.showSearchDialog());

        // 이전 날짜 버튼
        prevDateBtn.setOnClickListener(v -> uiManager.goToPreviousDate());

        // 다음 날짜 버튼
        nextDateBtn.setOnClickListener(v -> uiManager.goToNextDate());
    }

    /**
     * 권한 요청
     */
    private void requestLocationPermission() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // Android 버전에 따라 다른 권한 추가
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        ActivityCompat.requestPermissions(this,
                permissions.toArray(new String[0]),
                LOCATION_PERMISSION_REQUEST_CODE);
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
                loadPhotoData();
            } else {
                ToastManager.getInstance().showToast("앱 사용에 필요한 권한이 필요합니다", Toast.LENGTH_LONG);
            }
        }
    }

    /**
     * 구글 맵 준비 완료 콜백
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mapManager.setGoogleMap(googleMap);

        // 권한 확인
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 현재 위치 버튼 활성화
            mMap.setMyLocationEnabled(true);

            // 현재 위치로 카메라 이동
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10));
                        }
                    });

            // 모든 사진 로드
            loadAllPhotos();
        }
    }

    /**
     * 사진 데이터 로드
     */
    private void loadPhotoData() {
        dataManager.loadPhotoData();
        loadDataForDate(uiManager.getFormattedDate());

        new Thread(() -> {
            List<PhotoInfo> allPhotos = dataManager.getAllPhotoInfo();
            for (PhotoInfo photo : allPhotos) {
                Uri uri = Uri.fromFile(new File(photo.getFilePath()));
                Bitmap bitmap = ImageUtils.loadBitmapFromUri(this, uri);

                if (bitmap != null) {
                    ImageMeta meta = imageRepository.classifyImage(uri, bitmap);
                    imageRepository.savePhotoToDB(uri, meta);
                }
            }
        }).start();
    }


    /**
     * 모든 사진 지도에 로드
     */
    private void loadAllPhotos() {
        dataManager.loadAllPhotosToMap(new DataManager.OnDataLoadedListener() {
            @Override
            public void onPhotosLoaded(List<PhotoInfo> photos, Map<LatLng, List<PhotoInfo>> clusters) {
                mapManager.clearMap();
                mapManager.addMarkersForClusters(clusters);
            }

            @Override
            public void onTimelineLoaded(List<TimelineItem> timelineItems) {
                // 특정 날짜에 대한 로드가 아니므로 타임라인 업데이트 안함
            }

            @Override
            public void onRouteGenerated(List<LatLng> route) {
                // 특정 날짜에 대한 로드가 아니므로 경로 그리기 안함
            }
        });
    }

    /**
     * 특정 날짜의 데이터 로드
     */
    private void loadDataForDate(String dateString) {
        dataManager.loadPhotosForDate(dateString, new DataManager.OnDataLoadedListener() {
            @Override
            public void onPhotosLoaded(List<PhotoInfo> photos, Map<LatLng, List<PhotoInfo>> clusters) {
                // 지도 업데이트
                mapManager.clearMap();
                mapManager.addMarkersForClusters(clusters);

                // 사진 정보 로드 후 처리 (각 사진에 대한 장소 정보 로드 등)
                processPhotoInfo(photos);
            }

            @Override
            public void onTimelineLoaded(List<TimelineItem> timelineItems) {
                // 타임라인 업데이트
                uiManager.updateTimelineData(timelineItems);
            }

            @Override
            public void onRouteGenerated(List<LatLng> route) {
                // 경로 그리기
                if (route != null && route.size() > 1) {
                    mapManager.drawRoute(route);

                    // 첫 번째 위치로 카메라 이동
                    if (!route.isEmpty()) {
                        mapManager.moveCamera(route.get(0), 12f);
                    }
                }
            }
        });
    }

    /**
     * 사진 정보 처리
     */
    private void processPhotoInfo(List<PhotoInfo> photos) {
        if (photos == null || photos.isEmpty()) return;

        // 누적 리스트 준비
        List<TimelineItem> accumulatedItems = new ArrayList<>();

        // 각 사진에 대해 주소 및 POI 정보 가져오기
        for (PhotoInfo photo : photos) {
            if (photo.getLatLng() != null) {
                // 이미 장소 ID가 있는 경우 추가 처리 안함
                if (photo.getPlaceId() != null) continue;

                apiManager.fetchAddressAndPOIs(photo, new ApiManager.OnAddressResolvedListener() {
                    @Override
                    public void onSuccess(Address address, PhotoInfo photoInfo, List<PlaceData> places) {
                        TimelineItem item = apiManager.createTimelineItem(photoInfo, address, places);

                        runOnUiThread(() -> {
                            if (item != null) {
                                accumulatedItems.add(item);
                                // 리스트 갱신
                                uiManager.updateTimelineData(new ArrayList<>(accumulatedItems));
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "주소 정보 가져오기 실패: " + e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * 검색 수행
     */
    private void performSearch(String query) {
        dataManager.performSearch(query, mMap, new DataManager.OnSearchResultListener() {
            @Override
            public void onDateSearchResult(Date date) {
                // 날짜 검색 결과 처리
                uiManager.setDate(date);
            }

            @Override
            public void onLocationSearchResult(LatLng location, String name) {
                // 위치 검색 결과 처리
                mapManager.addSearchResultMarker(location, name != null ? name : query);
            }

//            @Override
//            public void onSearchFailed(String query) {
//                uiManager.showToast("검색 결과가 없습니다: " + query);
//            }

            @Override
            public void onSearchFailed(String query) {
                // 날짜나 위치 검색이 아니고, 텍스트(clip) 검색이면 토스트 띄우지 않음
                if (query.matches(".*\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}.*")) {
                    uiManager.showToast("검색 결과가 없습니다: " + query); // 날짜 검색 실패
                } else if (query.matches(".*[가-힣a-zA-Z]+.*")) {
                    // 일반 텍스트 검색은 CLIP으로 처리 중일 수 있으므로 토스트 안 띄움
                    return;
                } else {
                    uiManager.showToast("검색 결과가 없습니다: " + query);
                }
            }
        });
    }
}