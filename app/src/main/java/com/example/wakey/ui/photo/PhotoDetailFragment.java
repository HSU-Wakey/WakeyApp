package com.example.wakey.ui.photo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.ui.timeline.TimelineManager;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.tflite.ImageClassifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.drawable.BitmapDrawable;
import android.widget.Toast;

import com.example.wakey.tflite.ESRGANUpscaler;

public class PhotoDetailFragment extends DialogFragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String ARG_TIMELINE_ITEM = "timelineItem";
    private static final String ARG_DATE = "date";
    private static final String ARG_POSITION = "position";

    private TimelineItem timelineItem;
    private String currentDate;
    private int currentPosition = 0;
    private List<TimelineItem> timelineItems;

    private boolean isUpscaled = false;
    private Bitmap originalBitmap;
    private Bitmap upscaledBitmap;

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
        ProgressBar progressBar = view.findViewById(R.id.progressBarUpscale);

        // 업스케일 버튼 참조 및 리스너 추가
        ImageButton upscaleButton = view.findViewById(R.id.upscaleButton);
        upscaleButton.setOnClickListener(v -> {
            if (originalBitmap == null) {
                Drawable drawable = photoImageView.getDrawable();
                if (drawable instanceof BitmapDrawable) {
                    originalBitmap = ((BitmapDrawable) drawable).getBitmap();
                } else {
                    Toast.makeText(getContext(), "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (isUpscaled) {
                photoImageView.setImageBitmap(originalBitmap);
                isUpscaled = false;
                Toast.makeText(getContext(), "원본 이미지 보기", Toast.LENGTH_SHORT).show();
            } else if (upscaledBitmap != null) {
                photoImageView.setImageBitmap(upscaledBitmap);
                isUpscaled = true;
                Toast.makeText(getContext(), "업스케일된 이미지 보기", Toast.LENGTH_SHORT).show();
            } else {
                progressBar.setVisibility(View.VISIBLE);

                new Thread(() -> {
                    try {
                        ESRGANUpscaler upscaler = new ESRGANUpscaler(requireContext());
                        upscaledBitmap = upscaler.upscale(originalBitmap);

                        requireActivity().runOnUiThread(() -> {
                            photoImageView.setImageBitmap(upscaledBitmap);
                            isUpscaled = true;
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "이미지가 선명하게 업스케일 되었습니다!", Toast.LENGTH_SHORT).show();
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        requireActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "업스케일 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            }
        });

        // 내비게이션 버튼 활성화 상태 설정
        updateNavigationButtons(btnPrevious, btnNext);

        // 내비게이션 버튼 리스너 설정
        btnPrevious.setOnClickListener(v -> navigateToPrevious());
        btnNext.setOnClickListener(v -> navigateToNext());

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

        // 업스케일 상태 리셋
        isUpscaled = false;
        originalBitmap = null;
        upscaledBitmap = null;

        // UI 업데이트
        updateNavigationUI();
    }

    private void navigateToNext() {
        if (timelineItems == null || currentPosition >= timelineItems.size() - 1) {
            return;
        }

        currentPosition++;
        timelineItem = timelineItems.get(currentPosition);

        // 업스케일 상태 리셋
        isUpscaled = false;
        originalBitmap = null;
        upscaledBitmap = null;

        // UI 업데이트
        updateNavigationUI();
    }

    private void updateNavigationUI() {
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
                    // ✅ 모바일넷 해시태그 생성 로직 복원
                    List<Pair<String, Float>> predictions = null;

                    // 기존 예측 결과가 있는지 확인
                    Map<String, Float> existingPredictions = timelineItem.getDetectedObjectPairs();
                    if (existingPredictions != null && !existingPredictions.isEmpty()) {
                        Log.d("HASHTAG_CHECK", "🟢 기존 예측 사용: " + existingPredictions);
                        predictions = new ArrayList<>();
                        for (Map.Entry<String, Float> entry : existingPredictions.entrySet()) {
                            predictions.add(new Pair<>(entry.getKey(), entry.getValue()));
                        }
                    } else {
                        // 예측 결과가 없으면 DB에서 다시 조회
                        Log.d("HASHTAG_CHECK", "🔄 DB에서 예측 결과 조회 시작");
                        executor.execute(() -> {
                            AppDatabase db = AppDatabase.getInstance(requireContext());
                            Photo latestPhoto = db.photoDao().getPhotoByFilePath(photoPath);

                            List<Pair<String, Float>> dbPredictions = null;
                            if (latestPhoto != null && latestPhoto.getDetectedObjectPairs() != null) {
                                dbPredictions = new ArrayList<>(latestPhoto.getDetectedObjectPairs());

                                // TimelineItem 업데이트 - Photo의 List<Pair>를 Map으로 변환
                                Map<String, Float> predictionsMap = new java.util.HashMap<>();
                                for (Pair<String, Float> pred : dbPredictions) {
                                    predictionsMap.put(pred.first, pred.second);
                                }
                                timelineItem.setDetectedObjectPairs(predictionsMap);
                                Log.d("HASHTAG_CHECK", "✅ DB에서 조회: " + dbPredictions);
                            } else {
                                // DB에도 없으면 모델로 새로 분석
                                Log.d("HASHTAG_CHECK", "🔴 예측 없음 → 모델 재분석 시작");
                                ImageClassifier classifier = null;
                                try {
                                    classifier = new ImageClassifier(requireContext());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                dbPredictions = classifier.classifyImage(bitmap);
                                classifier.close();

                                // TimelineItem 업데이트
                                Map<String, Float> predictionsMap = new java.util.HashMap<>();
                                for (Pair<String, Float> pred : dbPredictions) {
                                    predictionsMap.put(pred.first, pred.second);
                                }
                                timelineItem.setDetectedObjectPairs(predictionsMap);

                                // DB 업데이트
                                db.photoDao().updateDetectedObjectPairs(photoPath, dbPredictions);
                                Log.d("HASHTAG_CHECK", "💾 새로운 예측 DB 저장: " + dbPredictions);
                            }

                            // UI 업데이트
                            final List<Pair<String, Float>> finalPredictions = dbPredictions;
                            requireActivity().runOnUiThread(() -> {
                                createHashtags(finalPredictions);
                            });
                        });
                        return; // 백그라운드 처리이므로 여기서 리턴
                    }

                    Log.d("HASHTAG_CHECK", "🔖 최종 예측값: " + predictions);
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
        Log.d("HASHTAG_CHECK", "🏷️ 해시태그 생성 진입, 예측 개수: " + (predictions != null ? predictions.size() : 0));
        View currentView = getView();
        if (currentView == null) {
            Log.e("HASHTAG_CHECK", "❌ 뷰가 null입니다");
            return;
        }

        LinearLayout hashtagContainer = currentView.findViewById(R.id.hashtagContainer);
        if (hashtagContainer == null) {
            Log.e("HASHTAG_CHECK", "❌ hashtagContainer를 찾을 수 없습니다");
            return;
        }

        hashtagContainer.removeAllViews();

        if (predictions == null || predictions.isEmpty()) {
            Log.w("HASHTAG_CHECK", "❌ 예측 결과가 비어있음");
            // 기본 태그 추가
            addDefaultHashtag(hashtagContainer);
            return;
        }

        int count = 0;
        for (Pair<String, Float> pred : predictions) {
            if (pred.first == null || pred.first.isEmpty()) {
                continue;
            }

            // 예측 항목에서 해시태그 추출
            String term = pred.first.split(",")[0].trim();
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

            Log.d("HASHTAG_CHECK", "✅ 해시태그 추가: " + hashtag);
        }

        // 예측 결과가 있었지만 유효한 해시태그가 하나도 만들어지지 않은 경우
        if (count == 0) {
            Log.w("HASHTAG_CHECK", "⚠️ 유효한 해시태그가 없음, 기본 태그 추가");
            addDefaultHashtag(hashtagContainer);
        }

        Log.d("HASHTAG_CHECK", "🏁 해시태그 생성 완료, 총 " + count + "개");
    }

    /**
     * 기본 해시태그 추가
     */
    private void addDefaultHashtag(LinearLayout hashtagContainer) {
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