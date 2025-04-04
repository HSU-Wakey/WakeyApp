package com.example.wakey.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PhotoDao {
    @Insert
    void insertPhoto(Photo photo);

    @Insert
    void insertPhotos(List<Photo> photos);

    @Query("SELECT * FROM Photo")
    List<Photo> getAllPhotos();

    @Query("DELETE FROM Photo")
    void deleteAllPhotos(); // ✅ 추가
}
