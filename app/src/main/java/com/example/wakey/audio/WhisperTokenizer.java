package com.example.wakey.audio;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;

public class WhisperTokenizer {
    private static final String TAG = "녹음디버그 Tokenizer";
    private final HashMap<Integer, String> idToToken = new HashMap<>();

    public WhisperTokenizer(Context context) {
        try {
            InputStream is = context.getAssets().open("tokenizer.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JSONObject jsonObject = new JSONObject(jsonBuilder.toString());
            JSONObject modelObj = jsonObject.getJSONObject("model");
            JSONObject vocabObj = modelObj.getJSONObject("vocab");

            Iterator<String> keys = vocabObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int tokenId = vocabObj.getInt(key);
                idToToken.put(tokenId, key);
            }

            Log.d(TAG, "Tokenizer 로드 완료, vocab size=" + idToToken.size());
        } catch (Exception e) {
            Log.e(TAG, "Tokenizer 로드 실패: " + e.getMessage(), e);
            throw new RuntimeException("Failed to load tokenizer", e);
        }
    }

    public String decode(int[] tokenIds) {
        StringBuilder sb = new StringBuilder();
        for (int id : tokenIds) {
            String token = idToToken.get(id);
            if (token == null) {
                Log.w(TAG, "알 수 없는 토큰 ID: " + id);
                continue;
            }
            if (token.startsWith("▁")) {
                sb.append(" ").append(token.substring(1));
            } else {
                sb.append(token);
            }
        }
        return sb.toString().trim();
    }
}