package com.example.wakey.data.local;

import android.util.Pair;
import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class Converters {
    private static final Gson gson = new Gson();

    // String to List<Pair<String, Float>> 변환
    @TypeConverter
    public static List<Pair<String, Float>> toPairList(String value) {
        if (value == null) {
            return new ArrayList<>(); // null 대신 빈 리스트 반환
        }

        Type listType = new TypeToken<List<PairData>>() {}.getType();
        List<PairData> dataList = gson.fromJson(value, listType);

        if (dataList == null) {
            return new ArrayList<>(); // null 체크 추가
        }

        List<Pair<String, Float>> result = new ArrayList<>();
        for (PairData data : dataList) {
            result.add(new Pair<>(data.first, data.second));
        }
        return result;
    }

    // List<Pair<String, Float>> to String 변환
    @TypeConverter
    public static String fromPairList(List<Pair<String, Float>> list) {
        if (list == null) {
            return null; // null 체크 추가
        }

        List<PairData> dataList = new ArrayList<>();
        for (Pair<String, Float> pair : list) {
            dataList.add(new PairData(pair.first, pair.second));
        }
        return gson.toJson(dataList);
    }

    // JSON 직렬화를 위한 도우미 클래스
    private static class PairData {
        String first;
        Float second;

        PairData(String first, Float second) {
            this.first = first;
            this.second = second;
        }
    }
}