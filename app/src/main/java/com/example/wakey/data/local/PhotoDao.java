package com.example.wakey.data.local;

import android.util.Pair;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PhotoDao {

    // INSERT
    @Insert
    void insertPhoto(Photo photo);

    @Insert
    void insertPhotos(List<Photo> photos);

    // 기본 SELECT
    @Query("SELECT * FROM Photo")
    List<Photo> getAllPhotos();

    // 전체 삭제
    @Query("DELETE FROM Photo")
    void deleteAllPhotos();

    // 중복 제거 쿼리 (선택적으로 사용)
    @Query("DELETE FROM Photo WHERE rowid NOT IN (SELECT MIN(rowid) FROM Photo GROUP BY filePath)")
    void deleteDuplicatePhotos();

    // ⭐️ 객체 인식 결과 있는 사진만
    @Query("SELECT * FROM Photo WHERE detectedObjects IS NOT NULL")
    List<Photo> getPhotosWithObjects();

    // ✅ 날짜만 추출 (yyyy-MM-dd)
    @Query("SELECT DISTINCT SUBSTR(dateTaken, 1, 10) as date FROM Photo")
    List<String> getAvailableDates();

    // ✅ 특정 날짜에 찍힌 사진만 (타임라인용)
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :date || '%'")
    List<Photo> getPhotosForDate(String date);

    // ✅ 위치 + 객체가 있는 사진만 (지도 클러스터용)
    @Query("SELECT * FROM Photo WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND detectedObjects IS NOT NULL")
    List<Photo> getPhotosWithLocationAndObjects();

    // ✅ 중복 검사용: 파일 경로로 사진조회
    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoByFilePath(String filePath);

    @Query("UPDATE Photo SET detectedObjectPairs = :pairs WHERE filePath = :filePath")
    void updateDetectedObjectPairs(String filePath, List<Pair<String, Float>> pairs);

    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoWithDetectedPairs(String filePath);


}
