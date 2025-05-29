package com.example.wakey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.app.ProgressDialog;
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
import com.example.wakey.ui.timeline.StoryFragment;
import com.example.wakey.ui.timeline.StoryGenerator;
import com.example.wakey.ui.timeline.TimelineManager;
import com.example.wakey.util.ImageUtils;
import com.example.wakey.util.ToastManager;
import com.example.wakey.util.ThumbnailCache;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final int MAX_PHOTOS_PER_BATCH = 50; // 배치 크기 증가
    private static final int THREAD_POOL_SIZE = 3; // 병렬 처리를 위한 스레드 풀 크기

    // 처리 진행 상태 추적
    private volatile boolean isProcessingPhotos = false;
    private int totalPhotosToProcess = 0;
    private int photosProcessed = 0;
    private ProgressDialog progressDialog;

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

        // 스레드 풀 실행기로 변경 (병렬 처리)
        backgroundExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // ThreeTen 라이브러리 초기화
        AndroidThreeTen.init(this);

        // 썸네일 캐시 초기화 - 중요!
        ThumbnailCache.getInstance(this);

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

        // 앱 시작 후 3초 후에 해시태그 처리 시작 (더 빠르게)
        mainHandler.postDelayed(this::initializeHashtagsDelayed, 3000);
    }

    @Override
    protected void onDestroy() {
        // 진행 중인 다이얼로그 정리
        if (progressDialog != null) {
            progressDialog.dismiss();
        }

        // 백그라운드 작업 정리
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow(); // 즉시 종료
        }

        // 썸네일 캐시 정리 - 중요!
        ThumbnailCache thumbnailCache = ThumbnailCache.getInstance(this);
        if (thumbnailCache != null) {
            thumbnailCache.shutdown();
        }

        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        // 메모리 부족 시 캐시 클리어
        ThumbnailCache thumbnailCache = ThumbnailCache.getInstance(this);
        if (thumbnailCache != null) {
            thumbnailCache.clearCache();
            Log.d(TAG, "메모리 부족으로 썸네일 캐시 클리어");
        }
    }

    private void initializeHashtagsDelayed() {
        Log.d(TAG, "해시태그 처리 지연 시작");
        // 전체 사진 개수 확인
        checkTotalPhotosToProcess();
        // 해시태그 없는 사진 처리 시작
        processExistingPhotosWithoutHashtags(MAX_PHOTOS_PER_BATCH);
    }

    private void checkTotalPhotosToProcess() {
        backgroundExecutor.execute(() -> {
            try {
                List<Uri> allUris = ImageUtils.getAllImageUris(this);
                AppDatabase db = AppDatabase.getInstance(this);
                int existingCount = db.photoDao().getPhotoCount();
                totalPhotosToProcess = allUris.size() - existingCount;

                Log.d(TAG, "총 이미지: " + allUris.size() +
                        ", DB에 저장된 이미지: " + existingCount +
                        ", 처리할 신규 이미지: " + totalPhotosToProcess);

                // UI에 진행 상황 표시
                if (totalPhotosToProcess > 0) {
                    showProcessingNotification();
                }
            } catch (Exception e) {
                Log.e(TAG, "사진 개수 확인 실패", e);
            }
        });
    }

    private void processExistingPhotosWithoutHashtags(int maxPhotos) {
        if (isProcessingPhotos) {
            Log.d(TAG, "이미 처리 중입니다.");
            return;
        }

        isProcessingPhotos = true;

        backgroundExecutor.execute(() -> {
            Log.d(TAG, "기존 해시태그 없는 사진 처리 시작");
            ImageClassifier classifier = null;

            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<Photo> photosWithoutHashtags = db.photoDao().getPhotosWithoutHashtagsLimit(maxPhotos);
                Log.d(TAG, "해시태그 없는 사진 수: " + photosWithoutHashtags.size());

                if (photosWithoutHashtags.isEmpty()) {
                    Log.d(TAG, "처리할 사진이 없습니다. 신규 사진 스캔으로 넘어갑니다.");
                    isProcessingPhotos = false;
                    // 즉시 신규 사진 스캔 시작
                    scanNewPhotosInBatches();
                    return;
                }

                classifier = new ImageClassifier(this);
                int successCount = 0;

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
                                successCount++;
                            }

                            bitmap.recycle();
                        }

                        // 처리 간 지연 감소
                        Thread.sleep(50);
                    } catch (Exception e) {
                        Log.e(TAG, "사진 처리 중 오류: " + photo.filePath, e);
                    }
                }

                Log.d(TAG, "기존 사진 " + successCount + "개 처리 완료");

                int remainingCount = db.photoDao().countPhotosWithoutHashtags();

                if (remainingCount > 0) {
                    // 2초 후 다음 배치 처리 (더 빠르게)
                    mainHandler.postDelayed(() -> {
                        isProcessingPhotos = false;
                        processExistingPhotosWithoutHashtags(MAX_PHOTOS_PER_BATCH);
                    }, 2000);
                } else {
                    isProcessingPhotos = false;
                    // 즉시 신규 사진 스캔 시작
                    scanNewPhotosInBatches();
                }

            } catch (Exception e) {
                Log.e(TAG, "해시태그 초기화 중 오류", e);
                isProcessingPhotos = false;
            } finally {
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

    // 신규 사진을 배치로 계속 처리하는 새로운 메서드
    private void scanNewPhotosInBatches() {
        if (isProcessingPhotos) {
            Log.d(TAG, "이미 처리 중입니다.");
            return;
        }

        isProcessingPhotos = true;

        backgroundExecutor.execute(() -> {
            Log.d(TAG, "신규 사진 배치 스캔 시작");

            try {
                AppDatabase db = AppDatabase.getInstance(this);
                List<Uri> allImageUris = ImageUtils.getAllImageUris(this);
                Log.d(TAG, "기기에서 발견된 총 이미지 수: " + allImageUris.size());

                // DB에 이미 있는 사진 경로들을 Set으로 미리 로드 (성능 향상)
                Set<String> existingPaths = new HashSet<>(db.photoDao().getAllPhotoPaths());
                Log.d(TAG, "DB에 저장된 사진 수: " + existingPaths.size());

                // 처리할 신규 사진 필터링
                List<Uri> newUris = new ArrayList<>();
                for (Uri uri : allImageUris) {
                    if (!existingPaths.contains(uri.toString())) {
                        newUris.add(uri);
                    }
                }

                Log.d(TAG, "처리할 신규 사진 수: " + newUris.size());
                totalPhotosToProcess = newUris.size();
                photosProcessed = 0;

                if (newUris.isEmpty()) {
                    Log.d(TAG, "모든 사진 처리 완료!");
                    isProcessingPhotos = false;
                    mainHandler.post(() -> {
                        Toast.makeText(this, "모든 사진 처리가 완료되었습니다!", Toast.LENGTH_LONG).show();
                        hideProcessingNotification();
                    });
                    return;
                }

                // 진행 상황 표시 업데이트
                mainHandler.post(() -> {
                    if (progressDialog != null) {
                        progressDialog.setMax(totalPhotosToProcess);
                    }
                });

                // 배치 처리
                int batchStart = 0;
                int batchSize = MAX_PHOTOS_PER_BATCH;

                while (batchStart < newUris.size()) {
                    int batchEnd = Math.min(batchStart + batchSize, newUris.size());
                    List<Uri> batch = newUris.subList(batchStart, batchEnd);

                    Log.d(TAG, "배치 처리 시작: " + batchStart + " ~ " + batchEnd);
                    processBatch(batch);

                    batchStart = batchEnd;

                    // 배치 간 짧은 휴식
                    Thread.sleep(1000);

                    // 진행률 업데이트
                    updateProcessingProgress(photosProcessed, totalPhotosToProcess);
                }

                Log.d(TAG, "모든 신규 사진 처리 완료!");
                isProcessingPhotos = false;

                mainHandler.post(() -> {
                    Toast.makeText(this, "모든 사진 처리가 완료되었습니다!", Toast.LENGTH_LONG).show();
                    hideProcessingNotification();
                });

            } catch (Exception e) {
                Log.e(TAG, "신규 사진 배치 스캔 중 오류", e);
                isProcessingPhotos = false;
                mainHandler.post(() -> {
                    Toast.makeText(this, "사진 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    hideProcessingNotification();
                });
            }
        });
    }

    private void processBatch(List<Uri> uris) {
        ImageClassifier classifier = null;

        try {
            classifier = new ImageClassifier(this);
            int processedCount = 0;

            for (Uri uri : uris) {
                try {
                    Bitmap bitmap = ImageUtils.loadBitmapFromUri(this, uri);
                    if (bitmap != null) {
                        ImageMeta meta = imageRepository.classifyImage(uri, bitmap);
                        Photo savedPhoto = imageRepository.savePhotoToDB(uri, meta);

                        if (savedPhoto != null) {
                            processedCount++;
                            photosProcessed++;
                            Log.d(TAG, "사진 저장 완료 (" + photosProcessed + "/" + totalPhotosToProcess + "): " + uri.toString());

                            // UI 업데이트
                            if (photosProcessed % 5 == 0) { // 5개마다 UI 업데이트
                                updateProcessingProgress(photosProcessed, totalPhotosToProcess);
                            }
                        }

                        bitmap.recycle();

                        // 더 짧은 지연
                        Thread.sleep(30);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "사진 처리 중 오류: " + uri.toString(), e);
                }
            }

            Log.d(TAG, "배치 처리 완료: " + processedCount + "개");

        } catch (Exception e) {
            Log.e(TAG, "배치 처리 중 오류", e);
        } finally {
            if (classifier != null) {
                classifier.close();
            }
        }
    }

    // 진행 상황을 표시하는 메서드들
    private void showProcessingNotification() {
        mainHandler.post(() -> {
            // 간단한 프로그레스 다이얼로그 표시
            if (progressDialog == null) {
                progressDialog = new ProgressDialog(this);
                progressDialog.setTitle("사진 처리 중");
                progressDialog.setMessage("신규 사진을 분석하고 있습니다...");
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setCancelable(false);
                progressDialog.setMax(100);
                progressDialog.show();
            }
        });
    }

    private void updateProcessingProgress(int current, int total) {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.setMax(total);
                progressDialog.setProgress(current);
                int percentage = (int) ((current / (float) total) * 100);
                progressDialog.setMessage("사진 처리 중... (" + current + "/" + total + ") - " + percentage + "%");
            }
        });
    }

    private void hideProcessingNotification() {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
                progressDialog = null;
            }
        });
    }

    // 선택사항: 캐시 상태 모니터링
    private void logCacheStats() {
        ThumbnailCache cache = ThumbnailCache.getInstance(this);
        Log.d(TAG, "썸네일 캐시 상태: " + cache.getCacheStats());
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

        // StoryGenerator 초기화
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

        Fragment storyFragment = getSupportFragmentManager().findFragmentByTag("StoryFragment");
        if (storyFragment instanceof StoryFragment) {
            ((StoryFragment) storyFragment).loadStoriesForDate(dateString);
        }

        // 날짜 범위인지 확인 (콜론 포함)
        if (dateString.contains(":")) {
            dataManager.loadPhotosForDateRange(dateString, new DataManager.OnDataLoadedListener() {
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

                    // UI 업데이트
                    uiManager.updateTimelineData(enhancedTimeline);

                    // 스토리 생성 코드
                    TimelineManager timelineManager = TimelineManager.getInstance(MainActivity.this);
                    timelineManager.setOnStoryGeneratedListener(itemsWithStories -> {
                        runOnUiThread(() -> {
                            Log.d(TAG, "스토리 생성 완료: " + itemsWithStories.size() + "개 항목");
                            uiManager.updateTimelineData(itemsWithStories);
                            // Toast.makeText(MainActivity.this, "스토리가 준비되었습니다!", Toast.LENGTH_SHORT).show();
                        });
                    });

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
        } else {
            // 기존 단일 날짜 처리
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

                            // 스토리 준비 완료 알림 표시
                            // Toast.makeText(MainActivity.this, "스토리가 준비되었습니다!", Toast.LENGTH_SHORT).show();
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
        // 디버깅용: 캐시 상태 로그
        logCacheStats();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 필요시 캐시 상태 저장
    }

    // MainActivity.java에서 스토리 구성 요소 초기화
    private void initStoryComponents() {
        // UIManager를 통해 스토리 컴포넌트 초기화
        uiManager = UIManager.getInstance(this);

        // StoryGenerator 초기화
        StoryGenerator.getInstance(this);

        // UIManager가 바텀 시트 설정 시 스토리 관련 컴포넌트 설정
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
}