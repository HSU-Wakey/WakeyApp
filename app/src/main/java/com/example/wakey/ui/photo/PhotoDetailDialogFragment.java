package com.example.wakey.ui.photo;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class PhotoDetailDialogFragment extends DialogFragment {

    private static final String ARG_PHOTO_URI = "photo_uri";
    private static final String ARG_PHOTO_NAME = "photo_name";
    private static final String ARG_PHOTO_DATE = "photo_date";
    private static final String ARG_PHOTO_TAGS = "photo_tags";
    private static final String ARG_HASHTAG = "hashtag";

    private ViewPager2 viewPager;
    private PhotoDetailPagerAdapter pagerAdapter;
    private List<Photo> allPhotos = new ArrayList<>();
    private int currentPosition = 0;
    private String currentHashtag;
    private GestureDetector gestureDetector;

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

    // 해시태그 사진 목록용 생성자 추가
    public static PhotoDetailDialogFragment newInstance(String photoUri, String photoName,
                                                        long photoDate, List<String> tags,
                                                        String hashtag) {
        PhotoDetailDialogFragment fragment = new PhotoDetailDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PHOTO_URI, photoUri);
        args.putString(ARG_PHOTO_NAME, photoName);
        args.putLong(ARG_PHOTO_DATE, photoDate);
        args.putStringArrayList(ARG_PHOTO_TAGS, new ArrayList<>(tags));
        args.putString(ARG_HASHTAG, hashtag);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        if (getArguments() != null) {
            currentHashtag = getArguments().getString(ARG_HASHTAG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_photo_detail_pager, container, false);

        // ViewPager2 초기화
        viewPager = view.findViewById(R.id.photoViewPager);
        ImageButton closeDetailButton = view.findViewById(R.id.closeDetailButton);

        // 닫기 버튼 리스너 설정
        closeDetailButton.setOnClickListener(v -> {
            Log.d("PhotoDetailDialog", "Close button clicked");
            dismiss();
        });

        // 제스처 감지기 초기화 (ViewPager와 별개로 다이얼로그 닫기용)
        gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // 화면 탭 시 닫기 (옵션)
                // dismiss();
                return true;
            }
        });

        loadPhotos();

        return view;
    }

    private void loadPhotos() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());

                if (currentHashtag != null) {
                    // 해시태그로 필터링된 사진들 로드
                    allPhotos = db.photoDao().getPhotosByHashtag(currentHashtag);
                } else {
                    // 모든 사진 로드 (또는 다른 로직)
                    allPhotos = db.photoDao().getAllPhotos();
                }

                // 현재 사진의 위치 찾기
                Bundle args = getArguments();
                if (args != null) {
                    String currentPhotoUri = args.getString(ARG_PHOTO_URI);
                    for (int i = 0; i < allPhotos.size(); i++) {
                        if (allPhotos.get(i).getFilePath().equals(currentPhotoUri)) {
                            currentPosition = i;
                            break;
                        }
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    setupViewPager();
                });

            } catch (Exception e) {
                Log.e("PhotoDetailDialog", "Error loading photos: " + e.getMessage(), e);
            }
        });
    }

    private void setupViewPager() {
        pagerAdapter = new PhotoDetailPagerAdapter(requireContext(), allPhotos);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(currentPosition, false);

        // 페이지 변경 콜백
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 초기 데이터는 loadPhotos에서 처리
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 전체 화면 설정
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return dialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (viewPager != null) {
            viewPager.setAdapter(null);
        }
    }
}