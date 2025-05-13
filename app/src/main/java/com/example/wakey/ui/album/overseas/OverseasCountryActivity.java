package com.example.wakey.ui.album.overseas;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.repository.PhotoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class OverseasCountryActivity extends AppCompatActivity {
    private static final String TAG = "OverseasCountryActivity";

    private RecyclerView countryRecyclerView;
    private OverseasLocationAdapter countryAdapter;
    private ProgressBar loadingSpinner;
    private TextView emptyTextView;

    private PhotoRepository photoRepository;
    private List<OverseasFragment.OverseasLocationItem> countryItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overseas_country);

        photoRepository = new PhotoRepository(this);
        initViews();
        loadCountryData();
    }

    private void initViews() {
        countryRecyclerView = findViewById(R.id.recyclerViewCountries);
        countryRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        loadingSpinner = findViewById(R.id.loadingSpinner);
        emptyTextView = findViewById(R.id.emptyTextView);

        countryAdapter = new OverseasLocationAdapter(countryItems, item -> {
            Intent intent = new Intent(OverseasCountryActivity.this, OverseasRegionActivity.class);
            intent.putExtra("REGION_NAME", item.getName());
            intent.putExtra("REGION_ORIGINAL_NAME", item.getOriginalName());
            startActivity(intent);
        });

        countryRecyclerView.setAdapter(countryAdapter);
    }

    private void loadCountryData() {
        loadingSpinner.setVisibility(View.VISIBLE);
        photoRepository.getOverseasPhotos().thenAccept(countryMap -> {
            List<OverseasFragment.OverseasLocationItem> items = new ArrayList<>();

            for (Map.Entry<String, List<Photo>> entry : countryMap.entrySet()) {
                String country = entry.getKey();
                List<Photo> photos = entry.getValue();

                if (photos.isEmpty()) continue;

                String thumbnailPath = photos.get(0).filePath;

                OverseasFragment.OverseasLocationItem item = new OverseasFragment.OverseasLocationItem(
                        country, country, thumbnailPath, photos.size()
                );
                items.add(item);
            }

            Collections.sort(items, Comparator.comparing(OverseasFragment.OverseasLocationItem::getName));

            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                if (items.isEmpty()) {
                    emptyTextView.setVisibility(View.VISIBLE);
                } else {
                    emptyTextView.setVisibility(View.GONE);
                    countryItems.clear();
                    countryItems.addAll(items);
                    countryAdapter.notifyDataSetChanged();
                }
            });
        }).exceptionally(e -> {
            Log.e(TAG, "Error loading country data", e);
            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                emptyTextView.setVisibility(View.VISIBLE);
                emptyTextView.setText("데이터를 불러오지 못했습니다.");
            });
            return null;
        });
    }
}
