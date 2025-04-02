package com.example.wakey.ui.search;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.repository.SearchHistoryRepository;
import com.example.wakey.data.util.SimilarityUtil;
import com.example.wakey.tflite.ClipImageEncoder;
import com.example.wakey.tflite.ClipTextEncoder;
import com.example.wakey.tflite.ClipTokenizer;

import java.io.IOException;

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
        resultImageView = findViewById(R.id.resultImageView);
        recentSearchRecyclerView = findViewById(R.id.recentSearchRecyclerView);
        recentSearchRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 최근 검색어 어댑터 연결
        historyAdapter = new SearchHistoryAdapter(SearchHistoryRepository.getInstance(this).getSearchHistory());
        recentSearchRecyclerView.setAdapter(historyAdapter);

        // 검색어 입력 후 엔터 누르면 실행
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                analyzeSimilarity();
                return true;
            }
            return false;
        });
    }

    private void analyzeSimilarity() {
        try {
            String text = searchEditText.getText().toString().trim();

            // 1. 텍스트 → Token ID
            ClipTokenizer tokenizer = new ClipTokenizer(this);
            int[] tokenIds = tokenizer.tokenize(text);

            // 2. Token ID → 텍스트 벡터
            ClipTextEncoder textEncoder = new ClipTextEncoder(this);
            float[] textVec = textEncoder.getTextEncoding(tokenIds);
            textEncoder.close();

            // 3. 이미지 리스트를 돌면서 유사도 계산
            ClipImageEncoder imageEncoder = new ClipImageEncoder(this);
            float maxSim = -1f;
            int bestImageResId = -1;

            for (int resId : imageResIds) {
                Bitmap bitmap = Bitmap.createScaledBitmap(
                        BitmapFactory.decodeResource(getResources(), resId),
                        224, 224, true
                );
                float[] imageVec = imageEncoder.getImageEncoding(bitmap);
                float sim = SimilarityUtil.cosineSimilarity(imageVec, textVec);

                if (sim > maxSim) {
                    maxSim = sim;
                    bestImageResId = resId;
                }
            }

            imageEncoder.close();

            // 4. 유사도 계산 및 결과 출력
            if (bestImageResId != -1) {
                Bitmap bestBitmap = Bitmap.createScaledBitmap(
                        BitmapFactory.decodeResource(getResources(), bestImageResId),
                        224, 224, true
                );
                resultImageView.setImageBitmap(bestBitmap);
                Toast.makeText(this, "가장 유사한 이미지 유사도: " + maxSim, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "이미지를 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "모델 로딩 실패", Toast.LENGTH_SHORT).show();
        }
    }

}
