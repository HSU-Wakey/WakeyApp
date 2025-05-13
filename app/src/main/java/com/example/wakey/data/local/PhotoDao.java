package com.example.wakey.data.local;

import android.util.Pair;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;  // ⭐ 추가

import java.util.List;

@Dao
public interface PhotoDao {

    // INSERT
    @Insert
    void insertPhoto(Photo photo);

    @Insert
    void insertPhotos(List<Photo> photos);

    // ⭐ UPDATE 메서드 추가
    @Update
    void updatePhoto(Photo photo);

    // 기본 SELECT
    @Query("SELECT * FROM Photo")
    List<Photo> getAllPhotos();

    // ✅ 특정 ID의 사진 조회 (벡터 비교용)
    @Query("SELECT * FROM Photo WHERE id = :photoId")
    Photo getPhotoById(int photoId);

    // 전체 삭제
    @Query("DELETE FROM Photo")
    void deleteAllPhotos();

    // 중복 제거 쿼리
    @Query("DELETE FROM Photo WHERE rowid NOT IN (SELECT MIN(rowid) FROM Photo GROUP BY filePath)")
    void deleteDuplicatePhotos();

    // 객체 인식 결과 있는 사진만
    @Query("SELECT * FROM Photo WHERE detectedObjects IS NOT NULL")
    List<Photo> getPhotosWithObjects();

    // 날짜만 추출 (yyyy-MM-dd)
    @Query("SELECT DISTINCT SUBSTR(dateTaken, 1, 10) as date FROM Photo")
    List<String> getAvailableDates();

    // 특정 날짜에 찍힌 사진만
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :date || '%'")
    List<Photo> getPhotosForDate(String date);

    // 위치 + 객체가 있는 사진만
    @Query("SELECT * FROM Photo WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND detectedObjects IS NOT NULL")
    List<Photo> getPhotosWithLocationAndObjects();

    // 중복 검사용: 파일 경로로 사진조회
    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoByFilePath(String filePath);

    // 모델 예측 결과 업데이트
    @Query("UPDATE Photo SET detectedObjectPairs = :pairs WHERE filePath = :filePath")
    void updateDetectedObjectPairs(String filePath, List<Pair<String, Float>> pairs);

    // 전체 주소 업데이트
    @Query("UPDATE Photo SET fullAddress = :address WHERE filePath = :filePath")
    void updateFullAddress(String filePath, String address);

    // 전체 주소가 포함된 사진 조회
    @Query("SELECT * FROM Photo WHERE fullAddress IS NOT NULL")
    List<Photo> getPhotosWithFullAddress();

    // 예측된 객체 결과와 함께 저장된 사진 조회
    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoWithDetectedPairs(String filePath);

    // 캡션 업데이트
    @Query("UPDATE Photo SET caption = :caption WHERE filePath = :filePath")
    void updateCaption(String filePath, String caption);

    // 캡션이 없는 사진 조회
    @Query("SELECT * FROM Photo WHERE caption IS NULL OR caption = ''")
    List<Photo> getPhotosWithoutCaptions();

    // 특정 날짜의 사진 중 캡션이 없는 사진 조회
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :date || '%' AND (caption IS NULL OR caption = '')")
    List<Photo> getPhotosForDateWithoutCaptions(String date);

    // 스토리 업데이트 (추가)
    @Query("UPDATE Photo SET story = :story WHERE filePath = :filePath")
    void updateStory(String filePath, String story);

    // 스토리가 없는 사진 조회 (추가)
    @Query("SELECT * FROM Photo WHERE story IS NULL OR story = ''")
    List<Photo> getPhotosWithoutStories();

    // 특정 날짜의 사진 중 스토리가 없는 사진 조회 (추가)
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :date || '%' AND (story IS NULL OR story = '')")
    List<Photo> getPhotosForDateWithoutStories(String date);

    // ✅ 감지된 객체 문자열 업데이트 (누락된 메서드 추가)
    @Query("UPDATE Photo SET detectedObjects = :objects WHERE filePath = :filePath")
    void updateObjectLabels(String filePath, String objects);

    // detectedObjects 조회
    @Query("SELECT detectedObjects FROM Photo WHERE filePath = :filePath")
    String getDetectedObjects(String filePath);
}