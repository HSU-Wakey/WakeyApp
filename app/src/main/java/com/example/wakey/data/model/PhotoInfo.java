package com.example.wakey.data.model;

import com.google.android.gms.maps.model.LatLng;

import android.util.Pair;

import java.util.Date;
import java.util.List;

public class PhotoInfo {
    private String filePath;
    private Date dateTaken;
    private LatLng latLng;

    private String placeId;
    private String placeName;
    private String address;
    private String description;

    private List<String> objects;
    private String locationDo;
    private String locationGu;
    private String locationStreet;

    private List<Pair<String, Float>> detectedObjectPairs; // ✅ 수정됨

    // ✅ 전체 필드 포함 생성자
    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng,
                     String placeId, String placeName,
                     String address, String description,
                     List<String> objects, List<Pair<String, Float>> detectedObjectPairs) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
        this.placeId = placeId;
        this.placeName = placeName;
        this.address = address;
        this.description = description;
        this.objects = objects;
        this.detectedObjectPairs = detectedObjectPairs;
    }

    // ✅ Getters
    public String getFilePath() {
        return filePath;
    }

    public Date getDateTaken() {
        return dateTaken;
    }

    public LatLng getLatLng() {
        return latLng;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getPlaceName() {
        return placeName;
    }

    public String getAddress() {
        return address;
    }

    public String getLocationDo() {
        return locationDo;
    }

    public String getLocationGu() {
        return locationGu;
    }

    public String getLocationStreet() {
        return locationStreet;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getObjects() {
        return objects;
    }

    public List<Pair<String, Float>> getDetectedObjectPairs() {
        return detectedObjectPairs;
    }

    // ✅ Setters
    public void setAddress(String address) {
        this.address = address;
    }

    public void setLocationDo(String locationDo) {
        this.locationDo = locationDo;
    }

    public void setLocationGu(String locationGu) {
        this.locationGu = locationGu;
    }

    public void setLocationStreet(String locationStreet) {
        this.locationStreet = locationStreet;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setObjects(List<String> objects) {
        this.objects = objects;
    }

    public void setDetectedObjectPairs(List<Pair<String, Float>> detectedObjectPairs) {
        this.detectedObjectPairs = detectedObjectPairs;
    }
}
