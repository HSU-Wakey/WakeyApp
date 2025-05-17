package com.example.wakey.ui.search;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

public class HashtagPhotosActivity extends AppCompatActivity {

    private String hashtag;
    private RecyclerView photoGridRecyclerView;
    private HashtagPhotoAdapter adapter;
    private TextView hashtagTitleTextView;
    private TextView photoCountTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hashtag_photos);

        // Intent에서 해시태그 받기
        hashtag = getIntent().getStringExtra("hashtag");
        if (hashtag == null) {
            finish();
            return;
        }

        initializeViews();
        setupToolbar();
        loadHashtagPhotos();
    }

    private void initializeViews() {
        photoGridRecyclerView = findViewById(R.id.photoGridRecyclerView);
        hashtagTitleTextView = findViewById(R.id.hashtagTitleTextView);
        photoCountTextView = findViewById(R.id.photoCountTextView);

        // GridLayoutManager 설정 (3열)
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        photoGridRecyclerView.setLayoutManager(gridLayoutManager);

        // FragmentManager를 어댑터에 전달
        adapter = new HashtagPhotoAdapter(new ArrayList<>(), getSupportFragmentManager());
        photoGridRecyclerView.setAdapter(adapter);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        hashtagTitleTextView.setText("#" + hashtag);
    }

    private void loadHashtagPhotos() {
        new Thread(() -> {
            try {
                Log.d("HashtagPhotos", "검색할 해시태그: " + hashtag);
                Log.d("HashtagPhotos", "검색할 정확한 패턴: '#" + hashtag + "'");

                // 데이터베이스에서 모든 사진의 해시태그 확인
                List<Photo> allPhotos = AppDatabase.getInstance(this)
                        .photoDao()
                        .getAllPhotos();

                for (Photo photo : allPhotos) {
                    if (photo.hashtags != null && !photo.hashtags.isEmpty()) {
                        Log.d("HashtagPhotos", "사진: " + photo.getFilePath() + ", 해시태그: " + photo.hashtags);
                    }
                }

                // 특정 해시태그로 검색
                List<Photo> photos = AppDatabase.getInstance(this)
                        .photoDao()
                        .getPhotosByHashtag(hashtag);

                Log.d("HashtagPhotos", "검색된 사진 개수: " + (photos != null ? photos.size() : 0));

                final List<Photo> finalPhotos = photos;
                runOnUiThread(() -> {
                    if (finalPhotos == null || finalPhotos.isEmpty()) {
                        photoCountTextView.setText("(0장)");
                        Toast.makeText(this, "해당 해시태그의 사진이 없습니다.", Toast.LENGTH_SHORT).show();
                        Log.d("HashtagPhotos", "결과 없음");
                    } else {
                        adapter.updatePhotos(finalPhotos);
                        photoCountTextView.setText("(" + finalPhotos.size() + "장)");
                        Log.d("HashtagPhotos", "결과 표시: " + finalPhotos.size() + "장");
                    }
                });
            } catch (Exception e) {
                Log.e("HashtagPhotos", "사진 로딩 실패: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    photoCountTextView.setText("(0장)");
                    Toast.makeText(this, "사진 로딩 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}