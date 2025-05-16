package com.example.wakey.data.model;

public class StoryItem {
    private String photoPath;
    private String description;

    public StoryItem(String photoPath, String description) {
        this.photoPath = photoPath;
        this.description = description;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public String getDescription() {
        return description;
    }
}
