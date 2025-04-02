package com.example.wakey.tflite;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class ClipTokenizer {

    private static final String TAG = "ClipTokenizer";

    private static final int MAX_TOKENS = 77;
    private static final int SOS_TOKEN_ID = 49406;
    private static final int EOS_TOKEN_ID = 49407;

    private final Map<String, Integer> vocab = new HashMap<>();
    private final Map<String, Integer> mergeRanks = new HashMap<>();

    public ClipTokenizer(Context context) throws IOException {
        loadVocab(context);
        loadMerges(context);
    }

    private void loadVocab(Context context) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("tokenizer/vocab.json"))
        );
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }

        try {
            JSONObject jsonObject = new JSONObject(jsonBuilder.toString());
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    int value = jsonObject.getInt(key);
                    vocab.put(key, value);
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "JSONException while parsing vocab value", e);
                }
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "JSONException while creating JSONObject", e);
        }
    }

    private void loadMerges(Context context) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("tokenizer/merges.txt"))
        );
        String line;
        boolean isFirst = true;
        int rank = 0;
        while ((line = reader.readLine()) != null) {
            if (isFirst) { // 첫 줄은 버전 정보
                isFirst = false;
                continue;
            }
            String[] parts = line.split(" ");
            if (parts.length == 2) {
                String pair = parts[0] + " " + parts[1];
                mergeRanks.put(pair, rank++);
            }
        }
    }

    public int[] tokenize(String text) {
        List<Integer> tokenIds = new ArrayList<>();
        tokenIds.add(SOS_TOKEN_ID);

        for (String token : basicTokenize(text)) {
            List<String> bpeTokens = bpe(token);
            for (String bpeToken : bpeTokens) {
                Integer id = vocab.get(bpeToken);
                if (id != null) {
                    tokenIds.add(id);
                }
            }
        }

        tokenIds.add(EOS_TOKEN_ID);

        // Padding
        while (tokenIds.size() < MAX_TOKENS) {
            tokenIds.add(0);
        }
        if (tokenIds.size() > MAX_TOKENS) {
            tokenIds = tokenIds.subList(0, MAX_TOKENS);
        }

        int[] result = new int[MAX_TOKENS];
        for (int i = 0; i < MAX_TOKENS; i++) {
            result[i] = tokenIds.get(i);
        }
        return result;
    }

    private List<String> basicTokenize(String text) {
        // 간단한 공백 기준 토큰화 및 소문자 처리
        text = text.trim().toLowerCase(Locale.ROOT);
        return Arrays.asList(text.split("\\s+"));
    }

    private List<String> bpe(String token) {
        List<String> chars = new ArrayList<>();
        for (char c : token.toCharArray()) {
            chars.add(String.valueOf(c));
        }
        if (chars.size() == 0) return new ArrayList<>();

        List<String> pairs = getPairs(chars);
        while (true) {
            String bigram = getLowestRankPair(pairs);
            if (bigram == null || !mergeRanks.containsKey(bigram)) break;

            String[] parts = bigram.split(" ");
            List<String> newChars = new ArrayList<>();
            int i = 0;
            while (i < chars.size()) {
                int j = indexOfPair(chars, parts[0], parts[1], i);
                if (j == -1) {
                    newChars.addAll(chars.subList(i, chars.size()));
                    break;
                }
                newChars.addAll(chars.subList(i, j));
                newChars.add(parts[0] + parts[1]);
                i = j + 2;
            }
            chars = newChars;
            pairs = getPairs(chars);
        }

        // 토큰 끝에 </w>를 붙여 vocab에서 인식되도록
        if (chars.size() > 0) {
            chars.set(chars.size() - 1, chars.get(chars.size() - 1) + "</w>");
        }

        return chars;
    }

    private List<String> getPairs(List<String> chars) {
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < chars.size() - 1; i++) {
            pairs.add(chars.get(i) + " " + chars.get(i + 1));
        }
        return pairs;
    }

    private String getLowestRankPair(List<String> pairs) {
        int minRank = Integer.MAX_VALUE;
        String best = null;
        for (String pair : pairs) {
            Integer rank = mergeRanks.get(pair);
            if (rank != null && rank < minRank) {
                minRank = rank;
                best = pair;
            }
        }
        return best;
    }

    private int indexOfPair(List<String> list, String a, String b, int start) {
        for (int i = start; i < list.size() - 1; i++) {
            if (list.get(i).equals(a) && list.get(i + 1).equals(b)) {
                return i;
            }
        }
        return -1;
    }
}

