package com.example.wakey.data.local;

import android.util.Pair;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.List;

@Entity
public class Photo {

    @TypeConverters(Converters.class)
    @ColumnInfo(name = "detectedObjectPairs")
    public List<Pair<String, Float>> detectedObjectPairs;

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "filePath")
    public String filePath;

    @ColumnInfo(name = "dateTaken")
    public String dateTaken;

    @ColumnInfo(name = "latitude")
    public Double latitude;

    @ColumnInfo(name = "longitude")
    public Double longitude;

    @ColumnInfo(name = "locationDo")
    public String locationDo;

    @ColumnInfo(name = "locationSi")
    public String locationSi;

    @ColumnInfo(name = "locationGu")
    public String locationGu;

    @ColumnInfo(name = "locationStreet")
    public String locationStreet;

    @ColumnInfo(name = "caption")
    public String caption;

    @ColumnInfo(name = "detectedObjects")
    public String detectedObjects;

    @Ignore
    public Photo(String filePath, String dateTaken, String locationDo, String locationSi,
                 String locationGu, String locationStreet, String caption,
                 Double latitude, Double longitude, String detectedObjects) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.locationDo = locationDo;
        this.locationSi = locationSi;
        this.locationGu = locationGu;
        this.locationStreet = locationStreet;
        this.caption = caption;
        this.latitude = latitude;
        this.longitude = longitude;
        this.detectedObjects = detectedObjects;
    }

    // Room을 위한 기본 생성자
    public Photo() {}
}
