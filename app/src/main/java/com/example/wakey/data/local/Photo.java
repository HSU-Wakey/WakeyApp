package com.example.wakey.data.local;

import android.util.Pair;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Entity
public class Photo {

//    @TypeConverters(Converters.class)
//    @ColumnInfo(name = "detectedObjectPairs")
//    public List<Pair<String, Float>> detectedObjectPairs;

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "filePath")
    public String filePath;

    @ColumnInfo(name = "dateTaken")
    public String dateTaken;

    // 타임스탬프 필드 추가
    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "latitude")
    public Double latitude;

    @ColumnInfo(name = "longitude")
    public Double longitude;

    @ColumnInfo(name = "locationDo")
    public String locationDo;

    @ColumnInfo(name = "locationSi")
    public String locationSi;

    @ColumnInfo(name = "locationGu")
    public String locationGu;

    @ColumnInfo(name = "locationStreet")
    public String locationStreet;

    @ColumnInfo(name = "fullAddress")
    public String fullAddress;

    @ColumnInfo(name = "caption")
    public String caption;

    @ColumnInfo(name = "detectedObjects")
    public String detectedObjects;

    @ColumnInfo(name = "country")
    public String country; // 예: "일본", "미국"

    // ✅ List<Pair<String, Float>>로 변경
    @TypeConverters(Converters.class)
    @ColumnInfo(name = "detectedObjectPairs")
    public List<Pair<String, Float>> detectedObjectPairs;

    // ⭐ 벡터 문자열 저장용 필드
    @ColumnInfo(name = "embedding_vector_str")
    public String embeddingVectorStr;

    // 해시태그 컬럼 추가
    @ColumnInfo(name = "hashtags")
    public String hashtags;

    // ⭐ 실제 사용할 float[] 배열 (Room 저장 제외)
    @Ignore
    public float[] embeddingVector;

    // ✅ 생성자 추가 (Room이 무시하도록 @Ignore 붙이기)
    @Ignore
    public Photo(String filePath,
                 String dateTaken,
                 String locationDo,
                 String locationSi,
                 String locationGu,
                 String locationStreet,
                 String caption,
                 Double latitude,
                 Double longitude,
                 String detectedObjects) {
        this.filePath = filePath;
        this.dateTaken = dateTaken;
        this.locationDo = locationDo;
        this.locationSi = locationSi;
        this.locationGu = locationGu;
        this.locationStreet = locationStreet;
        this.caption = caption;
        this.latitude = latitude;
        this.longitude = longitude;
        this.detectedObjects = detectedObjects;

        // dateTaken이 있으면 timestamp 생성
        this.timestamp = parseTimestampFromDateTaken(dateTaken);
    }

    public String getFilePath() {
        return filePath;
    }

    public List<Pair<String, Float>> getDetectedObjectPairs() {
        return detectedObjectPairs;
    }

    public void setDetectedObjectPairs(List<Pair<String, Float>> detectedObjectPairs) {
        this.detectedObjectPairs = detectedObjectPairs;
    }

    public void setEmbeddingVector(float[] vector) {
        this.embeddingVector = vector;
        this.embeddingVectorStr = vectorToString(vector);
    }

    public String getEmbeddingVectorStr() {
        return embeddingVectorStr;
    }

    public float[] getEmbeddingVector() {
        if (embeddingVector == null && embeddingVectorStr != null) {
            embeddingVector = stringToVector(embeddingVectorStr);
        }
        return embeddingVector;
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (float v : vector) {
            sb.append(v).append(",");
        }
        return sb.toString();
    }

    private float[] stringToVector(String str) {
        String[] parts = str.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i]);
        }
        return vec;
    }

    // getTags 메소드 추가 - 해시태그 문자열을 태그 리스트로 변환
    public List<String> getTags() {
        List<String> tagList = new ArrayList<>();
        if (hashtags != null && !hashtags.isEmpty()) {
            String[] tags = hashtags.split(",");
            for (String tag : tags) {
                String trimmedTag = tag.trim();
                if (!trimmedTag.isEmpty()) {
                    tagList.add(trimmedTag);
                }
            }
        }
        return tagList;
    }

    // 날짜 문자열에서 타임스탬프 추출 (다양한 형식 지원)
    private long parseTimestampFromDateTaken(String dateTakenStr) {
        if (dateTakenStr == null || dateTakenStr.isEmpty()) {
            return System.currentTimeMillis();
        }

        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy:MM:dd HH:mm:ss",
                "yyyy-MM-dd",
                "yyyy:MM:dd"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                Date date = sdf.parse(dateTakenStr);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException e) {
                // 이 형식으로 파싱 실패, 다음 형식 시도
            }
        }

        // 모든 파싱 실패 시 현재 시간 반환
        return System.currentTimeMillis();
    }

    // getTimestamp() 메소드 추가
    public long getTimestamp() {
        // timestamp가 0이면 dateTaken에서 파싱 시도
        if (timestamp == 0 && dateTaken != null && !dateTaken.isEmpty()) {
            timestamp = parseTimestampFromDateTaken(dateTaken);
        }

        // 여전히 0이면 현재 시간 반환
        return timestamp == 0 ? System.currentTimeMillis() : timestamp;
    }

    // Room을 위한 기본 생성자
    public Photo() {}
}
