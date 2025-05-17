package com.example.wakey.ui.photo;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PhotoDetailDialogFragment extends DialogFragment {

    private static final String ARG_PHOTO_URI = "photo_uri";
    private static final String ARG_PHOTO_NAME = "photo_name";
    private static final String ARG_PHOTO_DATE = "photo_date";
    private static final String ARG_PHOTO_TAGS = "photo_tags";

    private PhotoView detailPhotoView;
    private TextView fileNameTextView;
    private TextView dateTakenTextView;
    private ChipGroup tagChipGroup;
    private ImageButton closeDetailButton;

    public static PhotoDetailDialogFragment newInstance(String photoUri, String photoName,
                                                        long photoDate, List<String> tags) {
        PhotoDetailDialogFragment fragment = new PhotoDetailDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PHOTO_URI, photoUri);
        args.putString(ARG_PHOTO_NAME, photoName);
        args.putLong(ARG_PHOTO_DATE, photoDate);
        args.putStringArrayList(ARG_PHOTO_TAGS, new ArrayList<>(tags));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_photo_detail, container, false);

        // 뷰 초기화
        detailPhotoView = view.findViewById(R.id.detailPhotoView);
//        fileNameTextView = view.findViewById(R.id.fileNameTextView);
//        dateTakenTextView = view.findViewById(R.id.dateTakenTextView);
//        tagChipGroup = view.findViewById(R.id.tagChipGroup);
        closeDetailButton = view.findViewById(R.id.closeDetailButton);

        closeDetailButton.setOnClickListener(v -> dismiss());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            try {
                String photoUri = args.getString(ARG_PHOTO_URI);
                String photoName = args.getString(ARG_PHOTO_NAME);
                long photoDate = args.getLong(ARG_PHOTO_DATE);
                List<String> tags = args.getStringArrayList(ARG_PHOTO_TAGS);

                // 이미지 로드
                if (photoUri != null) {
                    Glide.with(requireContext())
                            .load(Uri.parse(photoUri))
                            .into(detailPhotoView);
                }

                // 파일명 설정
                fileNameTextView.setText(photoName);

                // 날짜 포맷팅 및 설정
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                String formattedDate = dateFormat.format(new Date(photoDate));
                dateTakenTextView.setText(formattedDate);

                // 태그 추가
                if (tags != null && !tags.isEmpty()) {
                    for (String tag : tags) {
                        if (tag != null && !tag.trim().isEmpty()) {
                            addTagChip(tag.trim());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("PhotoDetailDialogFragment", "Error setting up dialog: " + e.getMessage(), e);
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    private void addTagChip(String tag) {
        try {
            Chip chip = new Chip(requireContext());
            chip.setText(tag.startsWith("#") ? tag : "#" + tag);
            chip.setClickable(false);
            chip.setCheckable(false);

            // 태그 칩 스타일 설정 - 리소스 ID가 없으면 직접 색상 설정
            try {
                chip.setChipBackgroundColorResource(R.color.chip_background);
            } catch (Exception e) {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#3D5AFE")));
            }
            chip.setTextColor(Color.WHITE);

            tagChipGroup.addView(chip);
        } catch (Exception e) {
            Log.e("PhotoDetailDialogFragment", "Error adding tag chip: " + e.getMessage(), e);
        }
    }
}