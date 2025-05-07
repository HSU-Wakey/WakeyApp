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

    @TypeConverter
    public static String fromPairList(List<Pair<String, Float>> list) {
        if (list == null) return null;

        List<CustomPair> customList = new ArrayList<>();
        for (Pair<String, Float> pair : list) {
            customList.add(new CustomPair(pair.first, pair.second));
        }
        return gson.toJson(customList);
    }

    @TypeConverter
    public static List<Pair<String, Float>> toPairList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();

        Type listType = new TypeToken<List<CustomPair>>() {}.getType();
        List<CustomPair> customList = gson.fromJson(json, listType);

        List<Pair<String, Float>> result = new ArrayList<>();
        for (CustomPair custom : customList) {
            result.add(new Pair<>(custom.first, custom.second));
        }
        return result;
    }
}
