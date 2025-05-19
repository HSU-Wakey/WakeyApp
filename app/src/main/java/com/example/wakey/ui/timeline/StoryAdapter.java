package com.example.wakey.ui.timeline;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.wakey.R;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.util.DateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 스토리 뷰를 위한 RecyclerView 어댑터
 * 사진 항목을 스토리 형식으로 표시
 */
public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.StoryViewHolder> {

    private static final String TAG = "StoryAdapter";
    private Context context;
    private List<TimelineItem> timelineItems;
    private OnItemClickListener clickListener;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private StoryGenerator storyGenerator;
    private List<TimelineItem> items;

    // 클릭 리스너 인터페이스
    public interface OnItemClickListener {
        void onItemClick(TimelineItem item, int position);
    }

    public StoryAdapter(List<TimelineItem> timelineItems) {
        this.timelineItems = timelineItems != null ? timelineItems : new ArrayList<>();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_story, parent, false);
        storyGenerator = StoryGenerator.getInstance(context);
        return new StoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        TimelineItem item = timelineItems.get(position);

        // 모든 데이터 로깅
        Log.d(TAG, "📌 onBindViewHolder 호출 - 위치: " + position);
        Log.d(TAG, "📌 사진 경로: " + item.getPhotoPath());
        Log.d(TAG, "📌 스토리 내용: " + item.getStory());
        Log.d(TAG, "📌 캡션 내용: " + item.getCaption());
        Log.d(TAG, "📌 객체 인식: " + item.getDetectedObjects());

        // 1. 사진 로드 - 이미지 크기 조정 및 둥근 모서리 적용
        if (item.getPhotoPath() != null) {
            Log.d(TAG, "📌 사진 로드 시도: " + item.getPhotoPath());
            Glide.with(context)
                    .load(item.getPhotoPath())
                    .centerCrop()
                    .transform(new RoundedCorners(20))
                    .into(holder.imageView);
        } else {
            Log.d(TAG, "📌 사진 경로 없음");
        }

        // 2. 시간 표시
        if (item.getTime() != null && holder.timeTextView != null) {
            String timeText = DateUtil.formatDate(item.getTime(), "HH:mm");
            holder.timeTextView.setText(timeText);
            holder.timeTextView.setVisibility(View.VISIBLE);
            Log.d(TAG, "📌 시간 설정: " + timeText);
        } else {
            if (holder.timeTextView != null) {
                holder.timeTextView.setVisibility(View.GONE);
            }
            Log.d(TAG, "📌 시간 정보 없음");
        }

        // 3. 위치 표시
        if (holder.locationTextView != null) {
            if (item.getLocation() != null && !item.getLocation().isEmpty() &&
                    !item.getLocation().equals("위치 정보 없음")) {
                holder.locationTextView.setText(item.getLocation());
                holder.locationTextView.setVisibility(View.VISIBLE);
                Log.d(TAG, "📌 위치 표시: " + item.getLocation());
            } else {
                holder.locationTextView.setVisibility(View.GONE);
                Log.d(TAG, "📌 위치 정보 숨김");
            }
        }

        // 4. 스토리 표시 (스토리 우선, 없으면 캡션 사용)
        if (holder.captionTextView != null) {
            String storyText = item.getStory();
            boolean hasStory = storyText != null && !storyText.trim().isEmpty();

            Log.d(TAG, "📌 스토리 확인: " + (hasStory ? "있음" : "없음"));

            // 스토리 텍스트뷰 설정
            if (hasStory) {
                Log.d(TAG, "📌 스토리 표시: " + storyText);
                holder.captionTextView.setText(storyText);
                holder.captionTextView.setVisibility(View.VISIBLE);

                if (holder.captionProgressBar != null) {
                    holder.captionProgressBar.setVisibility(View.GONE);
                }
            } else if (item.getCaption() != null && !item.getCaption().isEmpty()) {
                // 캡션만 있는 경우
                holder.captionTextView.setText(item.getCaption());
                holder.captionTextView.setVisibility(View.VISIBLE);

                if (holder.captionProgressBar != null) {
                    holder.captionProgressBar.setVisibility(View.GONE);
                }

                Log.d(TAG, "📌 캡션 표시: " + item.getCaption());
            } else {
                // 스토리와 캡션 모두 없는 경우 "생성 중..." 표시
                holder.captionTextView.setText("스토리 생성 중...");
                holder.captionTextView.setVisibility(View.VISIBLE);

                if (holder.captionProgressBar != null) {
                    holder.captionProgressBar.setVisibility(View.VISIBLE);
                }

                Log.d(TAG, "📌 스토리 생성 중... 표시");

                // 스토리 생성 요청
                generateStory(item, holder);
            }
        }

        // 클릭 리스너 설정
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(item, position);
            }
        });
    }

    /**
     * 스토리 생성 메서드
     * StoryGenerator를 사용해 스토리 생성
     */
    private void generateStory(TimelineItem item, StoryViewHolder holder) {
        if (item.getPhotoPath() == null) return;

        executor.execute(() -> {
            try {
                // StoryGenerator로 스토리 생성 요청
                List<TimelineItem> singleItemList = new ArrayList<>();
                singleItemList.add(item);

                storyGenerator.generateStories(singleItemList, new StoryGenerator.OnStoryGeneratedListener() {
                    @Override
                    public void onStoryGenerated(List<TimelineItem> itemsWithStories) {
                        if (itemsWithStories != null && !itemsWithStories.isEmpty()) {
                            TimelineItem updatedItem = itemsWithStories.get(0);
                            if (updatedItem.getStory() != null && !updatedItem.getStory().isEmpty()) {
                                // UI 업데이트
                                if (context instanceof androidx.fragment.app.FragmentActivity) {
                                    ((androidx.fragment.app.FragmentActivity) context).runOnUiThread(() -> {
                                        holder.captionTextView.setText(updatedItem.getStory());
                                        holder.captionProgressBar.setVisibility(View.GONE);

                                        // 텍스트뷰 높이 자동 조정
                                        holder.captionTextView.post(() -> {
                                            int textHeight = holder.captionTextView.getLineCount() * holder.captionTextView.getLineHeight();
                                            Log.d(TAG, "📏 생성된 스토리 높이: " + textHeight + "px, 라인 수: " + holder.captionTextView.getLineCount());

                                            // 스크롤뷰가 있는지 확인하고 높이를 적절히 설정
                                            if (holder.scrollView != null) {
                                                ViewGroup.LayoutParams params = holder.scrollView.getLayoutParams();
                                                // 최대 높이를 설정 (예: 5줄 이상은 스크롤)
                                                int maxHeight = holder.captionTextView.getLineHeight() * 5;
                                                if (textHeight > maxHeight) {
                                                    params.height = maxHeight;
                                                    holder.scrollView.setLayoutParams(params);
                                                    Log.d(TAG, "📏 스크롤뷰 높이 설정: " + maxHeight + "px (스크롤 가능)");
                                                } else {
                                                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                                                    holder.scrollView.setLayoutParams(params);
                                                    Log.d(TAG, "📏 스크롤뷰 높이 설정: WRAP_CONTENT (스크롤 불필요)");
                                                }
                                            }
                                        });

                                        // 원래 아이템에 스토리 설정
                                        item.setStory(updatedItem.getStory());
                                    });
                                }
                            }
                        }
                    }

                    @Override
                    public void onStoryGenerationFailed(Exception e) {
                        // 오류 발생 시 UI 업데이트
                        if (context instanceof androidx.fragment.app.FragmentActivity) {
                            ((androidx.fragment.app.FragmentActivity) context).runOnUiThread(() -> {
                                holder.captionTextView.setText("스토리를 만들 수 없습니다");
                                holder.captionProgressBar.setVisibility(View.GONE);
                            });
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();

                // 오류 발생 시 UI 업데이트
                if (context instanceof androidx.fragment.app.FragmentActivity) {
                    ((androidx.fragment.app.FragmentActivity) context).runOnUiThread(() -> {
                        holder.captionTextView.setText("스토리를 만들 수 없습니다");
                        holder.captionProgressBar.setVisibility(View.GONE);
                    });
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return timelineItems.size();
    }

    /**
     * 데이터 업데이트
     */
    public void updateItems(List<TimelineItem> newItems) {
        Log.d(TAG, "🔄 updateItems 호출됨, 항목 수: " +
                (newItems != null ? newItems.size() : 0));

        if (newItems == null || newItems.isEmpty()) {
            Log.d(TAG, "새 항목이 없음");
            return;
        }

        try {
            // 각 항목의 스토리 상태 명시적 확인
            for (TimelineItem item : newItems) {
                Log.d(TAG, "🔄 항목: " + item.getPhotoPath() +
                        ", 스토리: " + (item.getStory() != null ? "\"" + item.getStory() + "\"" : "null"));
            }

            // 기존 리스트를 완전히 교체 (참조 문제 방지)
            timelineItems = new ArrayList<>(newItems);

            // UI 갱신
            notifyDataSetChanged();
            Log.d(TAG, "🔄 notifyDataSetChanged 호출됨 - 항목 수: " + timelineItems.size());
        } catch (Exception e) {
            Log.e(TAG, "항목 업데이트 중 오류: " + e.getMessage(), e);
        }
    }

    public void updateItem(TimelineItem updatedItem) {
        Log.d(TAG, "🔄 updateItem 호출됨: " + updatedItem.getPhotoPath());
        Log.d(TAG, "🔄 스토리 내용: " + updatedItem.getStory());

        boolean updated = false;

        // 아이템 찾아서 업데이트
        for (int i = 0; i < timelineItems.size(); i++) {
            TimelineItem item = timelineItems.get(i);
            if (item.getPhotoPath() != null &&
                    item.getPhotoPath().equals(updatedItem.getPhotoPath())) {

                Log.d(TAG, "🔄 아이템 찾음 (위치: " + i + ")");
                Log.d(TAG, "🔄 기존 스토리: " + item.getStory());
                Log.d(TAG, "🔄 새 스토리: " + updatedItem.getStory());

                // 아이템 교체
                timelineItems.set(i, updatedItem);
                updated = true;

                // 해당 위치만 업데이트
                notifyItemChanged(i);
                break;
            }
        }

        if (!updated) {
            Log.e(TAG, "❌ 업데이트할 아이템을 찾을 수 없음: " + updatedItem.getPhotoPath());
        }
    }

    public void setItems(List<TimelineItem> items) {
        Log.d(TAG, "🔄 setItems 호출됨, 항목 수: " +
                (items != null ? items.size() : 0));

        if (items == null) {
            this.timelineItems = new ArrayList<>();
        } else {
            this.timelineItems = new ArrayList<>(items);  // 항상 새 리스트로 복사

            // 각 항목의 스토리 상태 확인 로깅
            for (TimelineItem item : this.timelineItems) {
                Log.d(TAG, "🔄 항목: " + item.getPhotoPath() +
                        ", 스토리: " + (item.getStory() != null ? item.getStory() : "null"));
            }
        }

        notifyDataSetChanged();
    }

    // TimelineItem 복사 헬퍼 메서드
    private TimelineItem copyTimelineItem(TimelineItem original) {
        TimelineItem.Builder builder = new TimelineItem.Builder()
                .setPhotoPath(original.getPhotoPath())
                .setTime(original.getTime())
                .setLocation(original.getLocation())
                .setPlaceName(original.getPlaceName())
                .setLatLng(original.getLatLng())
                .setDescription(original.getDescription())
                .setCaption(original.getCaption());

        // 중요: 스토리 복사
        if (original.getStory() != null) {
            builder.setStory(original.getStory());
        }

        if (original.getDetectedObjects() != null) {
            builder.setDetectedObjects(original.getDetectedObjects());
        }

        if (original.getDetectedObjectPairs() != null) {
            builder.setDetectedObjectPairs(original.getDetectedObjectPairs());
        }

        if (original.getActivityType() != null) {
            builder.setActivityType(original.getActivityType());
        }

        builder.setPlaceProbability(original.getPlaceProbability());

        if (original.getNearbyPOIs() != null) {
            builder.setNearbyPOIs(original.getNearbyPOIs());
        }

        return builder.build();
    }

    /**
     * ViewHolder 클래스 - 수정됨 (ScrollView 추가)
     */
    static class StoryViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView timeTextView;
        TextView locationTextView;
        TextView captionTextView;
        ProgressBar captionProgressBar;
        ScrollView scrollView;

        StoryViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.storyImageView);
            timeTextView = itemView.findViewById(R.id.storyTimeTextView);
            locationTextView = itemView.findViewById(R.id.storyLocationTextView);
            captionTextView = itemView.findViewById(R.id.storyCaptionTextView);
            captionProgressBar = itemView.findViewById(R.id.captionProgressBar);
            scrollView = itemView.findViewById(R.id.storyScrollView);
        }
    }

    public void release() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}