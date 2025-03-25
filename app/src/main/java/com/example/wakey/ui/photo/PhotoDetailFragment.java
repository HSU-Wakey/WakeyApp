// ui/photo/PhotoDetailFragment.java
package com.example.wakey.ui.photo;

import android.os.Bundle;
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

public class PhotoDetailFragment extends DialogFragment {

    private static final String ARG_TIMELINE_ITEM = "timelineItem";
    private TimelineItem timelineItem;

    public static PhotoDetailFragment newInstance(TimelineItem item) {
        PhotoDetailFragment fragment = new PhotoDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TIMELINE_ITEM, item); // TimelineItem이 Serializable을 구현한다고 가정
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
        View closeButton = view.findViewById(R.id.closeButton);

        if (timelineItem != null) {
            // 사진 로드
            if (timelineItem.getPhotoPath() != null) {
                Glide.with(this)
                        .load(timelineItem.getPhotoPath())
                        .into(photoImageView);
            }

            // 정보 설정
            captionTextView.setText(timelineItem.getDescription());
            locationTextView.setText(timelineItem.getLocation());

            String dateTimeStr = DateUtil.getFormattedDateWithDay(timelineItem.getTime()) +
                    " " + DateUtil.formatTime(timelineItem.getTime());
            timeTextView.setText(dateTimeStr);
        }

        // 닫기 버튼 클릭 처리
        closeButton.setOnClickListener(v -> dismiss());

        return view;
    }
}