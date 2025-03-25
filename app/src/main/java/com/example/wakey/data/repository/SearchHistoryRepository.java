// data/repository/SearchHistoryRepository.java
package com.example.wakey.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.wakey.data.model.SearchHistoryItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SearchHistoryRepository {
    private static final String PREFS_NAME = "wakey_search_history";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final int MAX_HISTORY_ITEMS = 10;

    private static SearchHistoryRepository instance;
    private final SharedPreferences preferences;
    private final Gson gson;

    private SearchHistoryRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static SearchHistoryRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SearchHistoryRepository(context.getApplicationContext());
        }
        return instance;
    }

    public List<SearchHistoryItem> getSearchHistory() {
        String json = preferences.getString(KEY_SEARCH_HISTORY, null);
        if (json == null) {
            return new ArrayList<>();
        }

        Type type = new TypeToken<ArrayList<SearchHistoryItem>>() {}.getType();
        List<SearchHistoryItem> history = gson.fromJson(json, type);

        // 최신순으로 정렬
        Collections.sort(history, (o1, o2) -> Long.compare(o2.getTimestamp(), o1.getTimestamp()));

        return history;
    }

    public void addSearchHistory(SearchHistoryItem item) {
        List<SearchHistoryItem> history = getSearchHistory();

        // 중복 항목 제거
        history.removeIf(existingItem -> existingItem.getQuery().equals(item.getQuery()));

        // 새 항목 추가
        history.add(0, item);

        // 최대 항목 수 유지
        if (history.size() > MAX_HISTORY_ITEMS) {
            history = history.subList(0, MAX_HISTORY_ITEMS);
        }

        // 저장
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_SEARCH_HISTORY, gson.toJson(history));
        editor.apply();
    }

    public void clearSearchHistory() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_SEARCH_HISTORY);
        editor.apply();
    }
}