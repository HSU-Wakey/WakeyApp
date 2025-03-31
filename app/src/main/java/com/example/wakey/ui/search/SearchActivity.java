package com.example.wakey.ui.search;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wakey.R;
import com.example.wakey.data.util.SimilarityUtil;
import com.example.wakey.tflite.ClipImageEncoder;
import com.example.wakey.tflite.ClipTextEncoder;
import com.example.wakey.tflite.ClipTokenizer;

import java.io.IOException;

public class SearchActivity extends AppCompatActivity {

    private EditText searchEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_smart_search); // 스마트 검색 XML

        searchEditText = findViewById(R.id.searchEditText);

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

            // 3. 이미지 벡터 (image4.png 사용)
            Bitmap bitmap = Bitmap.createScaledBitmap(
                    BitmapFactory.decodeResource(getResources(), R.drawable.image4),
                    224, 224, true
            );
            ClipImageEncoder imageEncoder = new ClipImageEncoder(this);
            float[] imageVec = imageEncoder.getImageEncoding(bitmap);
            imageEncoder.close();

            // 4. 유사도 계산
            float similarity = SimilarityUtil.cosineSimilarity(imageVec, textVec);

            // 5. 결과 출력
            Toast.makeText(this, "image4와 유사도: " + similarity, Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "모델 로딩 실패", Toast.LENGTH_SHORT).show();
        }
    }

}
