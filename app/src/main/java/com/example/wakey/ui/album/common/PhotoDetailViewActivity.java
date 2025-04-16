package com.example.wakey.ui.album.common;

import static android.content.ContentValues.TAG;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.util.ToastManager;
import com.github.chrisbanes.photoview.PhotoView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class PhotoDetailViewActivity extends AppCompatActivity {

    private PhotoView fullScreenPhotoView;
    private View topControlBar, bottomInfoBar;
    private TextView photoLocationText, photoDateText;
    private ImageButton backButton, sharePhotoButton, favoritePhotoButton, deletePhotoButton;

    private String photoPath;
    private int currentPosition;
    private GestureDetectorCompat gestureDetector;
    private boolean controlsVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_detail_view);

        // Get data from intent
        photoPath = getIntent().getStringExtra("PHOTO_PATH");
        currentPosition = getIntent().getIntExtra("POSITION", 0);

        if (photoPath == null) {
            finish();
            return;
        }

        initViews();
        setupListeners();
        loadPhotoDetails();
    }

    private void initViews() {
        fullScreenPhotoView = findViewById(R.id.fullScreenPhotoView);
        topControlBar = findViewById(R.id.topControlBar);
        bottomInfoBar = findViewById(R.id.bottomInfoBar);
        photoLocationText = findViewById(R.id.photoLocationText);
        photoDateText = findViewById(R.id.photoDateText);

        backButton = findViewById(R.id.backButton);
        sharePhotoButton = findViewById(R.id.sharePhotoButton);
        favoritePhotoButton = findViewById(R.id.favoritePhotoButton);
        deletePhotoButton = findViewById(R.id.deletePhotoButton);

        // Initialize gesture detector
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleControlsVisibility();
                return true;
            }
        });
    }

    private void setupListeners() {
        // Back button
        backButton.setOnClickListener(v -> onBackPressed());

        // Share button
        sharePhotoButton.setOnClickListener(v -> sharePhoto());

        // Favorite button
        favoritePhotoButton.setOnClickListener(v -> favoritePhoto());

        // Delete button
        deletePhotoButton.setOnClickListener(v -> deletePhoto());

        // Photo view touch listener
        fullScreenPhotoView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false; // Return false to allow the event to be processed by PhotoView
        });
    }

    private void toggleControlsVisibility() {
        controlsVisible = !controlsVisible;
        topControlBar.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        bottomInfoBar.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
    }

    private void loadPhotoDetails() {

        // Load photo image with Glide
        if (photoPath != null && !photoPath.isEmpty()) {
            try {
                if (photoPath.startsWith("/") || photoPath.startsWith("content:") ||
                        photoPath.startsWith("file:")) {

                    Uri photoUri = Uri.parse(photoPath);

                    // 안전한 이미지 로딩
                    Glide.with(this)
                            .load(photoUri)
                            .placeholder(R.drawable.placeholder_image)
                            .error(R.drawable.error_image)
                            .into(fullScreenPhotoView);
                } else {
                    // 리소스 ID로 추정되는 경우
                    try {
                        int resourceId = Integer.parseInt(photoPath.replace("photo_", ""));
                        fullScreenPhotoView.setImageResource(R.drawable.placeholder_image);
                    } catch (NumberFormatException e) {
                        // 기본 이미지 표시
                        fullScreenPhotoView.setImageResource(R.drawable.placeholder_image);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading image: " + e.getMessage());
                fullScreenPhotoView.setImageResource(R.drawable.error_image);
            }
        } else {
            // photoPath가 null이거나 비어있는 경우
            fullScreenPhotoView.setImageResource(R.drawable.placeholder_image);
        }

        // Load photo metadata from database
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Photo photo = db.photoDao().getPhotoByFilePath(photoPath);

            if (photo != null) {
                runOnUiThread(() -> {
                    // Format and display location
                    String location = buildLocationString(photo);
                    photoLocationText.setText(location);

                    // Format and display date
                    String formattedDate = formatDate(photo.dateTaken);
                    photoDateText.setText(formattedDate);

                    // Show info bar after data is loaded
                    bottomInfoBar.setVisibility(View.VISIBLE);
                });
            } else {
                // Use dummy data for demo
                runOnUiThread(() -> {
                    photoLocationText.setText("용산구");
                    photoDateText.setText("2025년 4월 6일 오후 3:24");
                    bottomInfoBar.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private String buildLocationString(Photo photo) {
        StringBuilder locationBuilder = new StringBuilder();

        // Add location info if available
        if (photo.locationDo != null && !photo.locationDo.isEmpty()) {
            locationBuilder.append(photo.locationDo);
        }

        if (photo.locationSi != null && !photo.locationSi.isEmpty()) {
            if (locationBuilder.length() > 0) {
                locationBuilder.append(" ");
            }
            locationBuilder.append(photo.locationSi);
        }

        if (photo.locationGu != null && !photo.locationGu.isEmpty()) {
            if (locationBuilder.length() > 0) {
                locationBuilder.append(" ");
            }
            locationBuilder.append(photo.locationGu);
        }

        // Return location or default string
        return locationBuilder.length() > 0 ? locationBuilder.toString() : "위치 정보 없음";
    }

    private String formatDate(String dateTaken) {
        try {
            // Parse date from database (yyyy-MM-dd HH:mm:ss)
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = inputFormat.parse(dateTaken);

            // Format for display (yyyy년 MM월 dd일 a hh:mm)
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy년 MM월 dd일 a h:mm", Locale.KOREAN);
            return outputFormat.format(date);
        } catch (Exception e) {
            // Return original string if parsing fails
            return dateTaken;
        }
    }

    private void sharePhoto() {
        ToastManager.getInstance().showToast("사진 공유 기능은 개발 중입니다");
    }

    private void favoritePhoto() {
        ToastManager.getInstance().showToast("즐겨찾기 기능은 개발 중입니다");
    }

    private void deletePhoto() {
        ToastManager.getInstance().showToast("삭제 기능은 개발 중입니다");
    }
}