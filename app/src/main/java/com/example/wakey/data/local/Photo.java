package com.example.wakey.data.local;

import android.util.Pair;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.util.List;

@Entity
public class Photo {

    @TypeConverters(Converters.class)
    @ColumnInfo(name = "detectedObjectPairs")
    public List<Pair<String, Float>> detectedObjectPairs;

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "filePath")
    public String filePath;

    @ColumnInfo(name = "dateTaken")
    public String dateTaken;

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

    // ⭐ 벡터 문자열 저장용 필드
    @ColumnInfo(name = "embedding_vector_str")
    public String embeddingVectorStr;

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
    }

    public String getFilePath() {
        return filePath;
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


    // Room을 위한 기본 생성자
    public Photo() {}
}
