package com.example.wakey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
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
import com.example.wakey.manager.ApiManager;
import com.example.wakey.manager.DataManager;
import com.example.wakey.manager.MapManager;
import com.example.wakey.manager.UIManager;
import com.example.wakey.ui.album.SmartAlbumActivity;
import com.example.wakey.ui.timeline.StoryGenerator;
import com.example.wakey.ui.timeline.TimelineManager;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.ToastManager;
import com.example.wakey.data.model.ImageMeta;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    private MapManager mapManager;
    private UIManager uiManager;
    private DataManager dataManager;
    private ApiManager apiManager;
    private TextView dateTextView;
    private ImageButton mapButton, albumButton, searchButton, prevDateBtn, nextDateBtn;
    private TextView bottomSheetDateTextView;

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;

    private ImageRepository imageRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        initManagers();
        initStoryComponents();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupClickListeners();
        requestLocationPermission();

        imageRepository = new ImageRepository(this);
    }

    private void initUI() {
        dateTextView = findViewById(R.id.dateTextView);
        mapButton = findViewById(R.id.mapButton);
        albumButton = findViewById(R.id.albumButton);
        searchButton = findViewById(R.id.searchButton);
        prevDateBtn = findViewById(R.id.prevDateBtn);
        nextDateBtn = findViewById(R.id.nextDateBtn);
        bottomSheetDateTextView = findViewById(R.id.bottom_sheet_date);
    }

    private void initManagers() {
        mapManager = MapManager.getInstance(this);
        uiManager = UIManager.getInstance(this);
        dataManager = DataManager.getInstance(this);
        apiManager = ApiManager.getInstance(this);

        mapManager.init(this, new MapManager.OnMarkerClickListener() {
            @Override
            public void onMarkerClick(PhotoInfo photoInfo) {
                uiManager.highlightTimelineItem(photoInfo.getFilePath());
                uiManager.setBottomSheetState(UIManager.BOTTOM_SHEET_EXPANDED);
            }

            @Override
            public void onClusterClick(LatLng position) {
                uiManager.setBottomSheetState(UIManager.BOTTOM_SHEET_HALF_EXPANDED);
            }

            @Override
            public void onPlaceMarkerClick(String placeId) {
                uiManager.showPlaceDetails(placeId);
            }
        });

        uiManager.initWithSearchPerformer(this, getSupportFragmentManager(), dateTextView, bottomSheetDateTextView,
                formattedDate -> loadDataForDate(formattedDate),
                query -> performSearch(query));

        dataManager.init(this);
        apiManager.init(this);

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

        // 추가: StoryGenerator 초기화 및 설정
        StoryGenerator.getInstance(this);
    }

    private void setupClickListeners() {
        dateTextView.setOnClickListener(v -> uiManager.showDatePickerDialog());
        mapButton.setOnClickListener(v -> {
            uiManager.toggleBottomSheetState();
            int currentState = uiManager.getCurrentBottomSheetState();
            if (currentState != UIManager.BOTTOM_SHEET_HIDDEN) {
                loadDataForDate(uiManager.getFormattedDate());
            }
        });

        albumButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SmartAlbumActivity.class);
            startActivity(intent);
        });

        searchButton.setOnClickListener(v -> uiManager.showSearchDialog());
        prevDateBtn.setOnClickListener(v -> uiManager.goToPreviousDate());
        nextDateBtn.setOnClickListener(v -> uiManager.goToNextDate());
    }

    private void requestLocationPermission() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        ActivityCompat.requestPermissions(this,
                permissions.toArray(new String[0]),
                LOCATION_PERMISSION_REQUEST_CODE);
    }

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
                loadPhotoData();
            } else {
                ToastManager.getInstance().showToast("앱 사용에 필요한 권한이 필요합니다", Toast.LENGTH_LONG);
            }
        }
    }

    private void loadPhotoData() {
        new Thread(() -> {
            List<Uri> imageUris = ImageUtils.getAllImageUris(this);
            for (Uri uri : imageUris) {
                Bitmap bitmap = ImageUtils.loadBitmapFromUri(this, uri);
                if (bitmap != null) {
                    ImageMeta meta = imageRepository.classifyImage(uri, bitmap);
                    imageRepository.savePhotoToDB(uri, meta);
                }
            }
        }).start();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mapManager.setGoogleMap(googleMap);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10));
                        }
                    });
            loadAllPhotos();
        }
    }

    private void loadAllPhotos() {
        dataManager.loadAllPhotosToMap(new DataManager.OnDataLoadedListener() {
            @Override
            public void onPhotosLoaded(List<PhotoInfo> photos, Map<LatLng, List<PhotoInfo>> clusters) {
                mapManager.clearMap();
                mapManager.addMarkersForClusters(clusters);
            }

            @Override
            public void onTimelineLoaded(List<TimelineItem> timelineItems) {
                uiManager.updateTimelineData(timelineItems);
                // 더 이상 storyFragment를 사용하지 않고, UIManager에서 바로 처리합니다.
            }

            @Override
            public void onRouteGenerated(List<LatLng> route) {}
        });
    }

    private void loadDataForDate(String dateString) {
        dataManager.loadPhotosForDate(dateString, new DataManager.OnDataLoadedListener() {
            @Override
            public void onPhotosLoaded(List<PhotoInfo> photos, Map<LatLng, List<PhotoInfo>> clusters) {
                mapManager.clearMap();
                mapManager.addMarkersForClusters(clusters);
                processPhotoInfo(photos);
            }

            @Override
            public void onTimelineLoaded(List<TimelineItem> timelineItems) {
                List<TimelineItem> enhancedTimeline = new ArrayList<>();
                for (TimelineItem item : timelineItems) {
                    if (item.getDetectedObjects() != null && !item.getDetectedObjects().isEmpty()) {
                        String desc = "📌 " + String.join(", ", item.getDetectedObjects());
                    }
                    enhancedTimeline.add(item);
                }

                // UI 첫 업데이트
                uiManager.updateTimelineData(enhancedTimeline);

                // 스토리 생성 리스너 등록
                TimelineManager timelineManager = TimelineManager.getInstance(MainActivity.this);
                timelineManager.setOnStoryGeneratedListener(itemsWithStories -> {
                    runOnUiThread(() -> {
                        Log.d(TAG, "스토리 생성 완료: " + itemsWithStories.size() + "개 항목");
                        // 타임라인 데이터 업데이트 (스토리가 포함된)
                        uiManager.updateTimelineData(itemsWithStories);

                        // 자동 전환 제거 - 사용자가 명시적으로 스토리 탭을 클릭할 때만 전환됨
                        // uiManager.switchToStoryTab(); <- 이 줄 제거

                        // 대신 스토리 준비 완료 알림 표시 (선택 사항)
                        Toast.makeText(MainActivity.this, "스토리가 준비되었습니다!", Toast.LENGTH_SHORT).show();
                    });
                });

                // Gemini 스토리 생성 시작
                timelineManager.generateStoriesForTimelineOptimized(enhancedTimeline);
            }

            @Override
            public void onRouteGenerated(List<LatLng> route) {
                if (route != null && route.size() > 1) {
                    mapManager.drawRoute(route);
                    if (!route.isEmpty()) {
                        mapManager.moveCamera(route.get(0), 12f);
                    }
                }
            }
        });
    }

    private void processPhotoInfo(List<PhotoInfo> photos) {
        if (photos == null || photos.isEmpty()) return;

        List<TimelineItem> accumulatedItems = new ArrayList<>();

        for (PhotoInfo photo : photos) {
            if (photo.getLatLng() != null && photo.getPlaceId() == null) {
                apiManager.fetchAddressAndPOIs(photo, new ApiManager.OnAddressResolvedListener() {
                    @Override
                    public void onSuccess(Address address, PhotoInfo photoInfo, List<PlaceData> places) {
                        TimelineItem item = apiManager.createTimelineItem(photoInfo, address, places);
                        runOnUiThread(() -> {
                            if (item != null) {
                                accumulatedItems.add(item);
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

    private void performSearch(String query) {
        dataManager.performSearch(query, mMap, new DataManager.OnSearchResultListener() {
            @Override
            public void onDateSearchResult(Date date) {
                uiManager.setDate(date);
            }

            @Override
            public void onLocationSearchResult(LatLng location, String name) {
                mapManager.addSearchResultMarker(location, name != null ? name : query);
            }

            @Override
            public void onSearchFailed(String query) {
                if (query.matches(".*\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}.*")) {
                    uiManager.showToast("검색 결과가 없습니다: " + query);
                } else if (query.matches(".*[가-힣a-zA-Z]+.*")) {
                    return;
                } else {
                    uiManager.showToast("검색 결과가 없습니다: " + query);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 더 이상 StoryFragment를 사용하지 않으므로 이 코드는 필요 없습니다.
        // UIManager가 직접 스토리 관련 기능을 처리합니다.
    }

    // MainActivity.java에서 setupStoryFragment() 메서드 제거하고 대신:
    private void initStoryComponents() {
        // UIManager를 통해 스토리 컴포넌트 초기화
        uiManager = UIManager.getInstance(this);

        // StoryGenerator 초기화
        StoryGenerator.getInstance(this);

        // UIManager가 바텀 시트 설정 시 storyRecyclerView까지 함께 설정하도록 함
        View bottomSheetView = findViewById(R.id.bottom_sheet);
        if (bottomSheetView != null) {
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

            Log.d(TAG, "⭐⭐⭐ 바텀 시트 초기화 완료");
        } else {
            Log.e(TAG, "⭐⭐⭐ 바텀 시트 뷰를 찾을 수 없음");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // StoryGenerator 자원 해제
        StoryGenerator.getInstance(this).release();
    }
}