package com.example.wakey.ui.timeline;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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

    // StoryAdapter의 onBindViewHolder 메서드 수정:

    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        TimelineItem item = timelineItems.get(position);

        // 모든 데이터 로깅
        Log.d("StoryAdapter", "📌 onBindViewHolder 호출 - 위치: " + position);
        Log.d("StoryAdapter", "📌 사진 경로: " + item.getPhotoPath());
        Log.d("StoryAdapter", "📌 스토리 내용: " + item.getStory());
        Log.d("StoryAdapter", "📌 캡션 내용: " + item.getCaption());
        Log.d("StoryAdapter", "📌 객체 인식: " + item.getDetectedObjects());

        // 1. 사진 로드
        if (item.getPhotoPath() != null) {
            Log.d("StoryAdapter", "📌 사진 로드 시도: " + item.getPhotoPath());
            Glide.with(context)
                    .load(item.getPhotoPath())
                    .into(holder.imageView);
        } else {
            Log.d("StoryAdapter", "📌 사진 경로 없음");
        }

        // 2. 시간 표시
        if (item.getTime() != null) {
            String timeText = DateUtil.formatDate(item.getTime(), "HH:mm");
            holder.timeTextView.setText(timeText);
            Log.d("StoryAdapter", "📌 시간 설정: " + timeText);
        } else {
            Log.d("StoryAdapter", "📌 시간 정보 없음");
        }

        // 3. 위치 표시
        if (item.getLocation() != null && !item.getLocation().isEmpty() &&
                !item.getLocation().equals("위치 정보 없음")) {
            holder.locationTextView.setText(item.getLocation());
            holder.locationTextView.setVisibility(View.VISIBLE);
            Log.d("StoryAdapter", "📌 위치 표시: " + item.getLocation());
        } else {
            holder.locationTextView.setVisibility(View.GONE);
            Log.d("StoryAdapter", "📌 위치 정보 숨김");
        }

        // 4. 스토리 표시 (스토리 우선, 없으면 캡션 사용)
        String storyText = item.getStory();
        boolean hasStory = storyText != null && !storyText.trim().isEmpty();

        Log.d("StoryAdapter", "📌 스토리 확인: " + (hasStory ? "있음" : "없음"));

        if (hasStory) {
            Log.d("StoryAdapter", "📌 스토리 표시: " + storyText);
            holder.captionTextView.setText(storyText);
            holder.captionTextView.setVisibility(View.VISIBLE);
            holder.captionProgressBar.setVisibility(View.GONE);
        } else if (item.getCaption() != null && !item.getCaption().isEmpty()) {
            // 캡션만 있는 경우
            holder.captionTextView.setText(item.getCaption());
            holder.captionTextView.setVisibility(View.VISIBLE);
            holder.captionProgressBar.setVisibility(View.GONE);
            Log.d("StoryAdapter", "📌 캡션 표시: " + item.getCaption());
        } else {
            // 스토리와 캡션 모두 없는 경우 "생성 중..." 표시
            holder.captionTextView.setText("스토리 생성 중...");
            holder.captionTextView.setVisibility(View.VISIBLE);
            holder.captionProgressBar.setVisibility(View.VISIBLE);
            Log.d("StoryAdapter", "📌 스토리 생성 중... 표시");

            // 스토리 생성 요청
            generateStory(item, holder);
        }

        // 5. 객체 인식 태그 표시
        if (item.getDetectedObjects() != null && !item.getDetectedObjects().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            // 쉼표로 구분된 객체 목록을 '#객체' 형식으로 변환
            String[] objects = item.getDetectedObjects().split(",");
            for (String object : objects) {
                sb.append("#").append(object.trim().replace(" ", "")).append(" ");
            }
            holder.tagsTextView.setText(sb.toString());
            holder.tagsTextView.setVisibility(View.VISIBLE);
            Log.d("StoryAdapter", "📌 태그 표시: " + sb.toString());
        } else {
            holder.tagsTextView.setVisibility(View.GONE);
            Log.d("StoryAdapter", "📌 태그 정보 숨김");
        }

        // 로그를 추가하여 텍스트뷰 상태 확인
        Log.d("StoryAdapter", "📌 텍스트뷰 확인 - captionTextView: " +
                (holder.captionTextView.getVisibility() == View.VISIBLE ? "보임" : "숨김") +
                ", 텍스트: \"" + holder.captionTextView.getText() + "\"");

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

                                        // 원래 아이템에 스토리 설정
                                        item.setStory(updatedItem.getStory());

                                        // (PhotoDao에 updateStory 메서드 추가 필요)
                                        // DB에 스토리 저장 코드는 향후 구현
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
    // StoryAdapter.java의 updateItems 메서드 수정

    // StoryAdapter.java의 updateItems 메서드 개선
    public void updateItems(List<TimelineItem> newItems) {
        Log.d("StoryAdapter", "🔄 updateItems 호출됨, 항목 수: " +
                (newItems != null ? newItems.size() : 0));

        if (newItems == null || newItems.isEmpty()) {
            Log.d("StoryAdapter", "새 항목이 없음");
            return;
        }

        try {
            // 각 항목의 스토리 상태 명시적 확인
            for (TimelineItem item : newItems) {
                Log.d("StoryAdapter", "🔄 항목: " + item.getPhotoPath() +
                        ", 스토리: " + (item.getStory() != null ? "\"" + item.getStory() + "\"" : "null"));
            }

            // 기존 리스트를 완전히 교체 (참조 문제 방지)
            timelineItems = new ArrayList<>(newItems);

            // UI 갱신
            notifyDataSetChanged();
            Log.d("StoryAdapter", "🔄 notifyDataSetChanged 호출됨 - 항목 수: " + timelineItems.size());
        } catch (Exception e) {
            Log.e("StoryAdapter", "항목 업데이트 중 오류: " + e.getMessage(), e);
        }
    }
// StoryAdapter.java의 updateItem 메서드 수정

    public void updateItem(TimelineItem updatedItem) {
        Log.d("StoryAdapter", "🔄 updateItem 호출됨: " + updatedItem.getPhotoPath());
        Log.d("StoryAdapter", "🔄 스토리 내용: " + updatedItem.getStory());

        boolean updated = false;

        // 아이템 찾아서 업데이트
        for (int i = 0; i < timelineItems.size(); i++) {
            TimelineItem item = timelineItems.get(i);
            if (item.getPhotoPath() != null &&
                    item.getPhotoPath().equals(updatedItem.getPhotoPath())) {

                Log.d("StoryAdapter", "🔄 아이템 찾음 (위치: " + i + ")");
                Log.d("StoryAdapter", "🔄 기존 스토리: " + item.getStory());
                Log.d("StoryAdapter", "🔄 새 스토리: " + updatedItem.getStory());

                // 아이템 교체
                timelineItems.set(i, updatedItem);
                updated = true;

                // 해당 위치만 업데이트
                notifyItemChanged(i);
                break;
            }
        }

        if (!updated) {
            Log.e("StoryAdapter", "❌ 업데이트할 아이템을 찾을 수 없음: " + updatedItem.getPhotoPath());
        }
    }
    public void setItems(List<TimelineItem> items) {
        Log.d("StoryAdapter", "🔄 setItems 호출됨, 항목 수: " +
                (items != null ? items.size() : 0));

        if (items == null) {
            this.timelineItems = new ArrayList<>();
        } else {
            this.timelineItems = new ArrayList<>(items);  // 항상 새 리스트로 복사

            // 각 항목의 스토리 상태 확인 로깅
            for (TimelineItem item : this.timelineItems) {
                Log.d("StoryAdapter", "🔄 항목: " + item.getPhotoPath() +
                        ", 스토리: " + (item.getStory() != null ? item.getStory() : "null"));
            }
        }

        notifyDataSetChanged();
    }


    // TimelineItem 복사 헬퍼 메서드 추가
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
     * ViewHolder 클래스
     */
    static class StoryViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView timeTextView;
        TextView locationTextView;
        TextView captionTextView;
        TextView tagsTextView;
        ProgressBar captionProgressBar;

        StoryViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.storyImageView);
            timeTextView = itemView.findViewById(R.id.storyTimeTextView);
            locationTextView = itemView.findViewById(R.id.storyLocationTextView);
            captionTextView = itemView.findViewById(R.id.storyCaptionTextView);
            tagsTextView = itemView.findViewById(R.id.storyTagsTextView);
            captionProgressBar = itemView.findViewById(R.id.captionProgressBar);
        }
    }

    public void release() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

}