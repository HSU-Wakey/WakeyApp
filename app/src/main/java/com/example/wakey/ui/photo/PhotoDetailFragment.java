// ui/photo/PhotoDetailFragment.java
package com.example.wakey.ui.photo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.tflite.ImageClassifier;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class PhotoDetailFragment extends DialogFragment {

    private static final String ARG_TIMELINE_ITEM = "timelineItem";
    private TimelineItem timelineItem;

    public static PhotoDetailFragment newInstance(TimelineItem item) {
        PhotoDetailFragment fragment = new PhotoDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TIMELINE_ITEM, item);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && getArguments().containsKey(ARG_TIMELINE_ITEM)) {
            timelineItem = (TimelineItem) getArguments().getSerializable(ARG_TIMELINE_ITEM);
        }
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_detail, container, false);

        ImageView photoImageView = view.findViewById(R.id.photoDetailImageView);
        TextView captionTextView = view.findViewById(R.id.photoDetailCaptionTextView);
        TextView locationTextView = view.findViewById(R.id.photoDetailLocationTextView);
        TextView timeTextView = view.findViewById(R.id.photoDetailTimeTextView);
        TextView predictionTextView = view.findViewById(R.id.photoDetailPredictionTextView);
        TextView addressTextView = view.findViewById(R.id.photoDetailAddressTextView);
        View closeButton = view.findViewById(R.id.closeButton);

        if (timelineItem != null) {
            // UI에 즉시 반영 가능한 부분 먼저
            captionTextView.setText(timelineItem.getDescription());
            locationTextView.setText(timelineItem.getLocation());

            String dateTimeStr = DateUtil.getFormattedDateWithDay(timelineItem.getTime()) +
                    " " + DateUtil.formatTime(timelineItem.getTime());
            timeTextView.setText(dateTimeStr);

            // Glide로 이미지 미리 로드
            if (timelineItem.getPhotoPath() != null) {
                Glide.with(this)
                        .load(timelineItem.getPhotoPath())
                        .into(photoImageView);
            }

            // 🔁 백그라운드 스레드에서 나머지 처리 (파일 & DB 접근)
            new Thread(() -> {
                try {
                    File imgFile = new File(timelineItem.getPhotoPath());
                    if (imgFile.exists()) {
                        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                        // ✅ 인식 결과 (getDetectedObjects) 백그라운드 처리
                        List<String> detectedObjects = timelineItem.getDetectedObjects();

                        StringBuilder sb = new StringBuilder();
                        if (detectedObjects != null && !detectedObjects.isEmpty()) {
                            sb.append("🔍 인식된 객체:\n\n");
                            for (String obj : detectedObjects) {
                                sb.append("• ").append(obj).append("\n");
                            }
                        } else {
                            sb.append("인식된 결과가 없습니다.");
                        }

                        requireActivity().runOnUiThread(() ->
                                predictionTextView.setText(sb.toString()));
                    }

                    // ✅ 위치 정보 가져오기 (Geocoder)도 백그라운드
                    if (timelineItem.getLatLng() != null) {
                        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(
                                timelineItem.getLatLng().latitude,
                                timelineItem.getLatLng().longitude,
                                1
                        );

                        String addressStr;
                        if (addresses != null && !addresses.isEmpty()) {
                            addressStr = "📍 위치: " + addresses.get(0).getAddressLine(0);
                        } else {
                            addressStr = "위치 정보 없음";
                        }

                        requireActivity().runOnUiThread(() ->
                                addressTextView.setText(addressStr));
                    } else {
                        requireActivity().runOnUiThread(() ->
                                addressTextView.setText("위치 정보 없음"));
                    }
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            predictionTextView.setText("정보 불러오기 실패"));
                    e.printStackTrace();
                }
            }).start();
        }

        closeButton.setOnClickListener(v -> dismiss());

        return view;
    }
}