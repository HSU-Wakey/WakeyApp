package com.example.wakey.data.local;

import android.util.Pair;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PhotoDao {
    // INSERT
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertPhoto(Photo photo);

    @Insert
    void insertPhotos(List<Photo> photos);

    // UPDATE
    @Update
    void updatePhoto(Photo photo);

    // 기본 SELECT
    @Query("SELECT * FROM Photo")
    List<Photo> getAllPhotos();

    @Query("SELECT * FROM Photo ORDER BY dateTaken DESC")
    List<Photo> getAllPhotosOrderByDateDesc();

    // 특정 ID의 사진 조회 (벡터 비교용)
    @Query("SELECT * FROM Photo WHERE id = :photoId")
    Photo getPhotoById(int photoId);

    // 특정 파일 경로의 사진 조회
    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoByFilePath(String filePath);

    // 날짜 기반 조회
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :dateString || '%' ORDER BY dateTaken")
    List<Photo> getPhotosForDate(String dateString);

    // 사용 가능한 날짜 목록 조회
    @Query("SELECT DISTINCT substr(dateTaken, 1, 10) FROM Photo ORDER BY dateTaken DESC")
    List<String> getAvailableDates();

    // 객체 인식 결과 있는 사진만 조회
    @Query("SELECT * FROM Photo WHERE detectedObjects IS NOT NULL AND detectedObjects != ''")
    List<Photo> getPhotosWithObjects();

    // 위치 + 객체가 있는 사진만 조회
    @Query("SELECT * FROM Photo WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND detectedObjects IS NOT NULL")
    List<Photo> getPhotosWithLocationAndObjects();

    // 위치 + 객체가 있는 사진만 조회 (detectedObjectPairs 기준)
    @Query("SELECT * FROM Photo WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND detectedObjectPairs IS NOT NULL")
    List<Photo> getPhotosWithLocationAndObjectPairs();

    // 전체 주소가 포함된 사진 조회
    @Query("SELECT * FROM Photo WHERE fullAddress IS NOT NULL")
    List<Photo> getPhotosWithFullAddress();

    // 캡션이 없는 사진 조회
    @Query("SELECT * FROM Photo WHERE caption IS NULL OR caption = ''")
    List<Photo> getPhotosWithoutCaptions();

    // 특정 날짜의 사진 중 캡션이 없는 사진 조회
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :date || '%' AND (caption IS NULL OR caption = '')")
    List<Photo> getPhotosForDateWithoutCaptions(String date);

    // 스토리가 없는 사진 조회
    @Query("SELECT * FROM Photo WHERE story IS NULL OR story = ''")
    List<Photo> getPhotosWithoutStories();

    // 특정 날짜의 사진 중 스토리가 없는 사진 조회
    @Query("SELECT * FROM Photo WHERE dateTaken LIKE :date || '%' AND (story IS NULL OR story = '')")
    List<Photo> getPhotosForDateWithoutStories(String date);

    // UPDATE 쿼리들
    @Query("UPDATE Photo SET fullAddress = :fullAddress WHERE filePath = :filePath")
    void updateFullAddress(String filePath, String fullAddress);

    @Query("UPDATE Photo SET detectedObjects = :detectedObjects WHERE filePath = :filePath")
    void updateDetectedObjects(String filePath, String detectedObjects);

    @Query("UPDATE Photo SET detectedObjectPairs = :pairs WHERE filePath = :filePath")
    void updateDetectedObjectPairs(String filePath, List<Pair<String, Float>> pairs);

    @Query("UPDATE Photo SET caption = :caption WHERE filePath = :filePath")
    void updateCaption(String filePath, String caption);

    @Query("UPDATE Photo SET story = :story WHERE filePath = :filePath")
    int updateStory(String filePath, String story);

    @Query("UPDATE Photo SET detectedObjects = :objects WHERE filePath = :filePath")
    void updateObjectLabels(String filePath, String objects);

    // DELETE 쿼리들
    @Query("DELETE FROM Photo")
    void deleteAllPhotos();

    // 중복 제거 대체 쿼리
    @Query("DELETE FROM Photo WHERE rowid NOT IN (SELECT MIN(rowid) FROM Photo GROUP BY filePath)")
    void deleteDuplicatePhotos();

    @Query("DELETE FROM Photo WHERE rowid NOT IN (SELECT MIN(rowid) FROM Photo GROUP BY filePath)")
    void deleteDuplicatePhotosAlt();

    // country 관련 쿼리
    @Query("SELECT * FROM Photo WHERE country = :country ORDER BY dateTaken DESC")
    List<Photo> getPhotosByCountry(String country);

    @Query("SELECT * FROM Photo WHERE locationDo = :locationDo ORDER BY dateTaken DESC")
    List<Photo> getPhotosByLocationDo(String locationDo);

    // 예측된 객체 결과와 함께 저장된 사진 조회
    @Query("SELECT * FROM Photo WHERE filePath = :filePath LIMIT 1")
    Photo getPhotoWithDetectedPairs(String filePath);

    // detectedObjects 조회
    @Query("SELECT detectedObjects FROM Photo WHERE filePath = :filePath")
    String getDetectedObjects(String filePath);

    // 해시태그 관련 쿼리
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
}