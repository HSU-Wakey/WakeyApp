// data/model/PhotoInfo.java
package com.example.wakey.data.model;

import com.google.android.gms.maps.model.LatLng;
import java.util.Date;

public class PhotoInfo {
    private String filePath;
    private Date dateTaken;
    private LatLng latLng;
    private String deviceModel;
    private float focalLength;
    private String lensModel;
    private boolean hasFlash;
    private float aperture;
    private String placeName;
    private String placeId; // Google Places API 장소 ID

    // 생성자
    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
    }

    // 확장 생성자
    public PhotoInfo(String filePath, Date dateTaken, LatLng latLng,
                     String deviceModel, float focalLength, String lensModel,
                     boolean hasFlash, float aperture) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.latLng = latLng;
        this.deviceModel = deviceModel;
        this.focalLength = focalLength;
        this.lensModel = lensModel;
        this.hasFlash = hasFlash;
        this.aperture = aperture;
    }

    // Getters
    public String getFilePath() {
        return filePath;
    }

    public Date getDateTaken() {
        return dateTaken;
    }

    public LatLng getLatLng() {
        return latLng;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public float getFocalLength() {
        return focalLength;
    }

    public String getLensModel() {
        return lensModel;
    }

    public boolean hasFlash() {
        return hasFlash;
    }

    public float getAperture() {
        return aperture;
    }

    public String getPlaceName() {
        return placeName;
    }

    public String getPlaceId() {
        return placeId;
    }

    // Setters
    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }
}