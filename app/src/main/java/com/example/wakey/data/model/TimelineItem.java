package com.example.wakey.data.model;

import com.google.android.gms.maps.model.LatLng;
import java.util.Date;

public class TimelineItem {
    private Date time;
    private String location;
    private String photoPath;
    private LatLng latLng;
    private String description;

    public TimelineItem(Date time, String location, String photoPath, LatLng latLng, String description) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
    }

    // Getters
    public Date getTime() {
        return time;
    }

    public String getLocation() {
        return location;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public LatLng getLatLng() {
        return latLng;
    }

    public String getDescription() {
        return description;
    }

    // Setters
    public void setTime(Date time) {
        this.time = time;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public void setLatLng(LatLng latLng) {
        this.latLng = latLng;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}