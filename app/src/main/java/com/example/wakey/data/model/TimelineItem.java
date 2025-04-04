package com.example.wakey.data.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;

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
    private transient Map<String, Float> detectedObjectPairs; // 객체-신뢰도 쌍 (Map 형태)
    private transient List<Pair<String, Float>> detectedObjectPairsList; // 객체-신뢰도 쌍 (List<Pair> 형태)
    private String activityType;     // 활동 유형
    private float placeProbability;  // 장소 확률
    private List<String> nearbyPOIs; // 주변 관심장소 목록

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

    // 마스터 브랜치의 확장된 생성자 통합
    public TimelineItem(Date time, String location, String photoPath, LatLng latLng,
                        String description, String activityType, List<Pair<String, Float>> detectedObjectPairsList) {
        this.time = time;
        this.location = location;
        this.photoPath = photoPath;
        this.latLng = latLng;
        this.description = description;
        this.activityType = activityType;
        this.detectedObjectPairsList = detectedObjectPairsList;
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
        this.photoPath = builder.photoPath;
        this.latLng = builder.latLng;
        this.description = builder.description;
        this.caption = builder.caption;
        this.story = builder.story;
        this.detectedObjects = builder.detectedObjects;
        this.detectedObjectPairs = builder.detectedObjectPairs;
        this.detectedObjectPairsList = builder.detectedObjectPairsList;
        this.activityType = builder.activityType;
        this.placeProbability = builder.placeProbability;
        this.nearbyPOIs = builder.nearbyPOIs;
        this.placeName = builder.placeName;

        // LatLng가 있으면 위도/경도 별도 저장
        if (builder.latLng != null) {
            this.latitude = builder.latLng.latitude;
            this.longitude = builder.latLng.longitude;
        }
    }

    // 빌더 클래스
    public static class Builder implements Serializable {
        private Date time;
        private String location;
        private String placeName;
        private String photoPath;
        private LatLng latLng;
        private String description;
        private String caption;
        private String story;
        private String detectedObjects;
        private Map<String, Float> detectedObjectPairs;
        private List<Pair<String, Float>> detectedObjectPairsList;
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

        public Builder setDetectedObjectPairs(Map<String, Float> detectedObjectPairs) {
            this.detectedObjectPairs = detectedObjectPairs;
            return this;
        }

        public Builder setDetectedObjectPairsList(List<Pair<String, Float>> detectedObjectPairsList) {
            this.detectedObjectPairsList = detectedObjectPairsList;
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
            return new TimelineItem(this);
        }
    }

    // 게터/세터 메서드들
    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public LatLng getLatLng() {
        // 직렬화/역직렬화 과정에서 latLng이 null이 되었다면 위도/경도로 재생성
        if (latLng == null && (latitude != 0 || longitude != 0)) {
            latLng = new LatLng(latitude, longitude);
        }
        return latLng;
    }

    public void setLatLng(LatLng latLng) {
        this.latLng = latLng;
        if (latLng != null) {
            this.latitude = latLng.latitude;
            this.longitude = latLng.longitude;
        }
    }

    // 위도 게터/세터
    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
        updateLatLng();
    }

    // 경도 게터/세터
    public double getLongitude() {
        return longitude;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getStory() {
        return story;
    }

    public void setStory(String story) {
        this.story = story;
    }

    public String getDetectedObjects() {
        return detectedObjects;
    }

    public void setDetectedObjects(String detectedObjects) {
        this.detectedObjects = detectedObjects;
    }

    public Map<String, Float> getDetectedObjectPairs() {
        return detectedObjectPairs;
    }

    public void setDetectedObjectPairs(Map<String, Float> detectedObjectPairs) {
        this.detectedObjectPairs = detectedObjectPairs;
    }

    // 마스터 브랜치의 List<Pair> 형태의 detectedObjectPairs 게터/세터 추가
    public List<Pair<String, Float>> getDetectedObjectPairsList() {
        return detectedObjectPairsList;
    }

    public void setDetectedObjectPairsList(List<Pair<String, Float>> detectedObjectPairsList) {
        this.detectedObjectPairsList = detectedObjectPairsList;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public float getPlaceProbability() {
        return placeProbability;
    }

    public void setPlaceProbability(float placeProbability) {
        this.placeProbability = placeProbability;
    }

    public List<String> getNearbyPOIs() {
        return nearbyPOIs;
    }

    public void setNearbyPOIs(List<String> nearbyPOIs) {
        this.nearbyPOIs = nearbyPOIs;
    }

    // Parcelable 구현
    protected TimelineItem(Parcel in) {
        long tmpTime = in.readLong();
        time = tmpTime != -1 ? new Date(tmpTime) : null;
        location = in.readString();
        placeName = in.readString();
        photoPath = in.readString();
        description = in.readString();
        caption = in.readString();
        story = in.readString();
        detectedObjects = in.readString();
        activityType = in.readString();
        placeProbability = in.readFloat();
        nearbyPOIs = in.createStringArrayList();

        latitude = in.readDouble();
        longitude = in.readDouble();
        if (latitude != 0 || longitude != 0) {
            latLng = new LatLng(latitude, longitude);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
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
        dest.writeStringList(nearbyPOIs);

        if (latLng != null) {
            dest.writeDouble(latLng.latitude);
            dest.writeDouble(latLng.longitude);
        } else {
            dest.writeDouble(latitude);
            dest.writeDouble(longitude);
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
}