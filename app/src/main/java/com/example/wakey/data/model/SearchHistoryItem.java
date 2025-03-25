package com.example.wakey.data.model;

public class SearchHistoryItem {
    private String query;
    private String imagePath;
    private long timestamp;

    public SearchHistoryItem(String query, String imagePath, long timestamp) {
        this.query = query;
        this.imagePath = imagePath;
        this.timestamp = timestamp;
    }

    public String getQuery() {
        return query;
    }

    public String getImagePath() {
        return imagePath;
    }

    public long getTimestamp() {
        return timestamp;
    }
}