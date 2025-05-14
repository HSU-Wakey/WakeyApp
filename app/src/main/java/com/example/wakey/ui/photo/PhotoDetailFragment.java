package com.example.wakey.ui.photo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.local.AppDatabase;
import com.example.wakey.data.local.Photo;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.ui.search.HashtagPhotosActivity;
import com.example.wakey.ui.timeline.TimelineManager;
import com.example.wakey.data.util.DateUtil;
import com.example.wakey.tflite.ImageClassifier;
import com.example.wakey.tflite.ESRGANUpscaler;

import java.util.ArrayList;
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

    // ViewPager and adapter for swiping
    private ViewPager2 photoViewPager;
    private PhotoPagerAdapter pagerAdapter;

    // 업스케일 관련 변수
    private boolean isUpscaled = false;
    private Bitmap originalBitmap;
    private Bitmap upscaledBitmap;

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

        // Load timeline items in background
        loadTimelineItems();

        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogStyle);
    }

    private void loadTimelineItems() {
        executor.execute(() -> {
            if (currentDate == null && timelineItem != null && timelineItem.getTime() != null) {
                currentDate = DateUtil.getFormattedDateString(timelineItem.getTime());
            }

            if (currentDate != null) {
                timelineItems = TimelineManager.getInstance(requireContext())
                        .loadTimelineForDate(currentDate);

                // Find current position if not provided
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

                // Update UI on the main thread
                requireActivity().runOnUiThread(() -> {
                    if (pagerAdapter != null && photoViewPager != null) {
                        pagerAdapter.setItems(timelineItems);
                        photoViewPager.setCurrentItem(currentPosition, false);
                        updateNavigationButtons();
                    }
                });
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_detail, container, false);

        // 닫기 버튼
        ImageButton closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dismiss());

        // 내비게이션 버튼
        ImageButton btnPrevious = view.findViewById(R.id.btnPrevious);
        ImageButton btnNext = view.findViewById(R.id.btnNext);
        btnPrevious.setOnClickListener(v -> navigateToPrevious());
        btnNext.setOnClickListener(v -> navigateToNext());

        // ViewPager2 설정
        photoViewPager = view.findViewById(R.id.photoViewPager);
        setupViewPager();

        // 내비게이션 버튼 상태 업데이트
        updateNavigationButtons();

        return view;
    }

    private void setupViewPager() {
        // Initialize adapter
        pagerAdapter = new PhotoPagerAdapter();
        photoViewPager.setAdapter(pagerAdapter);

        // Set initial data if available
        if (timelineItems != null && !timelineItems.isEmpty()) {
            pagerAdapter.setItems(timelineItems);
            photoViewPager.setCurrentItem(currentPosition, false);
        }

        // Register page change callback
        photoViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                timelineItem = timelineItems.get(position);

                // Reset upscale state
                isUpscaled = false;
                originalBitmap = null;
                upscaledBitmap = null;

                // Update UI for the selected item
                updateNavigationButtons();

                // Update info section
                View currentView = getView();
                if (currentView != null) {
                    updateInfoSection(currentView);
                }
            }
        });

        // Enable hardware acceleration for smoother scrolling
        photoViewPager.setOffscreenPageLimit(1);

        // Reduce sensitivity to prevent accidental swipes
        try {
            RecyclerView recyclerView = (RecyclerView) photoViewPager.getChildAt(0);
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        } catch (Exception e) {
            Log.e("PhotoDetailFragment", "Error setting overscroll mode", e);
        }
    }

    /**
     * Updates the information section below the photo
     */
    private void updateInfoSection(View view) {
        if (timelineItem == null) return;

        TextView locationTextView = view.findViewById(R.id.photoDetailLocationTextView);
        TextView timeTextView = view.findViewById(R.id.photoDetailTimeTextView);
        TextView addressTextView = view.findViewById(R.id.photoDetailAddressTextView);
        TextView activityChip = view.findViewById(R.id.activityChip);

        // Time
        String dateTimeStr = DateUtil.formatDate(timelineItem.getTime(), "yyyy.MM.dd(E) HH:mm");
        timeTextView.setText(dateTimeStr);

        // Activity type
        if (timelineItem.getActivityType() != null && !timelineItem.getActivityType().isEmpty()) {
            activityChip.setText(timelineItem.getActivityType());
            activityChip.setVisibility(View.VISIBLE);
        } else {
            activityChip.setVisibility(View.GONE);
        }

        // Address and location
        if (timelineItem.getLatLng() != null) {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            executor.execute(() -> {
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
                        String finalLocationStr = locationStr;
                        executor.execute(() -> {
                            AppDatabase db = AppDatabase.getInstance(requireContext());
                            db.photoDao().updateFullAddress(timelineItem.getPhotoPath(), finalLocationStr);
                        });

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
            });
        } else {
            addressTextView.setText("위치 정보 없음");

            String fallbackLoc = timelineItem.getLocation();
            if (fallbackLoc == null || fallbackLoc.trim().isEmpty()) {
                fallbackLoc = "위치 정보 없음";
            }

            locationTextView.setText(fallbackLoc);
            locationTextView.setVisibility(View.VISIBLE);
        }

        // Load hashtags
        loadHashtags();
    }

    private void updateNavigationButtons() {
        View view = getView();
        if (view == null || timelineItems == null || timelineItems.isEmpty()) return;

        ImageButton btnPrevious = view.findViewById(R.id.btnPrevious);
        ImageButton btnNext = view.findViewById(R.id.btnNext);

        // Previous button visibility
        btnPrevious.setVisibility(currentPosition > 0 ? View.VISIBLE : View.INVISIBLE);

        // Next button visibility
        btnNext.setVisibility(currentPosition < timelineItems.size() - 1 ? View.VISIBLE : View.INVISIBLE);
    }

    private void navigateToPrevious() {
        if (photoViewPager != null && currentPosition > 0) {
            photoViewPager.setCurrentItem(currentPosition - 1, true);
        }
    }

    private void navigateToNext() {
        if (photoViewPager != null && timelineItems != null && currentPosition < timelineItems.size() - 1) {
            photoViewPager.setCurrentItem(currentPosition + 1, true);
        }
    }

    /**
     * Loads hashtags for the current photo
     */
    private void loadHashtags() {
        if (timelineItem == null || timelineItem.getPhotoPath() == null) return;

        String photoPath = timelineItem.getPhotoPath();

        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            String savedHashtags = db.photoDao().getHashtagsByPath(photoPath);

            if (savedHashtags != null && !savedHashtags.isEmpty()) {
                // Use saved hashtags
                List<Pair<String, Float>> pairs = new ArrayList<>();
                String[] tags = savedHashtags.split("#");
                for (String tag : tags) {
                    if (!tag.trim().isEmpty()) {
                        pairs.add(new Pair<>(tag.trim(), 1.0f));
                    }
                }

                requireActivity().runOnUiThread(() -> {
                    createHashtags(pairs);
                });
            } else {
                // Check for detected objects in DB
                Photo latestPhoto = db.photoDao().getPhotoByFilePath(photoPath);

                if (latestPhoto != null && latestPhoto.getDetectedObjectPairs() != null) {
                    // Use detected objects from DB
                    timelineItem.setDetectedObjectPairs(latestPhoto.getDetectedObjectPairs());
                    List<Pair<String, Float>> predictions = timelineItem.getDetectedObjectPairs();
                    Log.d("HASHTAG_CHECK", "✅ DB에서 조회된 예측값: " + predictions);

                    requireActivity().runOnUiThread(() -> {
                        createHashtags(predictions);
                    });
                } else {
                    // Try to analyze the image
                    try {
                        Bitmap bitmap = BitmapFactory.decodeStream(
                                requireContext().getContentResolver().openInputStream(android.net.Uri.parse(photoPath))
                        );

                        if (bitmap != null) {
                            List<Pair<String, Float>> predictions;

                            if (timelineItem.getDetectedObjectPairs() != null && !timelineItem.getDetectedObjectPairs().isEmpty()) {
                                Log.d("HASHTAG_CHECK", "🟢 기존 예측 사용: " + timelineItem.getDetectedObjectPairs().toString());
                                predictions = timelineItem.getDetectedObjectPairs();
                            } else {
                                Log.d("HASHTAG_CHECK", "🔴 예측 없음 → 모델 재분석 시작");
                                // Run image classification
                                ImageClassifier classifier = new ImageClassifier(requireContext());
                                predictions = classifier.classifyImage(bitmap);
                                classifier.close();

                                timelineItem.setDetectedObjectPairs(predictions);
                                executor.execute(() -> {
                                    AppDatabase db2 = AppDatabase.getInstance(requireContext());
                                    db2.photoDao().updateDetectedObjectPairs(photoPath, predictions);
                                });
                            }

                            Log.d("HASHTAG_CHECK", "🔖 최종 예측값: " + predictions);
                            List<Pair<String, Float>> finalPredictions = predictions;
                            requireActivity().runOnUiThread(() -> {
                                createHashtags(finalPredictions);
                            });
                        }
                    } catch (Exception e) {
                        Log.e("HASHTAG", "이미지 분석 중 오류: " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    /**
     * Creates hashtag views from predictions
     */
    private void createHashtags(List<Pair<String, Float>> predictions) {
        Log.d("HASHTAG_CHECK", "🏷️ 해시태그 생성 진입, 예측 개수: " + (predictions != null ? predictions.size() : 0));
        View view = getView();
        if (view == null) return;

        LinearLayout hashtagContainer = view.findViewById(R.id.hashtagContainer);
        if (hashtagContainer == null) return;

        hashtagContainer.removeAllViews();

        StringBuilder hashtagBuilder = new StringBuilder();
        int count = 0;
        int maxHashtags = 3;

        if (predictions != null && !predictions.isEmpty()) {
            for (Pair<String, Float> pred : predictions) {
                if (count >= maxHashtags) break;

                String term = pred.first != null ? pred.first.split(",")[0].trim() : "";
                if (term.isEmpty()) continue;

                String hashtag = "#" + term.replace(" ", "");
                hashtagBuilder.append(hashtag).append(" ");

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

                tagView.setOnClickListener(v -> {
                    String clickedHashtag = hashtag;
                    if (clickedHashtag.startsWith("#")) {
                        clickedHashtag = clickedHashtag.substring(1);
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
        }

        // Default tag if no predictions
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

            tagView.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), HashtagPhotosActivity.class);
                intent.putExtra("hashtag", "Photo");
                startActivity(intent);
            });

            hashtagContainer.addView(tagView);
        }

        // Save hashtags to DB
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

    // 기존의 updateUI 메서드는 ViewPager 구현으로 대체되었으므로 제거

    /**
     * ViewPager adapter for photos
     */
    private class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoViewHolder> {
        private List<TimelineItem> items = new ArrayList<>();

        public void setItems(List<TimelineItem> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo_detail, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            TimelineItem item = items.get(position);
            holder.bind(item);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class PhotoViewHolder extends RecyclerView.ViewHolder {
            private final ImageView photoImageView;
            private final ProgressBar progressBar;
            private final ImageButton upscaleButton;

            private boolean isItemUpscaled = false;
            private Bitmap itemOriginalBitmap;
            private Bitmap itemUpscaledBitmap;

            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                photoImageView = itemView.findViewById(R.id.photoItemImageView);
                progressBar = itemView.findViewById(R.id.progressBarItemUpscale);
                upscaleButton = itemView.findViewById(R.id.upscaleItemButton);

                upscaleButton.setOnClickListener(v -> handleUpscale());
            }

            void bind(TimelineItem item) {
                // Reset state
                isItemUpscaled = false;
                itemOriginalBitmap = null;
                itemUpscaledBitmap = null;

                // Load image
                if (item.getPhotoPath() != null) {
                    Glide.with(requireContext())
                            .load(item.getPhotoPath())
                            .into(photoImageView);
                }
            }

            void handleUpscale() {
                if (itemOriginalBitmap == null) {
                    Drawable drawable = photoImageView.getDrawable();
                    if (drawable instanceof BitmapDrawable) {
                        itemOriginalBitmap = ((BitmapDrawable) drawable).getBitmap();
                    } else {
                        Toast.makeText(getContext(), "이미지를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                if (isItemUpscaled) {
                    // Switch back to original
                    photoImageView.setImageBitmap(itemOriginalBitmap);
                    isItemUpscaled = false;
                    Toast.makeText(getContext(), "원본 이미지 보기", Toast.LENGTH_SHORT).show();
                } else if (itemUpscaledBitmap != null) {
                    // Use cached upscaled image
                    photoImageView.setImageBitmap(itemUpscaledBitmap);
                    isItemUpscaled = true;
                    Toast.makeText(getContext(), "업스케일된 이미지 보기", Toast.LENGTH_SHORT).show();
                } else {
                    // Perform upscaling
                    progressBar.setVisibility(View.VISIBLE);

                    new Thread(() -> {
                        try {
                            ESRGANUpscaler upscaler = new ESRGANUpscaler(requireContext());
                            itemUpscaledBitmap = upscaler.upscale(itemOriginalBitmap);

                            requireActivity().runOnUiThread(() -> {
                                photoImageView.setImageBitmap(itemUpscaledBitmap);
                                isItemUpscaled = true;
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
            }
        }
    }
}