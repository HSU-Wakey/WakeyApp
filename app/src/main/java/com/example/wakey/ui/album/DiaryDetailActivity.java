package com.example.wakey.ui.album;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.util.ToastManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DiaryDetailActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;

    private ImageView coverImageView;
    private EditText titleEditText, contentEditText;
    private TextView dateRangeTextView;
    private LinearLayout heartRatingContainer;
    private FloatingActionButton changeImageFab;
    private int currentRating = 0;
    private String currentImagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary_detail);

        // Initialize views
        coverImageView = findViewById(R.id.diaryCoverImage);
        titleEditText = findViewById(R.id.diaryTitleEdit);
        contentEditText = findViewById(R.id.diaryContentEdit);
        dateRangeTextView = findViewById(R.id.diaryDateRangeText);
        heartRatingContainer = findViewById(R.id.heartRatingContainer);
        changeImageFab = findViewById(R.id.changeImageFab);

        // Set current date as default
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        dateRangeTextView.setText(currentDate);

        // Setup heart rating
        setupHeartRating();

        // Back button
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());

        // Save button
        findViewById(R.id.saveButton).setOnClickListener(v -> saveDiary());

        // Change image FAB
        changeImageFab.setOnClickListener(v -> openGallery());

        // Get data from intent if available (for editing existing diary)
        if (getIntent().hasExtra("DIARY_TITLE")) {
            titleEditText.setText(getIntent().getStringExtra("DIARY_TITLE"));
            dateRangeTextView.setText(getIntent().getStringExtra("DIARY_DATE_RANGE"));
            contentEditText.setText(getIntent().getStringExtra("DIARY_CONTENT"));
            currentRating = getIntent().getIntExtra("DIARY_RATING", 0);
            updateHeartDisplay();

            // Load cover image if available
            currentImagePath = getIntent().getStringExtra("DIARY_THUMBNAIL");
            if (currentImagePath != null && !currentImagePath.isEmpty()) {
                Glide.with(this)
                        .load(Uri.parse(currentImagePath))
                        .centerCrop()
                        .into(coverImageView);
            }
        }
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                // Update the current image path
                currentImagePath = selectedImageUri.toString();

                // Load the selected image into the cover ImageView
                Glide.with(this)
                        .load(selectedImageUri)
                        .centerCrop()
                        .into(coverImageView);

                ToastManager.getInstance().showToast("커버 이미지가 변경되었습니다");
            }
        }
    }

    private void setupHeartRating() {
        // Clear previous hearts
        heartRatingContainer.removeAllViews();

        // Create 5 hearts
        for (int i = 0; i < 5; i++) {
            final int heartIndex = i;
            ImageView heartView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(8, 0, 8, 0);
            heartView.setLayoutParams(params);
            heartView.setImageResource(R.drawable.ic_heart_grey);

            // Set click listener
            heartView.setOnClickListener(v -> {
                currentRating = heartIndex + 1;
                updateHeartDisplay();
            });

            heartRatingContainer.addView(heartView);
        }

        // Update display based on current rating
        updateHeartDisplay();
    }

    private void updateHeartDisplay() {
        for (int i = 0; i < heartRatingContainer.getChildCount(); i++) {
            ImageView heartView = (ImageView) heartRatingContainer.getChildAt(i);
            if (i < currentRating) {
                heartView.setImageResource(R.drawable.ic_heart_filled);
            } else {
                heartView.setImageResource(R.drawable.ic_heart_grey);
            }
        }
    }

    private void saveDiary() {
        String title = titleEditText.getText().toString().trim();
        String content = contentEditText.getText().toString().trim();
        String dateRange = dateRangeTextView.getText().toString();

        if (title.isEmpty()) {
            ToastManager.getInstance().showToast("제목을 입력해주세요");
            return;
        }

        // Return selected data back to the calling activity
        Intent resultIntent = new Intent();
        resultIntent.putExtra("DIARY_TITLE", title);
        resultIntent.putExtra("DIARY_CONTENT", content);
        resultIntent.putExtra("DIARY_DATE_RANGE", dateRange);
        resultIntent.putExtra("DIARY_RATING", currentRating);
        resultIntent.putExtra("DIARY_THUMBNAIL", currentImagePath);

        setResult(RESULT_OK, resultIntent);

        ToastManager.getInstance().showToast("일기가 저장되었습니다");
        finish();
    }
}