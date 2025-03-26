//ui/map/photoclusteritem.java
package com.example.wakey.ui.map;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;
import android.graphics.Bitmap;

/**
 * Class representing a photo as a cluster item for the map
 */
public class PhotoClusterItem implements ClusterItem {

    private final LatLng position;
    private final String title;
    private final String snippet;
    private final Object tag;
    private final Bitmap thumbnail; // 썸네일 추가!

    /**
     * Create a new PhotoClusterItem
     *
     * @param position The location of the photo
     * @param title The title to show in the info window
     * @param snippet The snippet to show in the info window
     * @param tag Additional data to store with the item
     * @param thumbnail Bitmap thumbnail of the photo
     */
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

    /**
     * Get the tag object associated with this cluster item
     *
     * @return The tag object
     */
    public Object getTag() {
        return tag;
    }

    /**
     * Get thumbnail bitmap
     *
     * @return thumbnail bitmap
     */
    public Bitmap getThumbnail() {
        return thumbnail;
    }
}
