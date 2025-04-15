package com.example.wakey.data.util;

import android.util.Log;

public class SimilarityUtil {
    public static float cosineSimilarity(float[] imageVec1, float[] imageVec2) {
        if (imageVec1.length != imageVec2.length) {
            Log.d("벡터길이", "텍스트 벡터 길이: " + imageVec1.length);
            Log.d("벡터길이", "이미지 벡터 길이: " + imageVec2.length);
            throw new IllegalArgumentException("벡터 길이가 다릅니다.");
        }

        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < imageVec1.length; i++) {
            dot += imageVec1[i] * imageVec2[i];
            normA += imageVec1[i] * imageVec1[i];
            normB += imageVec2[i] * imageVec2[i];
        }

        float magnitude1 = (float) Math.sqrt(normA);
        float magnitude2 = (float) Math.sqrt(normB);

        // ✅ 2. magnitude가 0이면 비교 불가
        if (magnitude1 == 0 || magnitude2 == 0) {
            Log.e("유사도", "❗ 벡터 크기가 0이어서 비교 불가");
            return -1.0f;
        }

        float similarity = dot / (magnitude1 * magnitude2);
        Log.d("유사도", "✅ 계산된 cosine similarity: " + similarity);
        return similarity;
    }
}