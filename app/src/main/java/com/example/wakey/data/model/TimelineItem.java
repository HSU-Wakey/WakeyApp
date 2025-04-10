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
    private float placeProbability;   // 장소 확률 점수
    private List<String> nearbyPOIs;  // 주변 관심 장소

    private List<String> detectedObjects;

    private List<Pair<String, Float>> detectedObjectPairs = new ArrayList<>();

    public List<Pair<String, Float>> getDetectedObjectPairs() {
        return detectedObjectPairs;
    }

    public void setDetectedObjectPairs(List<Pair<String, Float>> pairs) {
        this.detectedObjectPairs = pairs;
    }


    // 생성자
    public TimelineItem(Date time, String location, String photoPath, LatLng latLng,
                        String description) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
        this.nearbyPOIs = new ArrayList<>();
        this.detectedObjects = new ArrayList<>();
    }

    // 확장된 생성자
    public TimelineItem(Date time, String location, String photoPath, LatLng latLng,
                        String description, String activityType, float placeProbability,
                        List<String> nearbyPOIs) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
        this.activityType = activityType;
        this.placeProbability = placeProbability;
        this.nearbyPOIs = nearbyPOIs != null ? nearbyPOIs : new ArrayList<>();
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

    public float getPlaceProbability() {
        return placeProbability;
    }

    public List<String> getNearbyPOIs() {
        return nearbyPOIs;
    }

    public List<String> getDetectedObjects() {
        return detectedObjects;
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

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public void setPlaceProbability(float placeProbability) {
        this.placeProbability = placeProbability;
    }

    public void setDetectedObjects(List<String> detectedObjects) {
        this.detectedObjects = detectedObjects;
    }


    public void setNearbyPOIs(List<String> nearbyPOIs) {
        this.nearbyPOIs = nearbyPOIs;
    }

    public void addNearbyPOI(String poi) {
        if (this.nearbyPOIs == null) {
            this.nearbyPOIs = new ArrayList<>();
        }
        this.nearbyPOIs.add(poi);
    }

    // 활동 유형을 결정하는 편의 메소드
    public static class Builder {
        private Date time;
        private String location;
        private String photoPath;
        private LatLng latLng;
        private String description;
        private String activityType;
        private float placeProbability = 0.0f;
        private List<String> nearbyPOIs;

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

        public Builder setPlaceProbability(float placeProbability) {
            this.placeProbability = placeProbability;
            return this;
        }

        public Builder setNearbyPOIs(List<String> nearbyPOIs) {
            this.nearbyPOIs = nearbyPOIs;
            return this;
        }

        public TimelineItem build() {
            return new TimelineItem(time, location, photoPath, latLng, description,
                    activityType, placeProbability, nearbyPOIs);
        }
    }
}