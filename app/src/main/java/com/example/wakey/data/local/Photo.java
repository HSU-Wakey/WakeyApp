package com.example.wakey.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "Photo")
public class Photo {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String filePath;
    public String dateTaken;
    public String locationDo;
    public String locationSi;
    public String locationGu;
    public String locationStreet;
    public Double latitude;
    public Double longitude;
    public String detectedObjects;
    public String caption;

    public Photo(String filePath, String dateTaken, String locationDo, String locationSi,
                 String locationGu, String locationStreet, Double latitude, Double longitude,
                 String detectedObjects, String caption) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.locationDo = locationDo;
        this.locationSi = locationSi;
        this.locationGu = locationGu;
        this.locationStreet = locationStreet;
        this.latitude = latitude;
        this.longitude = longitude;
        this.detectedObjects = detectedObjects;
        this.caption = caption;
    }
}
