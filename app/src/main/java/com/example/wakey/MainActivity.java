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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.PlaceData;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.ImageRepository;
import com.example.wakey.manager.ApiManager;
import com.example.wakey.manager.DataManager;
import com.example.wakey.manager.MapManager;
import com.example.wakey.manager.UIManager;
import com.example.wakey.tflite.ImageClassifier;
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
import com.jakewharton.threetenabp.AndroidThreeTen;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final int MAX_PHOTOS_PER_BATCH = 10; // 한 번에 처리할 최대 사진 수

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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService backgroundExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 단일 스레드 실행기 생성 (병렬 처리 방지)
        backgroundExecutor = Executors.newSingleThreadExecutor();

        // ThreeTen 라이브러리 초기화
        AndroidThreeTen.init(this);

        initUI();
        initManagers();
        initStoryComponents();

        imageRepository = new ImageRepository(this);

        // 지도 초기화
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupClickListeners();

        // 권한 요청
        requestLocationPermission();

        // 앱 시작 후 5초 후에 해시태그 처리 시작 (지연 시작)
        mainHandler.postDelayed(this::initializeHashtagsDelayed, 5000);
    }

    @Override
    protected void onDestroy() {
        // 백그라운드 작업 정리
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
        }
        super.onDestroy();
    }

    // 지연 시작을 위한 메서드
    private void initializeHashtagsDelayed() {
        Log.d(TAG, "해시태그 처리 지연 시작");
        // 한 번에 모든 작업을 시작하지 않고 필수 작업만 먼저 수행
        processExistingPhotosWithoutHashtags(MAX_PHOTOS_PER_BATCH);
    }

    // 기존 해시태그 없는 사진만 우선 처리 (개수 제한)
    private void processExistingPhotosWithoutHashtags(int maxPhotos) {
        backgroundExecutor.execute(() -> {
            Log.d(TAG, "기존 해시태그 없는 사진 처리 시작");
            ImageClassifier classifier = null;

            try {
                AppDatabase db = AppDatabase.getInstance(this);
                // 해시태그 없는 사진만 조회 (최대 개수 제한)
                List<Photo> photosWithoutHashtags = db.photoDao().getPhotosWithoutHashtagsLimit(maxPhotos);
                Log.d(TAG, "해시태그 없는 사진 수: " + photosWithoutHashtags.size());

                if (photosWithoutHashtags.isEmpty()) {
                    Log.d(TAG, "처리할 사진이 없습니다. 신규 사진 스캔으로 넘어갑니다.");
                    // 이미 모든 사진에 해시태그가 있으면 신규 사진 스캔으로 넘어감
                    mainHandler.postDelayed(this::scanNewPhotos, 2000);
                    return;
                }

                // 분류기 초기화
                try {
                    classifier = new ImageClassifier(this);
                } catch (Exception e) {
                    Log.e(TAG, "이미지 분류기 초기화 실패", e);
                    return;
                }

                int successCount = 0;

                // 사진 한 장씩 순차 처리
                for (Photo photo : photosWithoutHashtags) {
                    try {
                        Uri uri = Uri.parse(photo.filePath);
                        Bitmap bitmap = ImageUtils.loadBitmapFromUri(this, uri);

                        if (bitmap != null) {
                            List<Pair<String, Float>> predictions = classifier.classifyImage(bitmap);

                            StringBuilder hashtagBuilder = new StringBuilder();
                            for (Pair<String, Float> pred : predictions) {
                                if (pred != null && pred.first != null) {
                                    String term = pred.first.split(",")[0].trim();
                                    if (!term.isEmpty()) {
                                        String hashtag = "#" + term.replace(" ", "");
                                        hashtagBuilder.append(hashtag).append(" ");
                                    }
                                }
                            }

                            String finalHashtags = hashtagBuilder.toString().trim();
                            if (!finalHashtags.isEmpty()) {
                                db.photoDao().updateHashtags(photo.filePath, finalHashtags);
                                Log.d(TAG, "해시태그 생성 완료: " + photo.filePath);
                                successCount++;
                            }

                            // 메모리 누수 방지
                            bitmap.recycle();
                        }

                        // 처리 간 지연 (부하 감소)
                        Thread.sleep(100);
                    } catch (Exception e) {
                        Log.e(TAG, "사진 처리 중 오류: " + photo.filePath, e);
                    }
                }

                Log.d(TAG, "기존 사진 " + successCount + "개 처리 완료");

                // 남은 사진이 있는지 확인
                int remainingCount = db.photoDao().countPhotosWithoutHashtags();

                if (remainingCount > 0) {
                    // 아직 처리할 사진이 남아있으면 5초 후 다음 배치 처리
                    mainHandler.postDelayed(() ->
                            processExistingPhotosWithoutHashtags(MAX_PHOTOS_PER_BATCH), 5000);
                } else {
                    // 모든 기존 사진이 처리되었으면 신규 사진 스캔으로 넘어감
                    mainHandler.postDelayed(this::scanNewPhotos, 2000);
                }

            } catch (Exception e) {
                Log.e(TAG, "해시태그 초기화 중 오류", e);
            } finally {
                // 리소스 정리
                if (classifier != null) {
                    try {
                        classifier.close();
                    } catch (Exception e) {
                        Log.e(TAG, "분류기 닫기 실패", e);
                    }
                }
            }
        });
    }

    // 신규 사진 스캔 및 처리
    private void scanNewPhotos() {
        backgroundExecutor.execute(() -> {
            Log.d(TAG, "신규 사진 스캔 시작");
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<Uri> imageUris = ImageUtils.getAllImageUris(this);
                Log.d(TAG, "기기에서 발견된 총 이미지 수: " + imageUris.size());

                // 최대 처리 개수 제한
                int processedCount = 0;
                int maxToProcess = MAX_PHOTOS_PER_BATCH;

                for (Uri uri : imageUris) {
                    if (processedCount >= maxToProcess) {
                        break;
                    }

                    try {
                        // 이미 DB에 있는지 확인
                        Photo existingPhoto = db.photoDao().getPhotoByPath(uri.toString());

                        if (existingPhoto == null) {
                            // 신규 사진만 처리
                            Bitmap bitmap = ImageUtils.loadBitmapFromUri(this, uri);
                            if (bitmap != null) {
                                try {
                                    ImageMeta meta = imageRepository.classifyImage(uri, bitmap);
                                    Photo savedPhoto = imageRepository.savePhotoToDB(uri, meta);

                                    if (savedPhoto != null) {
                                        processedCount++;
                                        Log.d(TAG, "신규 사진 저장 완료: " + uri.toString());
                                    }

                                    // 메모리 정리
                                    bitmap.recycle();

                                    // 처리 간 지연
                                    Thread.sleep(100);
                                } catch (Exception e) {
                                    Log.e(TAG, "신규 사진 처리 중 오류: " + e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "URI 처리 중 오류: " + uri.toString(), e);
                    }
                }

                Log.d(TAG, "신규 사진 " + processedCount + "개 처리 완료");

                // 모든 작업 완료 메시지
                mainHandler.post(() -> {
                    Log.d(TAG, "이미지 처리 작업 모두 완료");
                    ToastManager.getInstance().showToast("이미지 준비 완료", Toast.LENGTH_SHORT);
                });

            } catch (Exception e) {
                Log.e(TAG, "신규 사진 스캔 중 오류", e);
            }
        });
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
                // 권한이 있을 경우 - 무거운 작업은 지연 실행하고 필수적인 UI 관련 작업만 즉시 실행
                if (mMap != null) {
                    loadAllPhotos();
                }
            } else {
                ToastManager.getInstance().showToast("앱 사용에 필요한 권한이 필요합니다", Toast.LENGTH_LONG);
            }
        }
    }

    // 개별 사진 해시태그 생성 - 안전하게 수정
    private void generateHashtagsForPhoto(Photo photo, Bitmap bitmap) {
        backgroundExecutor.execute(() -> {
            ImageClassifier classifier = null;
            try {
                classifier = new ImageClassifier(this);
                List<Pair<String, Float>> predictions = classifier.classifyImage(bitmap);

                StringBuilder hashtagBuilder = new StringBuilder();
                for (Pair<String, Float> pred : predictions) {
                    if (pred != null && pred.first != null) {
                        String term = pred.first.split(",")[0].trim();
                        if (!term.isEmpty()) {
                            String hashtag = "#" + term.replace(" ", "");
                            hashtagBuilder.append(hashtag).append(" ");
                        }
                    }
                }

                String finalHashtags = hashtagBuilder.toString().trim();
                if (!finalHashtags.isEmpty()) {
                    AppDatabase db = AppDatabase.getInstance(this);
                    db.photoDao().updateHashtags(photo.filePath, finalHashtags);
                    Log.d("HASHTAG_GENERATE", "해시태그 생성 완료: " + photo.filePath);
                }
            } catch (Exception e) {
                Log.e("HASHTAG_GENERATE", "해시태그 생성 실패: " + photo.filePath, e);
            } finally {
                if (classifier != null) {
                    classifier.close();
                }
            }
        });
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

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//
//        // StoryGenerator 자원 해제
//        StoryGenerator.getInstance(this).release();
//    }
}