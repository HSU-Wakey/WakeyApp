package com.example.wakey.ui.album.diary;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.audio.AudioRecorder;
import com.example.wakey.audio.MelSpectrogramGenerator;
import com.example.wakey.audio.WhisperDecoder;
import com.example.wakey.audio.WhisperEncoder;
import com.example.wakey.audio.WhisperTokenizer;
import com.example.wakey.util.ToastManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class DiaryDetailActivity extends AppCompatActivity {
    private boolean isRecording = false;
    private AudioRecorder recorder;
    private TextView recordingStatusTextView;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1001;
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

        recorder = new AudioRecorder();
        recordingStatusTextView = findViewById(R.id.recordingStatusText);
        FloatingActionButton recordVoiceFab = findViewById(R.id.recordVoiceFab);
        recordVoiceFab.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecordingAndRunInference();
            }
        });

    }


    private void startRecording() {
        isRecording = true;
        recorder.startRecording();
        recordingStatusTextView.setText("🎙 녹음 중...");
        ToastManager.getInstance().showToast("녹음을 시작했습니다");
    }

    private void stopRecordingAndRunInference() {
        isRecording = false;
        recordingStatusTextView.setText("");
        ToastManager.getInstance().showToast("녹음 종료, 변환 중...");

        float[] audioData = recorder.stopRecordingAndGetData();
        float[][][] mel = MelSpectrogramGenerator.generate(audioData);
        WhisperEncoder encoder = new WhisperEncoder(this);
        float[][][] encoderOutput = encoder.runInference(mel);
        WhisperDecoder decoder = new WhisperDecoder(this);
        WhisperTokenizer tokenizer = new WhisperTokenizer(this);

        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(50258); // BOS

        for (int i = 0; i < 100; i++) {
            int[] inputTokens = tokenIds.stream().mapToInt(Integer::intValue).toArray();
            float[][] logits = decoder.run(encoderOutput, inputTokens);

            int nextToken = 0;
            float maxLogit = -Float.MAX_VALUE;
            for (int j = 0; j < logits[0].length; j++) {
                if (logits[0][j] > maxLogit) {
                    maxLogit = logits[0][j];
                    nextToken = j;
                }
            }

            if (nextToken == 50257) break; // EOT
            tokenIds.add(nextToken);
        }

        int[] finalTokenArray = tokenIds.stream().mapToInt(Integer::intValue).toArray();
        String result = tokenizer.decode(finalTokenArray);
        contentEditText.setText(result);
    }
    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST);
    }

    private void checkAudioPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        // 권한이 있을 경우 실제 녹음 시작
        startRecording();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkAudioPermissionAndStartRecording(); // 다시 시도
            } else {
                ToastManager.getInstance().showToast("음성 녹음 권한이 필요합니다");
            }
        }
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