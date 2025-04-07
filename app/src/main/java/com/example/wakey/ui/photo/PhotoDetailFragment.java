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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.TimelineManager;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.tflite.ImageClassifier;
import com.example.wakey.util.ToastManager;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class PhotoDetailFragment extends DialogFragment {

    private static final String ARG_TIMELINE_ITEM = "timelineItem";
    private static final String ARG_DATE = "date";
    private static final String ARG_POSITION = "position";

    private TimelineItem timelineItem;
    private String currentDate;
    private int currentPosition = 0;
    private List<TimelineItem> timelineItems;

    public static PhotoDetailFragment newInstance(TimelineItem item) {
        PhotoDetailFragment fragment = new PhotoDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TIMELINE_ITEM, item);
        fragment.setArguments(args);
        return fragment;
    }

    public static PhotoDetailFragment newInstance(TimelineItem item, String date, int position) {
        PhotoDetailFragment fragment = new PhotoDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TIMELINE_ITEM, item);
        args.putString(ARG_DATE, date);
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            if (getArguments().containsKey(ARG_TIMELINE_ITEM)) {
                timelineItem = (TimelineItem) getArguments().getSerializable(ARG_TIMELINE_ITEM);
            }
            if (getArguments().containsKey(ARG_DATE)) {
                currentDate = getArguments().getString(ARG_DATE);
            }
            if (getArguments().containsKey(ARG_POSITION)) {
                currentPosition = getArguments().getInt(ARG_POSITION);
            }
        }

        // 현재 날짜의 모든 타임라인 아이템 가져오기
        loadTimelineItems();

        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
    }

    private void loadTimelineItems() {
        if (currentDate == null && timelineItem != null && timelineItem.getTime() != null) {
            // 날짜 정보가 없으면 현재 아이템의 날짜로 설정
            currentDate = DateUtil.getFormattedDateString(timelineItem.getTime());
        }

        if (currentDate != null) {
            // 현재 날짜의 모든 타임라인 가져오기
            timelineItems = TimelineManager.getInstance(requireContext())
                    .loadTimelineForDate(currentDate);

            // 현재 위치 찾기
            if (currentPosition == 0 && timelineItem != null) {
                for (int i = 0; i < timelineItems.size(); i++) {
                    TimelineItem item = timelineItems.get(i);
                    if (item.getPhotoPath() != null &&
                            item.getPhotoPath().equals(timelineItem.getPhotoPath())) {
                        currentPosition = i;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_detail, container, false);

        // 기본 뷰 참조 찾기
        ImageView photoImageView = view.findViewById(R.id.photoDetailImageView);
        TextView captionTextView = view.findViewById(R.id.photoDetailCaptionTextView);
        TextView locationTextView = view.findViewById(R.id.photoDetailLocationTextView);
        TextView timeTextView = view.findViewById(R.id.photoDetailTimeTextView);
        TextView predictionTextView = view.findViewById(R.id.photoDetailPredictionTextView);
        TextView addressTextView = view.findViewById(R.id.photoDetailAddressTextView);
        TextView activityChip = view.findViewById(R.id.activityChip);

        // 버튼 참조 찾기
        ImageButton closeButton = view.findViewById(R.id.closeButton);
        ImageButton btnPrevious = view.findViewById(R.id.btnPrevious);
        ImageButton btnNext = view.findViewById(R.id.btnNext);

        // 내비게이션 버튼 활성화 상태 설정
        updateNavigationButtons(btnPrevious, btnNext);

        // 내비게이션 버튼 리스너 설정
        btnPrevious.setOnClickListener(v -> {
            navigateToPrevious();
        });

        btnNext.setOnClickListener(v -> {
            navigateToNext();
        });

        // 현재 타임라인 아이템이 있으면 UI 업데이트
        updateUI(photoImageView, captionTextView, locationTextView, timeTextView,
                predictionTextView, addressTextView, activityChip);

        // 닫기 버튼
        closeButton.setOnClickListener(v -> dismiss());

        return view;
    }

    private void updateNavigationButtons(ImageButton btnPrevious, ImageButton btnNext) {
        if (timelineItems == null || timelineItems.isEmpty()) {
            btnPrevious.setVisibility(View.INVISIBLE);
            btnNext.setVisibility(View.INVISIBLE);
            return;
        }

        // 이전 버튼 활성화 여부
        btnPrevious.setVisibility(currentPosition > 0 ? View.VISIBLE : View.INVISIBLE);

        // 다음 버튼 활성화 여부
        btnNext.setVisibility(currentPosition < timelineItems.size() - 1 ? View.VISIBLE : View.INVISIBLE);
    }

    private void navigateToPrevious() {
        if (timelineItems == null || currentPosition <= 0) {
            return;
        }

        currentPosition--;
        timelineItem = timelineItems.get(currentPosition);

        // UI 업데이트
        View view = getView();
        if (view != null) {
            // 위치 정보 직접 설정 확인
            TextView locationTextView = view.findViewById(R.id.photoDetailLocationTextView);
            if (timelineItem.getLocation() != null && !timelineItem.getLocation().isEmpty()) {
                locationTextView.setText(timelineItem.getLocation());
            } else {
                locationTextView.setText("장소"); // 기본값
            }

            updateUI(
                    view.findViewById(R.id.photoDetailImageView),
                    view.findViewById(R.id.photoDetailCaptionTextView),
                    locationTextView,
                    view.findViewById(R.id.photoDetailTimeTextView),
                    view.findViewById(R.id.photoDetailPredictionTextView),
                    view.findViewById(R.id.photoDetailAddressTextView),
                    view.findViewById(R.id.activityChip)
            );

            // 네비게이션 버튼 상태 업데이트
            updateNavigationButtons(
                    view.findViewById(R.id.btnPrevious),
                    view.findViewById(R.id.btnNext)
            );
        }

        // 이동 알림 - 나중에 삭제
        ToastManager.getInstance().showToast("이전 사진으로 이동했습니다");
    }

    private void navigateToNext() {
        if (timelineItems == null || currentPosition >= timelineItems.size() - 1) {
            return;
        }

        currentPosition++;
        timelineItem = timelineItems.get(currentPosition);

        // UI 업데이트
        View view = getView();
        if (view != null) {
            // 위치 정보 직접 설정 확인
            TextView locationTextView = view.findViewById(R.id.photoDetailLocationTextView);
            if (timelineItem.getLocation() != null && !timelineItem.getLocation().isEmpty()) {
                locationTextView.setText(timelineItem.getLocation());
            } else {
                locationTextView.setText("장소"); // 기본값
            }

            updateUI(
                    view.findViewById(R.id.photoDetailImageView),
                    view.findViewById(R.id.photoDetailCaptionTextView),
                    locationTextView,
                    view.findViewById(R.id.photoDetailTimeTextView),
                    view.findViewById(R.id.photoDetailPredictionTextView),
                    view.findViewById(R.id.photoDetailAddressTextView),
                    view.findViewById(R.id.activityChip)
            );

            // 네비게이션 버튼 상태 업데이트
            updateNavigationButtons(
                    view.findViewById(R.id.btnPrevious),
                    view.findViewById(R.id.btnNext)
            );
        }

        // 이동 알림 - 나중에 삭제
        ToastManager.getInstance().showToast("다음 사진으로 이동했습니다");
    }

    private void updateUI(ImageView photoImageView, TextView captionTextView,
                          TextView locationTextView, TextView timeTextView,
                          TextView predictionTextView, TextView addressTextView,
                          TextView activityChip) {
        if (timelineItem == null) {
            return;
        }

        // 1. 사진 이미지 로드
        if (timelineItem.getPhotoPath() != null) {
            Glide.with(this)
                    .load(timelineItem.getPhotoPath())
                    .into(photoImageView);

            File imgFile = new File(timelineItem.getPhotoPath());
            if (imgFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                // 2. 이미지 분류 실행 (AI 예측 결과)
                try {
                    ImageClassifier classifier = new ImageClassifier(requireContext());
                    List<Pair<String, Float>> predictions = classifier.classifyImage(bitmap);

                    // 텍스트 포맷팅 변경 - 심플하게
                    StringBuilder sb = new StringBuilder();
                    for (Pair<String, Float> pred : predictions) {
                        sb.append("• ").append(pred.first)
                                .append(" (").append(String.format("%.2f", pred.second)).append("%)")
                                .append("\n");
                    }
                    predictionTextView.setText(sb.toString());

                    classifier.close();
                } catch (Exception e) {
                    predictionTextView.setText("• 이미지 분석 실패");
                    e.printStackTrace();
                }
            }
        }

        // 3. 주소 정보 가져오기 (Geocoder)
        if (timelineItem.getLatLng() != null) {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            new Thread(() -> {
                try {
                    List<Address> addresses = geocoder.getFromLocation(
                            timelineItem.getLatLng().latitude,
                            timelineItem.getLatLng().longitude,
                            1
                    );
                    if (addresses != null && !addresses.isEmpty()) {
                        String addressStr = addresses.get(0).getAddressLine(0);
                        requireActivity().runOnUiThread(() ->
                                addressTextView.setText(addressStr));
                    } else {
                        requireActivity().runOnUiThread(() ->
                                addressTextView.setText("위치 정보 없음"));
                    }
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            addressTextView.setText("위치 정보 불러오기 실패"));
                    e.printStackTrace();
                }
            }).start();
        } else {
            addressTextView.setText("위치 정보 없음");
        }

        // 4. 타임라인 텍스트 설정
        captionTextView.setText(timelineItem.getDescription());

        // 위치 정보 확실히 설정
        if (timelineItem.getLocation() != null && !timelineItem.getLocation().isEmpty()) {
            locationTextView.setText(timelineItem.getLocation());
        } else {
            locationTextView.setText("장소 미상");
        }

        // 날짜 형식 수정: YYYY.MM.DD(요일) HH:MM 형식으로
        String dateTimeStr = DateUtil.formatDate(timelineItem.getTime(), "yyyy.MM.dd(E) HH:mm");
        timeTextView.setText(dateTimeStr);

        // 5. 활동 유형 설정
        if (timelineItem.getActivityType() != null && !timelineItem.getActivityType().isEmpty()) {
            activityChip.setText(timelineItem.getActivityType());
            activityChip.setVisibility(View.VISIBLE);
        } else {
            activityChip.setVisibility(View.GONE);
        }
    }
}