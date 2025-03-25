// data/model/PlaceData.java
package com.example.wakey.data.model;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.Place;
import java.util.List;

public class PlaceData {
    private String name;
    private String address;
    private LatLng latLng;
    private List<Place.Type> types;
    private Double rating;

    public PlaceData(String name, String address, LatLng latLng, List<Place.Type> types, Double rating) {
        this.name = name;
        this.address = address;
        this.latLng = latLng;
        this.types = types;
        this.rating = rating;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public LatLng getLatLng() {
        return latLng;
    }

    public List<Place.Type> getTypes() {
        return types;
    }

    public Double getRating() {
        return rating;
    }

    // 대표 유형 반환 메소드
    public Place.Type getPrimaryType() {
        if (types != null && !types.isEmpty()) {
            return types.get(0);
        }
        return null;
    }
}