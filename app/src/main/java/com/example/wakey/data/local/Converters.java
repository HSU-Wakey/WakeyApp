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
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<Pair<String, Float>> toPairList(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<List<Pair<String, Float>>>() {}.getType();
        return gson.fromJson(json, type);
    }
}
