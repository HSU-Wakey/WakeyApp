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
import com.example.wakey.data.repository.TimelineManager;
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
            // ✅ DB에서 객체가 있는 사진만 불러오기
            PhotoRepository photoRepository = PhotoRepository.getInstance(requireContext());
            List<Photo> photoEntities = photoRepository.getPhotosWithObjects();
            Log.d(TAG, "\uD83D\uDCF8 불러온 사진 개수: " + photoEntities.size());

            List<PhotoInfo> photoInfoList = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault());

            for (Photo photo : photoEntities) {
                // ✅ 위도/경도 → LatLng
                LatLng latLng = new LatLng(photo.latitude, photo.longitude);

                // ✅ 날짜 파싱
                Date date;
                try {
                    date = sdf.parse(photo.dateTaken);
                } catch (ParseException e) {
                    Log.e(TAG, "❌ 날짜 파싱 실패: " + photo.dateTaken, e);
                    date = new Date();
                }

                // ✅ 객체 분리
                List<String> objects = new ArrayList<>();
                if (photo.detectedObjects != null && !photo.detectedObjects.isEmpty()) {
                    objects = Arrays.asList(photo.detectedObjects.split(","));
                }

                // ✅ 주소 문자열 조합
                String address = photo.locationDo + " " + photo.locationGu + " " + photo.locationStreet;

                // ✅ PhotoInfo 생성
                PhotoInfo info = new PhotoInfo(
                        photo.filePath,
                        date,
                        latLng,
                        null, // placeId
                        null, // placeName
                        address, // ✅ 조합한 주소 넣기
                        photo.caption, // ✅ description 대신
                        objects
                );

                // ✅ 세부 주소 필드
                info.setLocationDo(photo.locationDo);
                info.setLocationGu(photo.locationGu);
                info.setLocationStreet(photo.locationStreet);

                photoInfoList.add(info);

                // ✅ 로그 확인
                Log.d(TAG, "\uD83D\uDDFC️ 파일: " + photo.filePath);
                Log.d(TAG, "\uD83D\uDCC5 날짜: " + photo.dateTaken + " → " + date);
                Log.d(TAG, "\uD83E\uDDE0 객체: " + objects);
                Log.d(TAG, "\uD83D\uDCCD 위치: " + latLng);
                Log.d(TAG, "\uD83C\uDFE0 주소: " + address);
            }


            // ✅ 타임라인 생성
            List<TimelineItem> timelineItems = TimelineManager.getInstance(requireContext())
                    .buildTimelineWithObjects(photoInfoList);

            Log.d(TAG, "\uD83D\uDCCC 타임라인 아이템 수: " + timelineItems.size());

            requireActivity().runOnUiThread(() -> {
                adapter = new TimelineAdapter(timelineItems);
                recyclerView.setAdapter(adapter);
            });
        });

        return view;
    }
}
