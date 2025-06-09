package com.example.wakey.ui.photo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.ui.search.HashtagPhotosActivity;
import com.example.wakey.ui.timeline.TimelineManager;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.tflite.ESRGANUpscaler;
import com.example.wakey.tflite.ImageClassifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.net.Uri;
import java.io.File;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

public class PhotoDetailFragment extends DialogFragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String ARG_TIMELINE_ITEM = "timelineItem";
    private static final String ARG_DATE = "date";
    private static final String ARG_POSITION = "position";

    private TimelineItem timelineItem;
    private String currentDate;
    private int currentPosition = 0;
    private List<TimelineItem> timelineItems;

    // 업스케일 관련 변수
    private boolean isUpscaled = false;
    private Bitmap originalBitmap;
    private Bitmap upscaledBitmap;

    // 풀스크린 관련 변수
    private boolean isFullScreen = false;
    private View photoInfo;
    private ImageView photoImageView;

    // 제스처 감지
    private GestureDetector gestureDetector;

    public static PhotoDetailFragment newInstance(TimelineItem item) {
        PhotoDetailFragment fragment = new PhotoDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TIMELINE_ITEM, item);
        fragment.setArguments(args);
        return fragment;
    }

    public static PhotoDetailFragment newInstance(TimelineItem item, String date, int position) {
        PhotoDetailFragment fragment = new PhotoDetailFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TIMELINE_ITEM, item);
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    timelineItem = getArguments().getParcelable(ARG_TIMELINE_ITEM, TimelineItem.class);
                } else {
                    timelineItem = getArguments().getParcelable(ARG_TIMELINE_ITEM);
                }
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

    private void loadTimelineItems() {
        if (currentDate == null && timelineItem != null && timelineItem.getTime() != null) {
            currentDate = DateUtil.getFormattedDateString(timelineItem.getTime());
        }

        if (currentDate != null) {
            timelineItems = TimelineManager.getInstance(requireContext())
                    .loadTimelineForDate(currentDate);

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

        // 뷰 참조 저장
        photoImageView = view.findViewById(R.id.photoDetailImageView);
        photoInfo = view.findViewById(R.id.photoInfo); // 기존 정보 섹션 사용

        // 기본 뷰 참조 찾기
        TextView locationTextView = view.findViewById(R.id.photoDetailLocationTextView);
        TextView timeTextView = view.findViewById(R.id.photoDetailTimeTextView);
        TextView addressTextView = view.findViewById(R.id.photoDetailAddressTextView);
        TextView activityChip = view.findViewById(R.id.activityChip);

        // 버튼 참조 찾기
        ImageButton closeButton = view.findViewById(R.id.closeButton);
        ImageButton btnPrevious = view.findViewById(R.id.btnPrevious);
        ImageButton btnNext = view.findViewById(R.id.btnNext);
        ProgressBar progressBar = view.findViewById(R.id.progressBarUpscale);

        // 제스처 감지기 초기화
        initGestureDetector();

        // 이미지뷰에 터치 리스너 설정
        photoImageView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

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

    private void initGestureDetector() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                // 사진 클릭 시 풀스크린 토글
                toggleFullScreen();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // 가로 스와이프인지 확인 (세로 스와이프보다 가로 스와이프가 더 클 때)
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // 오른쪽 스와이프 - 이전 사진
                            navigateToPrevious();
                        } else {
                            // 왼쪽 스와이프 - 다음 사진
                            navigateToNext();
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void toggleFullScreen() {
        if (photoInfo == null) return;

        isFullScreen = !isFullScreen;

        if (isFullScreen) {
            // 풀스크린 모드: 상세 정보 숨기기
            Animation fadeOut = AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_out);
            fadeOut.setDuration(200);
            photoInfo.startAnimation(fadeOut);
            photoInfo.setVisibility(View.GONE);

            // 사진을 중앙에 확대 표시
            photoImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        } else {
            // 일반 모드: 상세 정보 보이기
            photoInfo.setVisibility(View.VISIBLE);
            Animation fadeIn = AnimationUtils.loadAnimation(getContext(), android.R.anim.fade_in);
            fadeIn.setDuration(200);
            photoInfo.startAnimation(fadeIn);

            // 사진 크기 원래대로
            photoImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
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
        resetUpscaleState();

        // UI 업데이트
        updateCurrentPhoto();
    }

    private void navigateToNext() {
        if (timelineItems == null || currentPosition >= timelineItems.size() - 1) {
            return;
        }

        currentPosition++;
        timelineItem = timelineItems.get(currentPosition);

        // 업스케일 상태 리셋
        resetUpscaleState();

        // UI 업데이트
        updateCurrentPhoto();
    }

    private void resetUpscaleState() {
        isUpscaled = false;
        originalBitmap = null;
        upscaledBitmap = null;
    }

    private void updateCurrentPhoto() {
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

    private void updateNavigationUI() {
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
            Log.d("PhotoDetailFragment", "Loading image from path: " + photoPath);

            try {
                if (photoPath.startsWith("content://")) {
                    // 콘텐츠 URI 형식
                    Uri contentUri = Uri.parse(photoPath);
                    Glide.with(requireContext())
                            .load(contentUri)
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    Log.e("PhotoDetailFragment", "Image load failed: " + (e != null ? e.getMessage() : "unknown error"));
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    Log.d("PhotoDetailFragment", "Image loaded successfully");
                                    if (resource instanceof BitmapDrawable) {
                                        originalBitmap = ((BitmapDrawable) resource).getBitmap();
                                    }
                                    return false;
                                }
                            })
                            .into(photoImageView);
                } else if (photoPath.startsWith("file://")) {
                    // 파일 URI 형식
                    Uri fileUri = Uri.parse(photoPath);
                    Glide.with(requireContext())
                            .load(fileUri)
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    Log.e("PhotoDetailFragment", "Image load failed: " + (e != null ? e.getMessage() : "unknown error"));
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    Log.d("PhotoDetailFragment", "Image loaded successfully");
                                    if (resource instanceof BitmapDrawable) {
                                        originalBitmap = ((BitmapDrawable) resource).getBitmap();
                                    }
                                    return false;
                                }
                            })
                            .into(photoImageView);
                } else {
                    // 일반 파일 경로로 간주
                    File imageFile = new File(photoPath);
                    if (imageFile.exists() && imageFile.canRead()) {
                        Log.d("PhotoDetailFragment", "Loading from file path, file exists: " + imageFile.length() + " bytes");

                        Glide.with(requireContext())
                                .load(imageFile)
                                .listener(new RequestListener<Drawable>() {
                                    @Override
                                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                        Log.e("PhotoDetailFragment", "Image load failed from file: " + (e != null ? e.getMessage() : "unknown error"));
                                        // 파일 로드 실패 시 URI로 다시 시도
                                        try {
                                            Uri uri = Uri.parse(photoPath);
                                            Glide.with(requireContext()).load(uri).into(photoImageView);
                                        } catch (Exception ex) {
                                            Log.e("PhotoDetailFragment", "URI fallback also failed: " + ex.getMessage());
                                        }
                                        return false;
                                    }

                                    @Override
                                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                        Log.d("PhotoDetailFragment", "Image loaded successfully from file");
                                        if (resource instanceof BitmapDrawable) {
                                            originalBitmap = ((BitmapDrawable) resource).getBitmap();
                                        }
                                        return false;
                                    }
                                })
                                .into(photoImageView);
                    } else {
                        Log.e("PhotoDetailFragment", "File does not exist or cannot be read: " + photoPath);

                        // 파일이 존재하지 않으면 URI로 시도
                        try {
                            Uri uri = Uri.parse(photoPath);
                            Glide.with(requireContext())
                                    .load(uri)
                                    .listener(new RequestListener<Drawable>() {
                                        @Override
                                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                            Log.e("PhotoDetailFragment", "All attempts to load image failed");
                                            return false;
                                        }

                                        @Override
                                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                            Log.d("PhotoDetailFragment", "Image loaded successfully as URI");
                                            if (resource instanceof BitmapDrawable) {
                                                originalBitmap = ((BitmapDrawable) resource).getBitmap();
                                            }
                                            return false;
                                        }
                                    })
                                    .into(photoImageView);
                        } catch (Exception e) {
                            Log.e("PhotoDetailFragment", "Failed to parse URI: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("PhotoDetailFragment", "Exception while loading image: " + e.getMessage(), e);
            }

            // 먼저 DB에서 저장된 해시태그 확인
            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                String savedHashtags = db.photoDao().getHashtagsByPath(photoPath);

                if (savedHashtags != null && !savedHashtags.isEmpty()) {
                    // 저장된 해시태그가 있으면 사용
                    List<android.util.Pair<String, Float>> pairs = new ArrayList<>();
                    String[] tags = savedHashtags.split("#");
                    for (String tag : tags) {
                        if (!tag.trim().isEmpty()) {
                            pairs.add(new android.util.Pair<>(tag.trim(), 1.0f));
                        }
                    }

                    requireActivity().runOnUiThread(() -> {
                        createHashtags(pairs);
                    });
                } else {
                    // 저장된 해시태그가 없으면 DB에서 Photo 객체 확인
                    Photo latestPhoto = db.photoDao().getPhotoByFilePath(photoPath);

                    if (latestPhoto != null && latestPhoto.getDetectedObjectPairs() != null) {
                        // DB에서 찾은 예측값 사용
                        timelineItem.setDetectedObjectPairs(latestPhoto.getDetectedObjectPairs()); // 갱신

                        List<Pair<String, Float>> updatedPredictions = timelineItem.getDetectedObjectPairs();
                        Log.d("HASHTAG_CHECK", "✅ DB에서 조회된 예측값: " + updatedPredictions);

                        requireActivity().runOnUiThread(() -> {
                            createHashtags(updatedPredictions);
                        });
                    } else {
                        // DB에서도 예측값을 찾지 못한 경우 이미지 분석 시도
                        try {
                            Bitmap bitmap = null;

                            // 이미지 로드 시도 방법 개선
                            try {
                                // 먼저 URI로 시도
                                bitmap = BitmapFactory.decodeStream(
                                        requireContext().getContentResolver().openInputStream(Uri.parse(photoPath))
                                );
                            } catch (Exception e) {
                                Log.e("HASHTAG", "URI로 이미지 로드 실패, 파일 경로로 시도: " + e.getMessage());

                                // URI 실패 시 파일로 시도
                                try {
                                    bitmap = BitmapFactory.decodeFile(photoPath);
                                } catch (Exception e2) {
                                    Log.e("HASHTAG", "파일 경로로도 이미지 로드 실패: " + e2.getMessage());
                                }
                            }

                            if (bitmap != null) {
                                List<Pair<String, Float>> predictions;

                                if (timelineItem.getDetectedObjectPairs() != null && !timelineItem.getDetectedObjectPairs().isEmpty()) {
                                    Log.d("HASHTAG_CHECK", "🟢 기존 예측 사용: " + timelineItem.getDetectedObjectPairs().toString());
                                    predictions = timelineItem.getDetectedObjectPairs();
                                } else {
                                    Log.d("HASHTAG_CHECK", "🔴 예측 없음 → 모델 재분석 시작");
                                    ImageClassifier classifier = new ImageClassifier(requireContext());
                                    predictions = classifier.classifyImage(bitmap);
                                    classifier.close();

                                    timelineItem.setDetectedObjectPairs(predictions);
                                    executor.execute(() -> {
                                        AppDatabase db2 = AppDatabase.getInstance(requireContext());
                                        db2.photoDao().updateDetectedObjectPairs(timelineItem.getPhotoPath(), predictions);
                                    });
                                }

                                Log.d("HASHTAG_CHECK", "🔖 최종 예측값: " + predictions);
                                List<Pair<String, Float>> finalPredictions = predictions;
                                requireActivity().runOnUiThread(() -> {
                                    createHashtags(finalPredictions);
                                });
                            } else {
                                Log.e("HASHTAG", "이미지를 로드할 수 없습니다.");
                                requireActivity().runOnUiThread(() -> {
                                    // 기본 해시태그라도 표시
                                    List<Pair<String, Float>> defaultTags = new ArrayList<>();
                                    defaultTags.add(new Pair<>("Photo", 1.0f));
                                    createHashtags(defaultTags);
                                });
                            }

                        } catch (Exception e) {
                            Log.e("HASHTAG", "이미지 분석 중 오류: " + e.getMessage(), e);
                            requireActivity().runOnUiThread(() -> {
                                // 오류 발생 시에도 기본 해시태그 표시
                                List<Pair<String, Float>> defaultTags = new ArrayList<>();
                                defaultTags.add(new Pair<>("Photo", 1.0f));
                                createHashtags(defaultTags);
                            });
                        }
                    }
                }
            });
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
            return;
        }

        LinearLayout hashtagContainer = getView().findViewById(R.id.hashtagContainer);
        if (hashtagContainer == null) {
            return;
        }

        hashtagContainer.removeAllViews();

        // 해시태그 문자열 생성
        StringBuilder hashtagBuilder = new StringBuilder();
        int count = 0;
        // 최대 3개의 해시태그만 표시하도록 제한
        int maxHashtags = 3;

        for (Pair<String, Float> pred : predictions) {
            if (count >= maxHashtags) break; // 최대 3개까지만 처리

            // 예측 항목에서 해시태그 추출
            String term = pred.first != null ? pred.first.split(",")[0].trim() : "";
            if (term.isEmpty()) continue;
            String hashtag = "#" + term.replace(" ", "");

            // 해시태그 문자열에 추가
            hashtagBuilder.append(hashtag).append(" ");

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

            // 해시태그 클릭 리스너 추가
            tagView.setOnClickListener(v -> {
                String clickedHashtag = hashtag;
                if (clickedHashtag.startsWith("#")) {
                    clickedHashtag = clickedHashtag.substring(1); // # 제거
                }

                Log.d("HashtagClick", "클릭한 해시태그(전): " + hashtag);
                Log.d("HashtagClick", "클릭한 해시태그(후): " + clickedHashtag);

                Intent intent = new Intent(getActivity(), HashtagPhotosActivity.class);
                intent.putExtra("hashtag", clickedHashtag);
                startActivity(intent);
            });

            hashtagContainer.addView(tagView);
            count++;
        }

        // 예측 결과 없을 경우 기본 태그
        if (count == 0) {
            hashtagBuilder.append("#Photo ");

            TextView tagView = new TextView(requireContext());
            tagView.setText("#Photo");
            tagView.setTextSize(12);
            tagView.setTextColor(Color.BLACK);
            int paddingPixels = (int) (12 * getResources().getDisplayMetrics().density);
            int topBottomPadding = (int) (6 * getResources().getDisplayMetrics().density);
            tagView.setPadding(paddingPixels, topBottomPadding, paddingPixels, topBottomPadding);
            tagView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.hash_tag_background));

            // 기본 태그에도 클릭 리스너 추가
            tagView.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), HashtagPhotosActivity.class);
                intent.putExtra("hashtag", "Photo");
                startActivity(intent);
            });

            hashtagContainer.addView(tagView);
        }

        // DB에 해시태그 저장
        if (timelineItem != null && timelineItem.getPhotoPath() != null) {
            String finalHashtags = hashtagBuilder.toString().trim();
            Log.d("HASHTAG_SAVE", "저장할 해시태그: " + finalHashtags);

            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                db.photoDao().updateHashtags(timelineItem.getPhotoPath(), finalHashtags);

                // 저장 후 확인
                String savedHashtags = db.photoDao().getHashtagsByPath(timelineItem.getPhotoPath());
                Log.d("HASHTAG_SAVE", "저장된 해시태그: " + savedHashtags);
            });
        }
    }
}