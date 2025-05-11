package com.example.wakey.data.model;

import android.util.Pair;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TimelineItem implements Serializable {
    private Date time;
    private String location;
    private String photoPath;
    private LatLng latLng;
    private String description;
    private double latitude;
    private double longitude;

    private String activityType;      // 활동 유형 (식사, 관광 등)


    private List<Pair<String, Float>> detectedObjectPairs;

    public List<Pair<String, Float>> getDetectedObjectPairs() {
        return detectedObjectPairs;
    }

    public void setDetectedObjectPairs(List<Pair<String, Float>> pairs) {
        this.detectedObjectPairs = pairs;
    }


    // 생성자
    public TimelineItem(Date time, String location, String photoPath, LatLng latLng,
                        String description, List<Pair<String, Float>> detectedObjectPairs) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
        this.detectedObjectPairs = detectedObjectPairs;
    }

    // 확장된 생성자
    public TimelineItem(Date time, String location, String photoPath, LatLng latLng,
                        String description, String activityType, List<Pair<String, Float>> detectedObjectPairs) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
        this.activityType = activityType;
        this.detectedObjectPairs = detectedObjectPairs;
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
        if (latLng == null && (latitude != 0 || longitude != 0)) {
            latLng = new LatLng(latitude, longitude);
        }
        return latLng;
    }
    public String getDescription() {
        return description;
    }

    public String getActivityType() {
        return activityType;
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
        if (latLng != null) {
            this.latitude = latLng.latitude;
            this.longitude = latLng.longitude;
        }
    }

    public void setDescription(String description) {
        this.description = description;
    }


    // 활동 유형을 결정하는 편의 메소드
    public static class Builder {
        private Date time;
        private String location;
        private String photoPath;
        private LatLng latLng;
        private String description;
        private String activityType;
        private List<Pair<String, Float>> detectedObjectPairs;

        public Builder setTime(Date time) {
            this.time = time;
            return this;
        }

        public Builder setLocation(String location) {
            this.location = location;
            return this;
        }

        public Builder setPhotoPath(String photoPath) {
            this.photoPath = photoPath;
            return this;
        }

        public Builder setLatLng(LatLng latLng) {
            this.latLng = latLng;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setActivityType(String activityType) {
            this.activityType = activityType;
            return this;
        }

        public TimelineItem build() {
            TimelineItem item = new TimelineItem(time, location, photoPath, latLng, description, activityType, detectedObjectPairs);
            item.setDetectedObjectPairs(detectedObjectPairs);  // 🔥 이 줄이 반드시 필요!
            return item;
        }

        public Builder setDetectedObjectPairs(List<Pair<String, Float>> detectedObjectPairs) {
            this.detectedObjectPairs = detectedObjectPairs;
            return this;
        }
    }
}