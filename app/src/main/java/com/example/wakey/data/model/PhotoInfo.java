package com.example.wakey.data.model;

import android.util.Pair;
import com.google.android.gms.maps.model.LatLng;

import java.util.Date;
import java.util.List;

public class PhotoInfo {
    private String filePath;
    private Date dateTaken;
    private LatLng latLng;

    private String placeId;
    private String placeName;
    private String address; // 전체 주소
    private String description;
    private List<String> objects;
    private String locationDo;
    private String locationGu;
    private String locationStreet;
    // ✅ 추가: detectedObjectPairs 필드
    private List<Pair<String, Float>> detectedObjectPairs;

    // ✅ 전체 필드 포함 생성자
    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng,
                     String placeId, String placeName,
                     String address, String description,
                     List<String> objects) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
        this.placeId = placeId;
        this.placeName = placeName;
        this.address = address;
        this.description = description;
        this.objects = objects;
    }

    // ✅ detectedObjectPairs 매개변수를 포함하는 새 생성자
    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng,
                     String placeId, String placeName,
                     String address, String description,
                     List<String> objects,
                     List<Pair<String, Float>> detectedObjectPairs) {
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

    // 필요한 경우만 사용하는 생성자들
    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
    }

    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng, String placeId, String placeName) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
        this.placeId = placeId;
        this.placeName = placeName;
    }

    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng, String placeId,
                     String description, List<String> objects) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
        this.placeId = placeId;
        this.description = description;
        this.objects = objects;
    }

    // ✅ Getter
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

    // ✅ 추가된 getter
    public List<Pair<String, Float>> getDetectedObjectPairs() {
        return detectedObjectPairs;
    }

    // ✅ Setter
    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

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

    // ✅ 추가된 setter
    public void setDetectedObjectPairs(List<Pair<String, Float>> detectedObjectPairs) {
        this.detectedObjectPairs = detectedObjectPairs;
    }
}