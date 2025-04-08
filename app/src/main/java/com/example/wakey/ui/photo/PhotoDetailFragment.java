// ui/photo/PhotoDetailFragment.java
package com.example.wakey.ui.photo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
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
        TextView locationTextView = view.findViewById(R.id.photoDetailLocationTextView);
        TextView timeTextView = view.findViewById(R.id.photoDetailTimeTextView);
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
        updateUI(
                photoImageView,
                locationTextView,
                timeTextView,
                addressTextView,
                activityChip
        );

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

            // Updated call without predictionTextView parameter
            updateUI(
                    view.findViewById(R.id.photoDetailImageView),
                    locationTextView,
                    view.findViewById(R.id.photoDetailTimeTextView),
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

            // Updated call without predictionTextView parameter
            updateUI(
                    view.findViewById(R.id.photoDetailImageView),
                    locationTextView,
                    view.findViewById(R.id.photoDetailTimeTextView),
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

    // updateUI
    private void updateUI(ImageView photoImageView,
                          TextView locationTextView, TextView timeTextView,
                          TextView addressTextView, TextView activityChip) {
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

                // 2. 이미지 분류 실행 (AI 예측 결과) - modified to use createHashtags
                try {
                    ImageClassifier classifier = new ImageClassifier(requireContext());
                    List<Pair<String, Float>> predictions = classifier.classifyImage(bitmap);

                    // Create individual hashtags instead of a single text view
                    createHashtags(predictions);

                    classifier.close();
                } catch (Exception e) {
                    // Create a single default hashtag
                    View currentView = getView();
                    if (currentView != null) {
                        LinearLayout hashtagContainer = currentView.findViewById(R.id.hashtagContainer);
                        hashtagContainer.removeAllViews();

                        TextView tagView = new TextView(requireContext());
                        tagView.setText("#Photo");
                        tagView.setTextSize(12);
                        tagView.setTextColor(Color.BLACK);

                        int paddingPixels = (int) (12 * getResources().getDisplayMetrics().density);
                        int topBottomPadding = (int) (6 * getResources().getDisplayMetrics().density);
                        tagView.setPadding(paddingPixels, topBottomPadding, paddingPixels, topBottomPadding);

                        tagView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.hash_tag_background));
                        hashtagContainer.addView(tagView);
                    }
                    e.printStackTrace();
                }
            }
        }

        // 3. 주소 정보 가져오기 (Geocoder)
        if (timelineItem.getLatLng() != null) {
            // Rest of the method remains the same...
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            new Thread(() -> {
                try {
                    List<Address> addresses = geocoder.getFromLocation(
                            timelineItem.getLatLng().latitude,
                            timelineItem.getLatLng().longitude,
                            1
                    );
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String addressStr = address.getAddressLine(0);

                        // 위치 정보를 풍부하게 가공
                        final String locationDisplay;
                        String locality = address.getLocality();
                        String subLocality = address.getSubLocality();
                        String featureName = address.getFeatureName();

                        if (locality != null && subLocality != null) {
                            locationDisplay = locality + " " + subLocality;
                        } else if (locality != null) {
                            locationDisplay = locality;
                        } else if (addressStr != null) {
                            // 주소에서 마지막 두 부분만 추출 (간소화된 위치)
                            String[] parts = addressStr.split(" ");
                            if (parts.length >= 2) {
                                locationDisplay = parts[parts.length - 2] + " " + parts[parts.length - 1];
                            } else {
                                locationDisplay = addressStr;
                            }
                        } else {
                            locationDisplay = "위치 정보 없음";
                        }

                        requireActivity().runOnUiThread(() -> {
                            // 주소는 기존처럼 전체 주소 표시
                            addressTextView.setText(addressStr);

                            // 위치 이름은 가공된 형태로 표시
                            locationTextView.setText(locationDisplay);
                        });
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            addressTextView.setText("위치 정보 없음");

                            // TimelineItem의 location 정보가 있으면 사용, 없으면 기본값
                            if (timelineItem.getLocation() != null &&
                                    !timelineItem.getLocation().equals("미상") &&
                                    !timelineItem.getLocation().equals("알 수 없는 위치")) {
                                locationTextView.setText(timelineItem.getLocation());
                            } else {
                                locationTextView.setText("위치 정보 없음");
                            }
                        });
                    }
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> {
                        addressTextView.setText("위치 정보 불러오기 실패");

                        // 실패 시에는 기존 location 정보 사용
                        if (timelineItem.getLocation() != null && !timelineItem.getLocation().isEmpty() &&
                                !timelineItem.getLocation().equals("미상") &&
                                !timelineItem.getLocation().equals("알 수 없는 위치")) {
                            locationTextView.setText(timelineItem.getLocation());
                        } else {
                            locationTextView.setText("위치 정보 없음");
                        }
                    });
                    e.printStackTrace();
                }
            }).start();
        } else {
            addressTextView.setText("위치 정보 없음");

            // 위도/경도가 없는 경우, 기존 location 정보 사용
            if (timelineItem.getLocation() != null && !timelineItem.getLocation().isEmpty() &&
                    !timelineItem.getLocation().equals("미상") &&
                    !timelineItem.getLocation().equals("알 수 없는 위치")) {
                locationTextView.setText(timelineItem.getLocation());
            } else {
                locationTextView.setText("위치 정보 없음");
            }
        }

        // 4. 날짜 형식 수정: YYYY.MM.DD(요일) HH:MM 형식으로
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

    /**
     * Creates individual hashtag views from classifier predictions
     */
    private void createHashtags(List<Pair<String, Float>> predictions) {
        // Find the container
        LinearLayout hashtagContainer = getView().findViewById(R.id.hashtagContainer);

        // Clear existing tags
        hashtagContainer.removeAllViews();

        // Create individual tag for each prediction (limit to 5)
        int count = 0;
        for (Pair<String, Float> pred : predictions) {
            if (count >= 5) break; // Limit to 5 tags maximum

            // Extract main term before comma or parenthesis
            String term = pred.first.split(",")[0].trim();
            term = term.split("\\(")[0].trim();
            String hashtag = "#" + term.replace(" ", "");

            // Create a TextView for the hashtag
            TextView tagView = new TextView(requireContext());
            tagView.setText(hashtag);
            tagView.setTextSize(12);
            tagView.setTextColor(Color.BLACK);

            // Set padding and margin
            int paddingPixels = (int) (12 * getResources().getDisplayMetrics().density);
            int topBottomPadding = (int) (6 * getResources().getDisplayMetrics().density);
            tagView.setPadding(paddingPixels, topBottomPadding, paddingPixels, topBottomPadding);

            // Create layout parameters with margin
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (count < predictions.size() - 1) {
                int marginPixels = (int) (6 * getResources().getDisplayMetrics().density);
                params.rightMargin = marginPixels;
            }
            tagView.setLayoutParams(params);

            // Set the background
            tagView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.hash_tag_background));

            // Add to container
            hashtagContainer.addView(tagView);
            count++;
        }

        // If no predictions or error, add a single default tag
        if (count == 0) {
            TextView tagView = new TextView(requireContext());
            tagView.setText("#Photo");
            tagView.setTextSize(12);
            tagView.setTextColor(Color.BLACK);

            int paddingPixels = (int) (12 * getResources().getDisplayMetrics().density);
            int topBottomPadding = (int) (6 * getResources().getDisplayMetrics().density);
            tagView.setPadding(paddingPixels, topBottomPadding, paddingPixels, topBottomPadding);

            tagView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.hash_tag_background));
            hashtagContainer.addView(tagView);
        }
    }
}

