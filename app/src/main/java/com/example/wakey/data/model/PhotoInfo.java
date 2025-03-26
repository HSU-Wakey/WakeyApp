package com.example.wakey.data.model;

import com.google.android.gms.maps.model.LatLng;

import java.util.Date;

public class PhotoInfo {
    private String filePath;
    private Date dateTaken;
    private LatLng latLng;
    private String placeId;
    private String placeName;

    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
    }

    // 위치 정보가 없는 경우를 위한 생성자
    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng, String placeId, String placeName) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
        this.placeId = placeId;
        this.placeName = placeName;
    }

    // Getters and Setters
    public String getFilePath() { return filePath; }
    public Date getDateTaken() { return dateTaken; }
    public LatLng getLatLng() { return latLng; }
    public String getPlaceId() { return placeId; }
    public String getPlaceName() { return placeName; }
    public void setPlaceId(String placeId) { this.placeId = placeId; }
    public void setPlaceName(String placeName) { this.placeName = placeName; }
}