package com.example.wakey.data.util;

public class SimilarityUtil {
    public static float cosineSimilarity(float[] imageVec1, float[] imageVec2) {
        if (imageVec1.length != imageVec2.length) {
            throw new IllegalArgumentException("벡터 길이가 다릅니다.");
        }

        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < imageVec1.length; i++) {
            dot += imageVec1[i] * imageVec2[i];
            normA += imageVec1[i] * imageVec1[i];
            normB += imageVec2[i] * imageVec2[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}