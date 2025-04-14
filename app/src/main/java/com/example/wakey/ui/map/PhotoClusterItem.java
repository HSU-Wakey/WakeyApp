package com.example.wakey.ui.map;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;
import android.graphics.Bitmap;

import java.util.Collections;
import java.util.List;

/**
 * Class representing a photo as a cluster item for the map
 */
public class PhotoClusterItem implements ClusterItem {

    private final LatLng position;
    private final String title;
    private final String snippet;
    private final Object tag;
    private final Bitmap thumbnail;
    private final List<String> detectedObjects = Collections.emptyList(); // ✅ 기본값

    public PhotoClusterItem(LatLng position, String title, String snippet, Object tag, Bitmap thumbnail) {
        this.position = position;
        this.title = title;
        this.snippet = snippet;
        this.tag = tag;
        this.thumbnail = thumbnail;
    }

    @Override
    public LatLng getPosition() {
        return position;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getSnippet() {
        return snippet;
    }

    public Object getTag() {
        return tag;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public List<String> getDetectedObjects() {
        return detectedObjects;
    }
}
