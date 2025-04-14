package com.example.wakey.ui.album.diary;

/**
 * 다이어리 아이템 모델 클래스
 */
public class DiaryItem {
    private String title;
    private String dateRange;
    private String thumbnailPath;
    private int heartCount;

    public DiaryItem(String title, String dateRange, String thumbnailPath, int heartCount) {
        this.title = title;
        this.dateRange = dateRange;
        this.thumbnailPath = thumbnailPath;
        this.heartCount = heartCount;
    }

    public String getTitle() { return title; }
    public String getDateRange() { return dateRange; }
    public String getThumbnailPath() { return thumbnailPath; }
    public int getHeartCount() { return heartCount; }
}