package com.example.wakey.data.local;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity
public class Photo {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "filePath")
    public String filePath;

    @ColumnInfo(name = "dateTaken")
    public String dateTaken;

    @ColumnInfo(name = "latitude")
    public Double latitude; // ✅ 기본형(double) → 객체형(Double)로 수정

    @ColumnInfo(name = "longitude")
    public Double longitude; // ✅ 동일하게 수정

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

    // ✅ Room이 무시하도록 @Ignore 생성자 정의
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

    // ✅ Room을 위한 기본 생성자 (반드시 있어야 함)
    public Photo() {}
}
