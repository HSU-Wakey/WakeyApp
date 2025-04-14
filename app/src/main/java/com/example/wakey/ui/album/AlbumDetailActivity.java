package com.example.wakey.ui.album;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.util.ToastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

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

            // Filter photos by region
            List<String> photoUrls = new ArrayList<>();
            for (Photo photo : dbPhotos) {
                // Apply year filter if set
                if (yearFilter != null && (photo.dateTaken == null || !photo.dateTaken.startsWith(yearFilter))) {
                    continue;
                }

                // Filter logic based on region
                if (matchesRegion(photo, regionName)) {
                    photoUrls.add(photo.filePath);
                }
            }

            // If no photos found, add dummy data for demo
            if (photoUrls.isEmpty()) {
                for (int i = 0; i < 30; i++) {
                    photoUrls.add("photo_" + i);
                }
            }

            runOnUiThread(() -> {
                PhotoAdapter adapter = new PhotoAdapter(photoUrls);
                adapter.setOnItemClickListener(new PhotoAdapter.OnPhotoClickListener() {
                    @Override
                    public void onPhotoClick(String photoPath, int position) {
                        if (isSelectionMode) {
                            togglePhotoSelection(photoPath);
                        } else {
                            showPhotoDetail(photoPath, position);
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
     * Show detail view for a photo
     */
    private void showPhotoDetail(String photoPath, int position) {
        // Open photo detail view activity
        Intent intent = new Intent(this, PhotoDetailViewActivity.class);
        intent.putExtra("PHOTO_PATH", photoPath);
        intent.putExtra("POSITION", position);
        startActivity(intent);
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