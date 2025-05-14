package com.example.wakey.data.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TimelineItem implements Parcelable {
    private Date time;
    private String location;
    private String photoPath;
    private LatLng latLng;
    private String description;
    private double latitude;
    private double longitude;

    private String activityType;      // 활동 유형 (식사, 관광 등)
    private float placeProbability;   // 장소 확률
    private List<String> nearbyPOIs = new ArrayList<>();  // 주변 POI
    private List<String> detectedObjects = new ArrayList<>();  // 감지된 객체들
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
        this.detectedObjectPairs = detectedObjectPairs != null ? detectedObjectPairs : new ArrayList<>();
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
        this.detectedObjectPairs = detectedObjectPairs != null ? detectedObjectPairs : new ArrayList<>();
    }

    // Parcelable 구현
    protected TimelineItem(Parcel in) {
        // Date 읽기
        long timeMillis = in.readLong();
        time = new Date(timeMillis);

        location = in.readString();
        photoPath = in.readString();
        description = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
        activityType = in.readString();
        placeProbability = in.readFloat();

        // LatLng 읽기
        if (in.readByte() == 1) {
            double lat = in.readDouble();
            double lng = in.readDouble();
            latLng = new LatLng(lat, lng);
        }

        // List 읽기 - 새로운 ArrayList 생성
        nearbyPOIs = new ArrayList<>();
        in.readStringList(nearbyPOIs);

        detectedObjects = new ArrayList<>();
        in.readStringList(detectedObjects);

        // detectedObjectPairs 읽기
        int pairsSize = in.readInt();
        detectedObjectPairs = new ArrayList<>();
        for (int i = 0; i < pairsSize; i++) {
            String key = in.readString();
            Float value = in.readFloat();
            detectedObjectPairs.add(new Pair<>(key, value));
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // Date 쓰기
        dest.writeLong(time != null ? time.getTime() : 0);

        dest.writeString(location);
        dest.writeString(photoPath);
        dest.writeString(description);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeString(activityType);
        dest.writeFloat(placeProbability);

        // LatLng 쓰기
        if (latLng != null) {
            dest.writeByte((byte) 1);
            dest.writeDouble(latLng.latitude);
            dest.writeDouble(latLng.longitude);
        } else {
            dest.writeByte((byte) 0);
        }

        // List 쓰기
        dest.writeStringList(nearbyPOIs != null ? nearbyPOIs : new ArrayList<>());
        dest.writeStringList(detectedObjects != null ? detectedObjects : new ArrayList<>());

        // detectedObjectPairs 쓰기
        if (detectedObjectPairs != null) {
            dest.writeInt(detectedObjectPairs.size());
            for (Pair<String, Float> pair : detectedObjectPairs) {
                dest.writeString(pair.first);
                dest.writeFloat(pair.second);
            }
        } else {
            dest.writeInt(0); // null인 경우 크기 0으로 처리
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TimelineItem> CREATOR = new Parcelable.Creator<TimelineItem>() {
        @Override
        public TimelineItem createFromParcel(Parcel in) {
            return new TimelineItem(in);
        }

        @Override
        public TimelineItem[] newArray(int size) {
            return new TimelineItem[size];
        }
    };

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

//    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
//        out.defaultWriteObject();
//
//        // 직렬화하기 전에 새로운 ArrayList로 복사
//        ArrayList<Pair<String, Float>> safePairs = new ArrayList<>(detectedObjectPairs);
//        out.writeObject(safePairs);
//    }
//
//    private void readObject(java.io.ObjectInputStream in)
//            throws java.io.IOException, ClassNotFoundException {
//        in.defaultReadObject();
//
//        // ArrayList로 읽어들이기
//        @SuppressWarnings("unchecked")
//        ArrayList<Pair<String, Float>> safePairs =
//                (ArrayList<Pair<String, Float>>) in.readObject();
//        this.detectedObjectPairs = new ArrayList<>(safePairs);
//    }
}