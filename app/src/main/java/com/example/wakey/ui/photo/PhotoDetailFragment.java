// ui/photo/PhotoDetailFragment.java
package com.example.wakey.ui.photo;

import static android.content.ContentValues.TAG;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.repository.TimelineManager;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.tflite.BeitClassifier;
import com.example.wakey.util.ToastManager;
import com.example.wakey.util.Upscaler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoDetailFragment extends DialogFragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        executor.execute(() -> {
            if (currentDate == null && timelineItem != null && timelineItem.getTime() != null) {
                currentDate = DateUtil.getFormattedDateString(timelineItem.getTime());
            }

            if (currentDate != null) {
                timelineItems = TimelineManager.getInstance(requireContext())
                        .loadTimelineForDate(currentDate);

                // currentPosition 계산
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

                requireActivity().runOnUiThread(() -> {
                    // 타임라인 로딩 후 UI 초기화 필요 시 여기에
                    View view = getView();
                    if (view != null) {
                        updateUI(
                                view.findViewById(R.id.photoDetailImageView),
                                view.findViewById(R.id.photoDetailLocationTextView),
                                view.findViewById(R.id.photoDetailTimeTextView),
                                view.findViewById(R.id.photoDetailAddressTextView),
                                view.findViewById(R.id.activityChip)
                        );
                        updateNavigationButtons(
                                view.findViewById(R.id.btnPrevious),
                                view.findViewById(R.id.btnNext)
                        );
                    }
                });
            }
        });


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
        ImageButton upscaleButton = view.findViewById(R.id.upscaleButton);
        upscaleButton.setOnClickListener(v -> {
            executor.execute(() -> {
                Log.d(TAG, "🔍 업스케일링 시작");
                long startTime = System.currentTimeMillis();

                try {
                    Uri photoUri = Uri.parse(timelineItem.getPhotoPath());
                    Log.d(TAG, "📂 원본 이미지 URI: " + photoUri);

                    // ▶ 여기서 ContentResolver로 InputStream 열기
                    InputStream inputStream = requireContext()
                            .getContentResolver()
                            .openInputStream(photoUri);

                    Bitmap original = BitmapFactory.decodeStream(inputStream);

                    if (inputStream != null) {
                        inputStream.close();
                    }

                    if (original == null) {
                        Log.e(TAG, "❌ 원본 이미지를 불러오지 못했습니다.");
                        return;
                    }
                    Log.d(TAG, "🖼️ 원본 이미지 로딩 완료");

                    Upscaler upscaler = new Upscaler(requireContext());
                    Log.d(TAG, "⚙️ 업스케일러 초기화 완료");

                    Bitmap enhanced = upscaler.upscale(original);
                    Log.d(TAG, "✨ 업스케일링 완료");

                    upscaler.close();
                    Log.d(TAG, "🛑 업스케일러 종료");

                    String newPath = saveEnhancedImageToFile(enhanced, timelineItem.getPhotoPath());
                    Log.d(TAG, "💾 업스케일된 이미지 저장 완료: " + newPath);

                    timelineItem.setPhotoPath(newPath);

                    long endTime = System.currentTimeMillis();
                    Log.d(TAG, "⏱️ 업스케일링 소요 시간: " + (endTime - startTime) + "ms");

                    requireActivity().runOnUiThread(() -> {
                        Glide.with(requireContext())
                                .load(newPath)
                                .into(photoImageView);
                        ToastManager.getInstance().setContext(requireContext());
                        ToastManager.getInstance().showToast("✅ 업스케일 완료!");
                    });

                } catch (Exception e) {
                    Log.e(TAG, "❌ 업스케일링 중 오류 발생: " + e.getMessage(), e);
                    requireActivity().runOnUiThread(() -> {
                        ToastManager.getInstance().setContext(requireContext());
                        ToastManager.getInstance().showToast("⚠️ 이미지 업스케일 실패");
                    });
                }
            });
        });



        // 나머지 UI 설정 및 리스너들...
        updateNavigationButtons(btnPrevious, btnNext);
        updateUI(photoImageView, locationTextView, timeTextView, addressTextView, activityChip);

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
    }

    // updateUI
    private void updateUI(ImageView photoImageView,
                          TextView locationTextView, TextView timeTextView,
                          TextView addressTextView, TextView activityChip) {

        if (timelineItem == null) return;

        // 1. 사진 이미지 로드
        String photoPath = timelineItem.getPhotoPath();
        if (photoPath != null) {
            Glide.with(this).load(photoPath).into(photoImageView);

            try {
                Bitmap bitmap = BitmapFactory.decodeStream(
                        requireContext().getContentResolver().openInputStream(android.net.Uri.parse(photoPath))
                );

                if (bitmap != null) {
                    List<Pair<String, Float>> predictions;

                    if (timelineItem.getDetectedObjectPairs() != null && !timelineItem.getDetectedObjectPairs().isEmpty()) {
                        predictions = timelineItem.getDetectedObjectPairs();
                    } else {
                        BeitClassifier classifier = new BeitClassifier(requireContext());
                        predictions = classifier.classifyImage(bitmap);
                        classifier.close();

                        timelineItem.setDetectedObjectPairs(predictions);
                        executor.execute(() -> {
                            AppDatabase db = AppDatabase.getInstance(requireContext());
                            db.photoDao().updateDetectedObjectPairs(timelineItem.getPhotoPath(), predictions);
                        });
                    }

                    createHashtags(predictions);
                }

            } catch (Exception e) {
                Log.e("HASHTAG", "이미지 분석 중 오류: " + e.getMessage(), e);
            }
        }

        // 2. 주소 및 위치
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
                        Address address = addresses.get(0);

                        String adminArea = address.getAdminArea();
                        String subAdminArea = address.getSubAdminArea();
                        String subLocality = address.getSubLocality();

                        StringBuilder locationBuilder = new StringBuilder();
                        if (adminArea != null) locationBuilder.append(adminArea).append(" ");
                        if (subAdminArea != null) locationBuilder.append(subAdminArea).append(" ");
                        if (subLocality != null) locationBuilder.append(subLocality);

                        String locationStr = locationBuilder.toString().trim();
                        if (locationStr.isEmpty()) locationStr = "위치 정보 없음";

                        String addressStr = address.getAddressLine(0);

                        // DB에 주소 업데이트
                        String finalLocationStr1 = locationStr;
                        executor.execute(() -> {
                            AppDatabase db = AppDatabase.getInstance(requireContext());
                            db.photoDao().updateFullAddress(timelineItem.getPhotoPath(), finalLocationStr1);
                        });

                        String finalLocationStr = locationStr;
                        requireActivity().runOnUiThread(() -> {
                            addressTextView.setText(addressStr);
                            locationTextView.setText(finalLocationStr);
                            locationTextView.setVisibility(View.VISIBLE);
                        });
                    } else {
                        requireActivity().runOnUiThread(() -> {
                            addressTextView.setText("위치 정보 없음");
                            locationTextView.setText("위치 정보 없음");
                            locationTextView.setVisibility(View.VISIBLE);
                        });
                    }

                } catch (Exception e) {
                    Log.e("GEO", "주소 변환 실패: " + e.getMessage(), e);
                    requireActivity().runOnUiThread(() -> {
                        addressTextView.setText("위치 정보 불러오기 실패");
                        locationTextView.setText("위치 정보 없음");
                        locationTextView.setVisibility(View.VISIBLE);
                    });
                }
            }).start();
        } else {
            addressTextView.setText("위치 정보 없음");

            String fallbackLoc = timelineItem.getLocation();
            if (fallbackLoc == null || fallbackLoc.trim().isEmpty()) {
                fallbackLoc = "위치 정보 없음";
            }

            locationTextView.setText(fallbackLoc);
            locationTextView.setVisibility(View.VISIBLE);
        }

        // 3. 시간
        String dateTimeStr = DateUtil.formatDate(timelineItem.getTime(), "yyyy.MM.dd(E) HH:mm");
        timeTextView.setText(dateTimeStr);

        // 4. 활동 유형
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
        View currentView = getView();
        if (currentView == null) {
            return;
        }

        LinearLayout hashtagContainer = getView().findViewById(R.id.hashtagContainer);
        if (hashtagContainer == null) {
            return;
        }

        hashtagContainer.removeAllViews();

        int count = 0;
        for (Pair<String, Float> pred : predictions) {
            // 예측 항목에서 해시태그 추출
            String term = pred.first != null ? pred.first.split(",")[0].trim() : "";
            String hashtag = "#" + term.replace(" ", "");

            // TextView 생성
            TextView tagView = new TextView(requireContext());
            tagView.setText(hashtag);
            tagView.setTextSize(12);
            tagView.setTextColor(Color.BLACK);

            int paddingPixels = (int) (12 * getResources().getDisplayMetrics().density);
            int topBottomPadding = (int) (6 * getResources().getDisplayMetrics().density);
            tagView.setPadding(paddingPixels, topBottomPadding, paddingPixels, topBottomPadding);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            int marginPixels = (int) (6 * getResources().getDisplayMetrics().density);
            params.rightMargin = marginPixels;
            tagView.setLayoutParams(params);

            tagView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.hash_tag_background));
            hashtagContainer.addView(tagView);
            count++;
        }

        // 예측 결과 없을 경우 기본 태그
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

    private String saveEnhancedImageToFile(Bitmap bitmap, String originalPath) throws IOException, IOException {
        File dir = new File(requireContext().getFilesDir(), "enhanced");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = "enhanced_" + System.currentTimeMillis() + ".jpg";
        File file = new File(dir, fileName);

        FileOutputStream out = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
        out.flush();
        out.close();

        return file.getAbsolutePath();
    }

}