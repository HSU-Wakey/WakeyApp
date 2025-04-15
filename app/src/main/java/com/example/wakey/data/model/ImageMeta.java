package com.example.wakey.data.model;

import android.util.Pair;
import java.util.List;

public class ImageMeta {
    private final String uri;
    private final String region;
    private final List<Pair<String, Float>> predictions;
    private final float[] embeddingVector;

    public ImageMeta(String uri, String region, List<Pair<String, Float>> predictions, float[] embeddingVector) {
        this.uri = uri;
        this.region = region;
        this.predictions = predictions;
        this.embeddingVector = embeddingVector;
    }

    public String getUri() {
        return uri;
    }

    public String getRegion() {
        return region;
    }

    public List<Pair<String, Float>> getPredictions() {
        return predictions;
    }

    public float[] getEmbeddingVector() {
        return embeddingVector;
    }
}
