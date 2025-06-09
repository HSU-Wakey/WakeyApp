package com.example.wakey.ui.search;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.repository.SearchHistoryRepository;
import com.example.wakey.data.util.SimilarityUtil;
import com.example.wakey.tflite.ClipTextEncoder;
import com.example.wakey.tflite.ClipTokenizer;

import java.io.IOException;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private EditText searchEditText;
    private ImageView resultImageView;
    private RecyclerView recentSearchRecyclerView;
    private SearchHistoryAdapter historyAdapter;

    // drawable 이미지 리소스 ID 리스트
    private final int[] imageResIds = {
            R.drawable.image1,
            R.drawable.image2,
            R.drawable.image3,
            R.drawable.image4
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_smart_search); // 스마트 검색 XML

        // 기본 UI 초기화
        searchEditText = findViewById(R.id.searchEditText);
//        resultImageView = findViewById(R.id.resultImageView);
//        recentSearchRecyclerView = findViewById(R.id.recentSearchRecyclerView);
//        recentSearchRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

//        // 최근 검색어 어댑터 연결
//        historyAdapter = new SearchHistoryAdapter(SearchHistoryRepository.getInstance(this).getSearchHistory());
//        recentSearchRecyclerView.setAdapter(historyAdapter);

        // 검색어 입력 후 엔터 누르면 실행
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // 키보드 숨기기
                hideKeyboard();
                // 검색 실행
                analyzeSimilarity();
                return true;
            }
            return false;
        });
    }

    /**
     * 키보드를 숨기는 메서드
     */
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }

        // EditText의 포커스도 제거 (선택사항)
        searchEditText.clearFocus();
    }

    private void analyzeSimilarity() {
        try {
            String query = searchEditText.getText().toString().trim();
            if (query.isEmpty()) {
                Toast.makeText(this, "검색어를 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }

            // 로딩 표시 (선택사항)
            Toast.makeText(this, "검색 중...", Toast.LENGTH_SHORT).show();

            // 텍스트 벡터 생성
            ClipTokenizer tokenizer = new ClipTokenizer(this);
            int[] tokenIds = tokenizer.tokenize(query);
            ClipTextEncoder encoder = new ClipTextEncoder(this);
            float[] queryVec = encoder.getTextEncoding(tokenIds);
            encoder.close();

            // DB의 모든 사진과 비교
            List<Photo> photos = AppDatabase.getInstance(this).photoDao().getAllPhotos();
            float maxSim = -1f;
            Photo bestMatch = null;

            for (Photo photo : photos) {
                float[] vec = photo.getEmbeddingVector();
                if (vec == null) continue;
                float sim = SimilarityUtil.cosineSimilarity(queryVec, vec);
                if (sim > maxSim) {
                    maxSim = sim;
                    bestMatch = photo;
                }
            }

            if (bestMatch != null) {
                // resultImageView가 주석처리되어 있어서 Toast로만 결과 표시
                Toast.makeText(this, "가장 유사한 사진 발견! 유사도: " + String.format("%.3f", maxSim), Toast.LENGTH_LONG).show();

                // resultImageView가 활성화되어 있다면 아래 코드 사용
                /*
                Glide.with(this)
                        .load(Uri.parse(bestMatch.getFilePath()))
                        .into(resultImageView);
                */
            } else {
                Toast.makeText(this, "일치하는 이미지가 없습니다", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "모델 로딩 실패", Toast.LENGTH_SHORT).show();
        }
    }
}