package com.example.wakey.data.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.wakey.R;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for Places API functionality
 */
public class PlaceHelper {

    private static final String TAG = "PlaceHelper";

    // Map of place types to icons
    private static final Map<Place.Type, Integer> PLACE_TYPE_ICONS = new HashMap<>();

    static {
        // Initialize map with common place types and corresponding icons
        PLACE_TYPE_ICONS.put(Place.Type.RESTAURANT, R.drawable.ic_restaurant);
        PLACE_TYPE_ICONS.put(Place.Type.CAFE, R.drawable.ic_cafe);
        PLACE_TYPE_ICONS.put(Place.Type.SHOPPING_MALL, R.drawable.ic_shopping);
        PLACE_TYPE_ICONS.put(Place.Type.TOURIST_ATTRACTION, R.drawable.ic_attraction);
        PLACE_TYPE_ICONS.put(Place.Type.PARK, R.drawable.ic_park);
        PLACE_TYPE_ICONS.put(Place.Type.MUSEUM, R.drawable.ic_museum);
        // Add more place types as needed
    }

    /**
     * Interface for place search callbacks
     */
    public interface OnPlaceFoundListener {
        void onPlaceFound(String name, String address, LatLng latLng, List<Place.Type> types);
        void onPlaceSearchComplete();
        void onPlaceSearchError(Exception e);
    }

    /**
     * Get nearby places using the Places SDK
     * @param client PlacesClient instance
     * @param location Current location to search around
     * @param radius Search radius in meters
     * @param listener Callback listener
     */
    public static void getNearbyPlaces(PlacesClient client, LatLng location, int radius, OnPlaceFoundListener listener) {
        // Define the place fields to return
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.TYPES
        );

        // Create a FindCurrentPlaceRequest
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);

        try {
            client.findCurrentPlace(request).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FindCurrentPlaceResponse response = task.getResult();

                    // Process each likely place
                    for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                        Place place = placeLikelihood.getPlace();

                        // Calculate distance from target location
                        float[] distance = new float[1];
                        android.location.Location.distanceBetween(
                                location.latitude, location.longitude,
                                place.getLatLng().latitude, place.getLatLng().longitude,
                                distance);

                        // Only include places within the radius
                        if (distance[0] <= radius) {
                            listener.onPlaceFound(
                                    place.getName(),
                                    place.getAddress(),
                                    place.getLatLng(),
                                    place.getTypes());
                        }
                    }

                    listener.onPlaceSearchComplete();
                } else {
                    Exception exception = task.getException();
                    if (exception != null) {
                        listener.onPlaceSearchError(exception);
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
            listener.onPlaceSearchError(e);
        }
    }

    /**
     * Get icon for a place type
     * @param context Application context
     * @param placeType Place type
     * @return BitmapDescriptor for the icon
     */
    public static BitmapDescriptor getPlaceTypeIcon(Context context, Place.Type placeType) {
        Integer iconResId = PLACE_TYPE_ICONS.get(placeType);

        if (iconResId != null) {
            return getBitmapFromDrawable(context, iconResId);
        } else {
            // Default icon if type not found
            return getBitmapFromDrawable(context, R.drawable.ic_place);
        }
    }

    /**
     * Convert drawable to BitmapDescriptor for map markers
     * @param context Application context
     * @param drawableId Drawable resource ID
     * @return BitmapDescriptor
     */
    private static BitmapDescriptor getBitmapFromDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);

        if (drawable == null) {
            return BitmapDescriptorFactory.defaultMarker();
        }

        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * Get a descriptive caption for a place type
     * @param placeType Place type
     * @return Descriptive caption
     */
    public static String getPlaceTypeCaption(Place.Type placeType) {
        switch (placeType) {
            case RESTAURANT:
                return "맛있는 음식을 즐긴 곳";
            case CAFE:
                return "커피와 함께한 시간";
            case SHOPPING_MALL:
                return "쇼핑을 즐긴 곳";
            case TOURIST_ATTRACTION:
                return "관광 명소 방문";
            case PARK:
                return "자연을 즐긴 공원";
            case MUSEUM:
                return "예술과 역사를 감상한 곳";
            default:
                return "방문한 장소";
        }
    }
}