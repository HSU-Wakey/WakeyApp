package com.example.wakey;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.ui.timeline.TimelineAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.exifinterface.media.ExifInterface;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private GoogleMap mMap;
    private PlacesClient placesClient;
    private FusedLocationProviderClient fusedLocationClient;

    private TextView dateTextView;
    private ImageButton mapButton;
    private ImageButton searchButton;
    private ImageButton prevDateBtn;
    private ImageButton nextDateBtn;

    private Calendar currentSelectedDate;
    private Map<String, List<PhotoInfo>> dateToPhotosMap;
    private Map<String, List<LatLng>> dateToRouteMap;

    // Bottom sheet components
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private RecyclerView timelineRecyclerView;
    private TimelineAdapter timelineAdapter;
    private List<TimelineItem> timelineItems = new ArrayList<>();
    private TextView bottomSheetDateTextView;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        dateTextView = findViewById(R.id.dateTextView);
        mapButton = findViewById(R.id.mapButton);
        searchButton = findViewById(R.id.searchButton);
        prevDateBtn = findViewById(R.id.prevDateBtn);
        nextDateBtn = findViewById(R.id.nextDateBtn);

        // Initialize the current date
        currentSelectedDate = Calendar.getInstance();
        updateDateDisplay();

        // Initialize maps
        dateToPhotosMap = new HashMap<>();
        dateToRouteMap = new HashMap<>();

        // Setup Google Maps : get map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Initialize Google Places
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_api_key));
        }
        placesClient = Places.createClient(this);

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Setup bottom sheet
        setupBottomSheet();

        // Setup click listeners
        setupClickListeners();

        // Request permissions
        requestLocationPermission();
    }

    private void setupBottomSheet() {
        // Initialize the bottom sheet
        View bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        // Set up the RecyclerView for timeline
        timelineRecyclerView = findViewById(R.id.timelineRecyclerView);
        timelineRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        timelineAdapter = new TimelineAdapter(timelineItems);
        timelineRecyclerView.setAdapter(timelineAdapter);

        // Set up date TextView in bottom sheet
        bottomSheetDateTextView = findViewById(R.id.bottom_sheet_date);

        // Set up tab layout
        tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Handle tab selection
                // This will be implemented in future steps
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not needed for now
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not needed for now
            }
        });

        // Set callback for bottom sheet state changes
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // Optionally handle state changes
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // Optionally handle sliding animation
            }
        });

        // Set up click listener for timeline items
        timelineAdapter.setOnTimelineItemClickListener(new TimelineAdapter.OnTimelineItemClickListener() {
            @Override
            public void onTimelineItemClick(TimelineItem item, int position) {
                // Move camera to the selected location
                if (mMap != null && item.getLatLng() != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(item.getLatLng(), 15));
                }
            }
        });
    }

    private void setupClickListeners() {
        // Date text view click - open calendar
        dateTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePickerDialog();
            }
        });

        // Map button click - toggle map layers or settings
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle map layers or show map settings
                Toast.makeText(MainActivity.this, "Map options clicked", Toast.LENGTH_SHORT).show();
            }
        });

        // Search button click - open search interface
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open search interface
                Toast.makeText(MainActivity.this, "Search clicked", Toast.LENGTH_SHORT).show();
            }
        });

        // Previous date button click
        prevDateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedDate.add(Calendar.DAY_OF_MONTH, -1);
                updateDateDisplay();
                loadPhotosForDate(getFormattedDate());
            }
        });

        // Next date button click
        nextDateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentSelectedDate.add(Calendar.DAY_OF_MONTH, 1);
                updateDateDisplay();
                loadPhotosForDate(getFormattedDate());
            }
        });
    }

    // Calendar Picker : jump to any date
    private void showDatePickerDialog() {
        // Create calendar builder
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker();

        // Apply custom theme
        builder.setTheme(R.style.CustomMaterialCalendarTheme);

        // Set title and initialize with current date
        builder.setTitleText("Wakey Wakey");
        builder.setSelection(currentSelectedDate.getTimeInMillis());

        // Create DatePicker
        MaterialDatePicker<Long> materialDatePicker = builder.build();

        // Set date selection listener
        materialDatePicker.addOnPositiveButtonClickListener(selection -> {
            // Set the selected date
            currentSelectedDate.setTimeInMillis(selection);
            updateDateDisplay();
            loadPhotosForDate(getFormattedDate());
        });

        // Show dialog
        materialDatePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void updateDateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
        String formattedDate = dateFormat.format(currentSelectedDate.getTime());
        dateTextView.setText(formattedDate);

        // Also update date in bottom sheet
        if (bottomSheetDateTextView != null) {
            bottomSheetDateTextView.setText(formattedDate);
        }
    }

    private String getFormattedDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(currentSelectedDate.getTime());
    }

    private void requestLocationPermission() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_MEDIA_IMAGES
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // If all permissions already granted, scan photos
            scanPhotosWithGeoData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // Permissions granted, scan photos
                scanPhotosWithGeoData();
            } else {
                Toast.makeText(this, "Permissions required to use this app", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Google Maps Callback : when map is ready
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check if we have permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Enable my location button
            mMap.setMyLocationEnabled(true);

            // Move camera to current location
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10));
                            }
                        }
                    });

            // Load photos for current date
            loadPhotosForDate(getFormattedDate());
        }

        // Setup marker click listener
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                // Show photo info when marker is clicked
                PhotoInfo photoInfo = (PhotoInfo) marker.getTag();
                if (photoInfo != null) {
                    // Expand bottom sheet to show details
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                    // Highlight the corresponding timeline item
                    highlightTimelineItem(photoInfo);
                }
                return false;
            }
        });
    }

    // Highlight a timeline item corresponding to a photo
    private void highlightTimelineItem(PhotoInfo photoInfo) {
        // Find the matching timeline item
        for (int i = 0; i < timelineItems.size(); i++) {
            TimelineItem item = timelineItems.get(i);
            if (item.getPhotoPath().equals(photoInfo.getFilePath())) {
                // Scroll to this position
                timelineRecyclerView.smoothScrollToPosition(i);
                break;
            }
        }
    }

    // Scan photos with EXIF data to get location
    private void scanPhotosWithGeoData() {
        // Clear existing data
        dateToPhotosMap.clear();
        dateToRouteMap.clear();

        // Define the columns we want to retrieve from MediaStore
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATA
        };

        // Query MediaStore for all images
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC")) {

            if (cursor != null) {
                int dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int dateTakenColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);

                while (cursor.moveToNext()) {
                    String filePath = cursor.getString(dataColumnIndex);
                    long dateTakenMillis = cursor.getLong(dateTakenColumnIndex);

                    // Try to extract GPS info from EXIF data
                    try {
                        ExifInterface exifInterface = new ExifInterface(filePath);
                        float[] latLong = new float[2];
                        boolean hasLatLong = exifInterface.getLatLong(latLong);

                        if (hasLatLong) {
                            // Create date string in format YYYY-MM-DD
                            Date dateTaken = new Date(dateTakenMillis);
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            String dateString = dateFormat.format(dateTaken);

                            // Create PhotoInfo object
                            PhotoInfo photoInfo = new PhotoInfo(
                                    filePath,
                                    dateTaken,
                                    new LatLng(latLong[0], latLong[1])
                            );

                            // Add to our map
                            if (!dateToPhotosMap.containsKey(dateString)) {
                                dateToPhotosMap.put(dateString, new ArrayList<>());
                                dateToRouteMap.put(dateString, new ArrayList<>());
                            }

                            dateToPhotosMap.get(dateString).add(photoInfo);
                            dateToRouteMap.get(dateString).add(photoInfo.getLatLng());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Load photos for current date
        loadPhotosForDate(getFormattedDate());
    }

    private void loadPhotosForDate(String dateString) {
        if (mMap == null) return;

        // Clear current markers
        mMap.clear();

        // Clear timeline items
        timelineItems.clear();

        // If we have photos for this date, add markers
        if (dateToPhotosMap.containsKey(dateString)) {
            List<PhotoInfo> photos = dateToPhotosMap.get(dateString);

            for (PhotoInfo photo : photos) {
                // Add marker to map
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(photo.getLatLng())
                        .title("Photo");

                Marker marker = mMap.addMarker(markerOptions);
                if (marker != null) {
                    marker.setTag(photo);
                }

                // Get place information for timeline
                getPlaceInfoForPhoto(photo);
            }

            // Draw route if we have multiple points
            List<LatLng> route = dateToRouteMap.get(dateString);
            if (route != null && route.size() > 1) {
                PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(route)
                        .width(5)
                        .color(getResources().getColor(R.color.route_color));

                mMap.addPolyline(polylineOptions);

                // Move camera to show the entire route
                if (!route.isEmpty()) {
                    LatLng firstPoint = route.get(0);
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 12));
                }
            }
        } else {
            Toast.makeText(this, "No photos for this date", Toast.LENGTH_SHORT).show();
        }

        // Update the timeline adapter
        if (timelineAdapter != null) {
            timelineAdapter.updateItems(timelineItems);
        }
    }

    private void getPlaceInfoForPhoto(final PhotoInfo photo) {
        // Use Geocoder to get location name for the photo
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try {
            LatLng location = photo.getLatLng();
            List<Address> addresses = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String locationName = "";

                // Try to get meaningful name (feature name, locality, or address line)
                if (address.getFeatureName() != null && !address.getFeatureName().equals("Unnamed Road")) {
                    locationName = address.getFeatureName();
                } else if (address.getLocality() != null) {
                    locationName = address.getLocality();
                } else if (address.getAddressLine(0) != null) {
                    locationName = address.getAddressLine(0);
                }

                // Create description based on location
                String description = "";
                if (address.getLocality() != null) {
                    description = address.getLocality() + "에서 찍은 사진";
                } else if (address.getSubLocality() != null) {
                    description = address.getSubLocality() + "에서 찍은 사진";
                } else {
                    description = "이 장소에서 찍은 사진";
                }

                // Create a timeline item
                TimelineItem item = new TimelineItem(
                        photo.getDateTaken(),
                        locationName,
                        photo.getFilePath(),
                        photo.getLatLng(),
                        description
                );

                // Add to timeline items and sort by time
                timelineItems.add(item);
                Collections.sort(timelineItems, (o1, o2) -> o1.getTime().compareTo(o2.getTime()));

                // Notify adapter of changes
                if (timelineAdapter != null) {
                    timelineAdapter.notifyDataSetChanged();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // PhotoInfo class to store photo metadata
    private static class PhotoInfo {
        private String filePath;
        private Date dateTaken;
        private LatLng latLng;

        public PhotoInfo(String filePath, Date dateTaken, LatLng latLng) {
            this.filePath = filePath;
            this.dateTaken = dateTaken;
            this.latLng = latLng;
        }

        public String getFilePath() {
            return filePath;
        }

        public Date getDateTaken() {
            return dateTaken;
        }

        public LatLng getLatLng() {
            return latLng;
        }
    }
}