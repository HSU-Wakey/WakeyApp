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
import java.util.Map;

/**
 * 타임라인 항목 데이터 모델
 */
public class TimelineItem implements Parcelable, Serializable {
    private static final long serialVersionUID = 1L; // Serializable 버전 관리용

    private Date time;               // 시간
    private String location;         // 위치 (주소)
    private String placeName;        // 장소명
    private String photoPath;        // 사진 경로
    private transient LatLng latLng; // 위도/경도 (Serializable 아님, transient 처리)
    private String description;      // 설명
    private String caption;          // 캡션 (BLIP 모델로 생성)
    private String story;            // 스토리 (새로 추가)
    private String detectedObjects;  // 탐지된 객체 목록 (문자열)
    private String activityType;     // 활동 유형
    private float placeProbability;  // 장소 확률
    private List<String> nearbyPOIs = new ArrayList<>(); // 주변 관심장소 목록

    // 두 버전의 코드 호환을 위해 두 타입 모두 유지
    private transient Map<String, Float> detectedObjectMap; // Map 형태의 객체-신뢰도 쌍
    private List<Pair<String, Float>> detectedObjectPairs; // List<Pair> 형태의 객체-신뢰도 쌍

    // LatLng를 대체할 수 있는 직렬화 가능한 필드
    private double latitude;
    private double longitude;

    // 기본 생성자
    public TimelineItem(Date time, String location, String photoPath, LatLng latLng, String description) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
        this.nearbyPOIs = new ArrayList<>();

        // LatLng가 있으면 위도/경도 별도 저장
        if (latLng != null) {
            this.latitude = latLng.latitude;
            this.longitude = latLng.longitude;
        }
    }

    // Pair 형태의 객체 목록을 사용하는 생성자
    public TimelineItem(Date time, String location, String photoPath, LatLng latLng,
                        String description, List<Pair<String, Float>> detectedObjectPairs) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
        this.detectedObjectPairs = detectedObjectPairs != null ? detectedObjectPairs : new ArrayList<>();
        this.nearbyPOIs = new ArrayList<>();

        // LatLng가 있으면 위도/경도 별도 저장
        if (latLng != null) {
            this.latitude = latLng.latitude;
            this.longitude = latLng.longitude;
        }
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
        this.nearbyPOIs = new ArrayList<>();

        // LatLng가 있으면 위도/경도 별도 저장
        if (latLng != null) {
            this.latitude = latLng.latitude;
            this.longitude = latLng.longitude;
        }
    }

    // 빌더 패턴을 사용한 생성자
    private TimelineItem(Builder builder) {
        this.time = builder.time;
        this.location = builder.location;
        this.placeName = builder.placeName;
        this.photoPath = builder.photoPath;
        this.latLng = builder.latLng;
        this.description = builder.description;
        this.caption = builder.caption;
        this.story = builder.story;
        this.detectedObjects = builder.detectedObjects;
        this.detectedObjectMap = builder.detectedObjectMap;
        this.detectedObjectPairs = builder.detectedObjectPairs != null ? builder.detectedObjectPairs : new ArrayList<>();
        this.activityType = builder.activityType;
        this.placeProbability = builder.placeProbability;
        this.nearbyPOIs = builder.nearbyPOIs != null ? builder.nearbyPOIs : new ArrayList<>();

        // LatLng가 있으면 위도/경도 별도 저장
        if (builder.latLng != null) {
            this.latitude = builder.latLng.latitude;
            this.longitude = builder.latLng.longitude;
        }
    }

    // Parcelable 구현
    protected TimelineItem(Parcel in) {
        // Date 읽기
        long timeMillis = in.readLong();
        time = timeMillis != -1 ? new Date(timeMillis) : null;

        location = in.readString();
        placeName = in.readString();
        photoPath = in.readString();
        description = in.readString();
        caption = in.readString();
        story = in.readString();
        detectedObjects = in.readString();
        activityType = in.readString();
        placeProbability = in.readFloat();
        latitude = in.readDouble();
        longitude = in.readDouble();

        // LatLng 복원
        if (latitude != 0 || longitude != 0) {
            latLng = new LatLng(latitude, longitude);
        }

        // List 읽기
        nearbyPOIs = in.createStringArrayList();

        // detectedObjectPairs 읽기
        int pairsSize = in.readInt();
        if (pairsSize > 0) {
            detectedObjectPairs = new ArrayList<>();
            for (int i = 0; i < pairsSize; i++) {
                String key = in.readString();
                Float value = in.readFloat();
                detectedObjectPairs.add(new Pair<>(key, value));
            }
        } else {
            detectedObjectPairs = new ArrayList<>();
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // Date 쓰기
        dest.writeLong(time != null ? time.getTime() : -1);

        dest.writeString(location);
        dest.writeString(placeName);
        dest.writeString(photoPath);
        dest.writeString(description);
        dest.writeString(caption);
        dest.writeString(story);
        dest.writeString(detectedObjects);
        dest.writeString(activityType);
        dest.writeFloat(placeProbability);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);

        // List 쓰기
        dest.writeStringList(nearbyPOIs != null ? nearbyPOIs : new ArrayList<>());

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

    public static final Creator<TimelineItem> CREATOR = new Creator<TimelineItem>() {
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

    public String getPlaceName() {
        return placeName;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public LatLng getLatLng() {
        // 직렬화/역직렬화 과정에서 latLng이 null이 되었다면 위도/경도로 재생성
        if (latLng == null && (latitude != 0 || longitude != 0)) {
            latLng = new LatLng(latitude, longitude);
        }
        return latLng;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getDescription() {
        return description;
    }

    public String getCaption() {
        return caption;
    }

    public String getStory() {
        return story;
    }

    public String getDetectedObjects() {
        return detectedObjects;
    }

    public List<Pair<String, Float>> getDetectedObjectPairs() {
        return detectedObjectPairs;
    }

    public Map<String, Float> getDetectedObjectMap() {
        return detectedObjectMap;
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

    // Setters
    public void setTime(Date time) {
        this.time = time;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
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

    public void setLatitude(double latitude) {
        this.latitude = latitude;
        updateLatLng();
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
        updateLatLng();
    }

    // 위도/경도 변경 시 LatLng 업데이트 헬퍼 메서드
    private void updateLatLng() {
        if (latitude != 0 || longitude != 0) {
            this.latLng = new LatLng(latitude, longitude);
        }
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setStory(String story) {
        this.story = story;
    }

    public void setDetectedObjects(String detectedObjects) {
        this.detectedObjects = detectedObjects;
    }

    public void setDetectedObjectPairs(List<Pair<String, Float>> detectedObjectPairs) {
        this.detectedObjectPairs = detectedObjectPairs;
    }

    public void setDetectedObjectMap(Map<String, Float> detectedObjectMap) {
        this.detectedObjectMap = detectedObjectMap;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public void setPlaceProbability(float placeProbability) {
        this.placeProbability = placeProbability;
    }

    public void setNearbyPOIs(List<String> nearbyPOIs) {
        this.nearbyPOIs = nearbyPOIs;
    }

    // 빌더 클래스
    public static class Builder {
        private Date time;
        private String location;
        private String placeName;
        private String photoPath;
        private LatLng latLng;
        private String description;
        private String caption;
        private String story;
        private String detectedObjects;
        private Map<String, Float> detectedObjectMap;
        private List<Pair<String, Float>> detectedObjectPairs;
        private String activityType;
        private float placeProbability;
        private List<String> nearbyPOIs = new ArrayList<>();

        public Builder() {
        }

        public Builder setTime(Date time) {
            this.time = time;
            return this;
        }

        public Builder setLocation(String location) {
            this.location = location;
            return this;
        }

        public Builder setPlaceName(String placeName) {
            this.placeName = placeName;
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

        public Builder setCaption(String caption) {
            this.caption = caption;
            return this;
        }

        public Builder setStory(String story) {
            this.story = story;
            return this;
        }

        public Builder setDetectedObjects(String detectedObjects) {
            this.detectedObjects = detectedObjects;
            return this;
        }

        public Builder setDetectedObjectMap(Map<String, Float> detectedObjectMap) {
            this.detectedObjectMap = detectedObjectMap;
            return this;
        }

        public Builder setDetectedObjectPairs(List<Pair<String, Float>> detectedObjectPairs) {
            this.detectedObjectPairs = detectedObjectPairs;
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
            TimelineItem item = new TimelineItem(this);
            // issue/WA-74 브랜치에서의 중요 로직을 통합:
            // detectedObjectPairs가 설정된 경우 별도 처리 - WA-74 브랜치 로직 통합
            if (detectedObjectPairs != null) {
                item.setDetectedObjectPairs(detectedObjectPairs);  // 🔥 이 줄이 반드시 필요!
            }
            return item;
        }
    }
}