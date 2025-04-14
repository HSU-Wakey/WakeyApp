package com.example.wakey.ui.timeline;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.PhotoInfo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.PhotoRepository;
import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TimelineFragment extends Fragment {

    private RecyclerView recyclerView;
    private TimelineAdapter adapter;
    private static final String TAG = "TimelineDebug";

    public TimelineFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.bottom_sheet_timeline, container, false);

        recyclerView = view.findViewById(R.id.timelineRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        Executors.newSingleThreadExecutor().execute(() -> {
            PhotoRepository photoRepository = PhotoRepository.getInstance(requireContext());
            List<Photo> photoEntities = photoRepository.getPhotosWithObjects();

            List<PhotoInfo> photoInfoList = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());

            for (Photo photo : photoEntities) {
                LatLng latLng = new LatLng(photo.latitude, photo.longitude);

                Date date;
                try {
                    date = sdf.parse(photo.dateTaken);
                } catch (ParseException e) {
                    Log.e(TAG, "날짜 파싱 실패: " + photo.dateTaken, e);
                    date = new Date();
                }

                List<String> objects = new ArrayList<>();
                if (photo.detectedObjects != null && !photo.detectedObjects.isEmpty()) {
                    objects = Arrays.asList(photo.detectedObjects.split(","));
                }

                String address = photo.locationDo + " " + photo.locationGu + " " + photo.locationStreet;

                PhotoInfo info = new PhotoInfo(
                        photo.filePath,
                        date,
                        latLng,
                        null,
                        null,
                        address,
                        photo.caption,
                        objects
                );

                info.setLocationDo(photo.locationDo);
                info.setLocationGu(photo.locationGu);
                info.setLocationStreet(photo.locationStreet);

                photoInfoList.add(info);
            }

            List<TimelineItem> timelineItems = TimelineManager.getInstance(requireContext())
                    .buildTimelineWithObjects(photoInfoList);

            requireActivity().runOnUiThread(() -> {
                adapter = new TimelineAdapter(timelineItems);
                recyclerView.setAdapter(adapter);
            });
        });

        return view;
    }
}