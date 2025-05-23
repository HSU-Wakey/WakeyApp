package com.example.wakey.ui.album.common;

import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.ui.photo.PhotoDetailFragment;
import com.example.wakey.util.ToastManager;
import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class AlbumDetailActivity extends AppCompatActivity {
    private static final String TAG = "AlbumDetailActivity";

    private TextView titleTextView, subtitleTextView;
    private RecyclerView photosRecyclerView;
    private ImageButton shareButton, favoriteButton, deleteButton;
    private View bottomBarLayout;

    private String regionName;
    private String regionCode;
    private String yearFilter = null; // Added year filter

    private boolean isSelectionMode = false;
    private List<String> selectedPhotos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_detail);

        // Get data from intent
        regionName = getIntent().getStringExtra("REGION_NAME");
        regionCode = getIntent().getStringExtra("REGION_CODE");
        yearFilter = getIntent().getStringExtra("YEAR_FILTER"); // Get year filter

        Log.d(TAG, "Region: " + regionName + ", Year Filter: " + yearFilter);

        initViews();
        setupListeners();
        loadPhotos();
    }

    private void initViews() {
        // Header views
        titleTextView = findViewById(R.id.albumDetailTitle);
        subtitleTextView = findViewById(R.id.albumDetailSubtitle);

        // Bottom action buttons
        bottomBarLayout = findViewById(R.id.bottomBarLayout);
        shareButton = findViewById(R.id.shareButton);
        favoriteButton = findViewById(R.id.favoriteButton);
        deleteButton = findViewById(R.id.deleteButton);

        // Photos grid
        photosRecyclerView = findViewById(R.id.photosGridView);
        photosRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        // Set region name as title
        titleTextView.setText(regionName);

        // Set subtitle with year filter if available
        String yearText = yearFilter != null ? yearFilter + "년 • " : "";

        if ("seoul".equals(regionCode)) {
            subtitleTextView.setText(yearText + "사진 87장");
        } else if ("japan".equals(regionCode)) {
            subtitleTextView.setText(yearText + "사진 521장");
        } else {
            subtitleTextView.setText(yearText + "사진 45장");
        }

        // Initially hide action buttons until selection mode is activated
        toggleSelectionMode(false);

        // Back button
        findViewById(R.id.backButton).setOnClickListener(v -> onBackPressed());
    }

    private void setupListeners() {
        // Action button listeners
        shareButton.setOnClickListener(v -> shareSelectedPhotos());
        favoriteButton.setOnClickListener(v -> favoriteSelectedPhotos());
        deleteButton.setOnClickListener(v -> deleteSelectedPhotos());
    }

    private void toggleSelectionMode(boolean enable) {
        isSelectionMode = enable;

        // Update visibility of bottom action bar
        bottomBarLayout.setVisibility(enable ? View.VISIBLE : View.GONE);

        // Update photo items to show/hide checkboxes
        if (photosRecyclerView.getAdapter() != null) {
            ((PhotoAdapter)photosRecyclerView.getAdapter()).setSelectionMode(enable);
        }
    }

    /**
     * Load all photos for this region
     */
    private void loadPhotos() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<Photo> dbPhotos = db.photoDao().getAllPhotos();

            // 최종 TimelineItem 리스트를 저장할 리스트
            final List<TimelineItem> timelineItems = new ArrayList<>();
            // 기존의 photoUrls 리스트도 유지 (UI 업데이트에 사용)
            List<String> photoUrls = new ArrayList<>();

            // Filter photos by region
            for (Photo photo : dbPhotos) {
                // Apply year filter if set
                if (yearFilter != null && (photo.dateTaken == null || !photo.dateTaken.startsWith(yearFilter))) {
                    continue;
                }

                // Filter logic based on region
                if (matchesRegion(photo, regionName)) {
                    photoUrls.add(photo.filePath);

                    // Photo를 TimelineItem으로 변환
                    TimelineItem item = convertPhotoToTimelineItem(photo);
                    timelineItems.add(item);
                }
            }

            // If no photos found, add dummy data for demo
            if (photoUrls.isEmpty()) {
                for (int i = 0; i < 30; i++) {
                    String dummyPath = "photo_" + i;
                    photoUrls.add(dummyPath);

                    // 더미 TimelineItem 생성
                    TimelineItem dummyItem = new TimelineItem.Builder()
                            .setPhotoPath(dummyPath)
                            .setActivityType("일상")
                            .build();
                    timelineItems.add(dummyItem);
                }
            }

            // UI는 메인 스레드에서 업데이트
            final List<TimelineItem> finalTimelineItems = timelineItems;
            runOnUiThread(() -> {
                PhotoAdapter adapter = new PhotoAdapter(photoUrls);
                adapter.setOnItemClickListener(new PhotoAdapter.OnPhotoClickListener() {
                    @Override
                    public void onPhotoClick(String photoPath, int position) {
                        if (isSelectionMode) {
                            togglePhotoSelection(photoPath);
                        } else {
                            // position이 범위 내에 있는지 확인
                            if (position >= 0 && position < finalTimelineItems.size()) {
                                showPhotoDetail(photoPath, position, finalTimelineItems.get(position));
                            } else {
                                // TimelineItem이 없으면 기본 메서드 호출
                                showPhotoDetail(photoPath, position);
                            }
                        }
                    }

                    @Override
                    public void onPhotoLongClick(String photoPath, int position) {
                        if (!isSelectionMode) {
                            toggleSelectionMode(true);
                            togglePhotoSelection(photoPath);
                        }
                    }
                });
                photosRecyclerView.setAdapter(adapter);

                // Update subtitle with photo count
                updatePhotoCount(photoUrls.size());
            });
        });
    }

    /**
     * Photo 객체를 TimelineItem으로 변환
     */
    private TimelineItem convertPhotoToTimelineItem(Photo photo) {
        TimelineItem.Builder builder = new TimelineItem.Builder();

        // 필수 정보 설정
        builder.setPhotoPath(photo.filePath);

        // 날짜 설정
        if (photo.dateTaken != null) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date date = format.parse(photo.dateTaken);
                builder.setTime(date);
            } catch (Exception e) {
                // 파싱 실패 시 timestamp 사용 시도
                final long timestamp = photo.timestamp;
                if (timestamp > 0) {
                    builder.setTime(new Date(timestamp));
                } else {
                    Log.e(TAG, "날짜 파싱 실패: " + photo.dateTaken, e);
                }
            }
        } else if (photo.timestamp > 0) {
            // dateTaken이 없지만 timestamp가 있는 경우
            builder.setTime(new Date(photo.timestamp));
        }

        // 위치 정보 설정
        StringBuilder locationBuilder = new StringBuilder();
        if (photo.locationDo != null) locationBuilder.append(photo.locationDo).append(" ");
        if (photo.locationSi != null) locationBuilder.append(photo.locationSi).append(" ");
        if (photo.locationGu != null) locationBuilder.append(photo.locationGu);
        final String location = locationBuilder.toString().trim();

        if (!location.isEmpty()) {
            builder.setLocation(location);
        } else {
            final String fullAddress = photo.fullAddress;
            final String country = photo.country;

            if (fullAddress != null && !fullAddress.isEmpty()) {
                // fullAddress가 있으면 사용
                builder.setLocation(fullAddress);
            } else if (country != null && !country.isEmpty()) {
                // country가 있으면 사용
                builder.setLocation(country);
            }
        }

        // 위도/경도 설정
        final Double latitude = photo.latitude;
        final Double longitude = photo.longitude;
        if (latitude != null && longitude != null &&
                (latitude != 0 || longitude != 0)) {
            builder.setLatLng(new LatLng(latitude, longitude));
        }

        // 캡션 설정
        final String caption = photo.caption;
        if (caption != null && !caption.isEmpty()) {
            builder.setCaption(caption);
        }

        // 스토리 설정
        final String story = photo.story;
        if (story != null && !story.isEmpty()) {
            builder.setStory(story);
        }

        // 감지된 객체 설정
        final String detectedObjects = photo.detectedObjects;
        if (detectedObjects != null && !detectedObjects.isEmpty()) {
            builder.setDetectedObjects(detectedObjects);
        }

        // 객체 쌍 설정
        final List<Pair<String, Float>> detectedObjectPairs = photo.detectedObjectPairs;
        if (detectedObjectPairs != null && !detectedObjectPairs.isEmpty()) {
            builder.setDetectedObjectPairs(detectedObjectPairs);
        }

        // 활동 유형 설정 - 기본값 사용
        builder.setActivityType("일상");

        return builder.build();
    }

    /**
     * Toggle selection status of a photo
     */
    private void togglePhotoSelection(String photoPath) {
        if (selectedPhotos.contains(photoPath)) {
            selectedPhotos.remove(photoPath);
        } else {
            selectedPhotos.add(photoPath);
        }

        // Update UI to reflect selection
        ((PhotoAdapter)photosRecyclerView.getAdapter()).toggleSelection(photoPath);

        // If no photos selected and in selection mode, exit selection mode
        if (selectedPhotos.isEmpty() && isSelectionMode) {
            toggleSelectionMode(false);
        }
    }

    /**
     * Show detail view for a photo (기존 photoPath만 사용하는 버전)
     */
    private void showPhotoDetail(String photoPath, int position) {
        // TimelineItem 객체 생성
        AtomicReference<TimelineItem> timelineItem = new AtomicReference<>(new TimelineItem.Builder()
                .setPhotoPath(photoPath)
                .setActivityType("일상") // 기본값
                .build());

        // 데이터베이스에서 해당 사진의 추가 정보 가져오기
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Photo photo = db.photoDao().getPhotoByFilePath(photoPath);

            if (photo != null) {
                // 변환된 TimelineItem으로 대체
                timelineItem.set(convertPhotoToTimelineItem(photo));
            }

            runOnUiThread(() -> {
                // PhotoDetailFragment 표시
                PhotoDetailFragment detailFragment = PhotoDetailFragment.newInstance(timelineItem.get());
                detailFragment.show(getSupportFragmentManager(), "photo_detail");
            });
        });
    }

    /**
     * Show detail view for a photo (TimelineItem을 전달받는 오버로드 버전)
     */
    private void showPhotoDetail(String photoPath, int position, TimelineItem timelineItem) {
        if (timelineItem != null) {
            // TimelineItem이 이미 있으면 바로 사용
            PhotoDetailFragment detailFragment = PhotoDetailFragment.newInstance(timelineItem);
            detailFragment.show(getSupportFragmentManager(), "photo_detail");
        } else {
            // TimelineItem이 없으면 기본 메서드 호출
            showPhotoDetail(photoPath, position);
        }
    }

    /**
     * Share selected photos
     */
    private void shareSelectedPhotos() {
        if (selectedPhotos.isEmpty()) {
            ToastManager.getInstance().showToast("공유할 사진을 선택해주세요");
            return;
        }

        // Share logic would go here
        ToastManager.getInstance().showToast(selectedPhotos.size() + "장의 사진을 공유합니다");

        // Exit selection mode after action
        selectedPhotos.clear();
        toggleSelectionMode(false);
    }

    /**
     * Mark selected photos as favorites
     */
    private void favoriteSelectedPhotos() {
        if (selectedPhotos.isEmpty()) {
            ToastManager.getInstance().showToast("즐겨찾기할 사진을 선택해주세요");
            return;
        }

        // Favorite logic would go here
        ToastManager.getInstance().showToast(selectedPhotos.size() + "장의 사진을 즐겨찾기에 추가했습니다");

        // Exit selection mode after action
        selectedPhotos.clear();
        toggleSelectionMode(false);
    }

    /**
     * Delete selected photos
     */
    private void deleteSelectedPhotos() {
        if (selectedPhotos.isEmpty()) {
            ToastManager.getInstance().showToast("삭제할 사진을 선택해주세요");
            return;
        }

        // Delete logic would go here
        ToastManager.getInstance().showToast(selectedPhotos.size() + "장의 사진을 삭제했습니다");

        // Exit selection mode after action
        selectedPhotos.clear();
        toggleSelectionMode(false);
    }

    /**
     * Update the subtitle with photo count if needed
     */
    private void updatePhotoCount(int count) {
        String yearText = yearFilter != null ? yearFilter + "년 • " : "";
        String photoCountText = yearText + "사진 " + count + "장";
        subtitleTextView.setText(photoCountText);
    }

    /**
     * Check if a photo belongs to the current region
     */
    private boolean matchesRegion(Photo photo, String regionName) {
        // This is a simplified check - in a real app, you would have more sophisticated logic
        return (photo.locationDo != null && photo.locationDo.contains(regionName)) ||
                (photo.locationSi != null && photo.locationSi.contains(regionName)) ||
                (photo.locationGu != null && photo.locationGu.contains(regionName));
    }
}