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
    @Query("SELECT * FROM Photo WHERE detectedObjectPairs IS NOT NULL")
    List<Photo> getPhotosWithObjects();

    // 날짜만 추출 (yyyy-MM-dd)
    @Query("SELECT DISTINCT SUBSTR(dateTaken, 1, 10) as date FROM Photo")
    List<String> getAvailableDates();

    // 특정 날짜에 찍힌 사진만
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :date || '%'")
    List<Photo> getPhotosForDate(String date);

    // 위치 + 객체가 있는 사진만
    @Query("SELECT * FROM Photo WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND detectedObjectPairs IS NOT NULL")
    List<Photo> getPhotosWithLocationAndObjects();

    // 중복 검사용: 파일 경로로 사진조회
    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoByFilePath(String filePath);

    // 모델 예측 결과 업데이트
    @Query("UPDATE Photo SET detectedObjectPairs = :detectedPairs WHERE filePath = :filePath")
    void updateDetectedObjectPairs(String filePath, List<Pair<String, Float>> detectedPairs);

    // 전체 주소 업데이트
    @Query("UPDATE Photo SET fullAddress = :address WHERE filePath = :filePath")
    void updateFullAddress(String filePath, String address);

    // 전체 주소가 포함된 사진 조회
    @Query("SELECT * FROM Photo WHERE fullAddress IS NOT NULL")
    List<Photo> getPhotosWithFullAddress();

    // 예측된 객체 결과와 함께 저장된 사진 조회
    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoWithDetectedPairs(String filePath);

    @Query("SELECT * FROM Photo WHERE hashtags LIKE '%#' || :hashtag || ' %' OR hashtags LIKE '%#' || :hashtag || '%' OR hashtags LIKE '#' || :hashtag || ' %' OR hashtags = '#' || :hashtag")
    List<Photo> getPhotosByHashtag(String hashtag);

    @Query("SELECT hashtags FROM Photo WHERE filePath = :photoPath")
    String getHashtagsByPath(String photoPath);

    @Query("UPDATE Photo SET hashtags = :hashtags WHERE filePath = :photoPath")
    void updateHashtags(String photoPath, String hashtags);

    @Query("SELECT * FROM Photo WHERE filePath = :filePath")
    Photo getPhotoByPath(String filePath);

    // 추가: 해시태그가 없는 사진들 조회 (개수 제한)
    @Query("SELECT * FROM Photo WHERE hashtags IS NULL OR hashtags = '' LIMIT :limit")
    List<Photo> getPhotosWithoutHashtagsLimit(int limit);

    // 추가: 해시태그가 없는 사진 개수 조회
    @Query("SELECT COUNT(*) FROM Photo WHERE hashtags IS NULL OR hashtags = ''")
    int countPhotosWithoutHashtags();

    @Query("SELECT DISTINCT country FROM Photo WHERE country IS NOT NULL")
    List<String> getAllCountries();

    @Query("SELECT * FROM Photo WHERE country = :country")
    List<Photo> getPhotosByCountry(String country);

}
