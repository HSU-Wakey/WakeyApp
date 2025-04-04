package com.example.wakey.ui.timeline;

import android.content.Context;
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

        // 1. 사진 로드
        if (item.getPhotoPath() != null) {
            Glide.with(context)
                    .load(item.getPhotoPath())
                    .into(holder.imageView);
        }

        // 2. 시간 표시
        if (item.getTime() != null) {
            String timeText = DateUtil.formatDate(item.getTime(), "HH:mm");
            holder.timeTextView.setText(timeText);
        }

        // 3. 위치 표시
        if (item.getLocation() != null && !item.getLocation().isEmpty() &&
                !item.getLocation().equals("위치 정보 없음")) {
            holder.locationTextView.setText(item.getLocation());
            holder.locationTextView.setVisibility(View.VISIBLE);
        } else {
            holder.locationTextView.setVisibility(View.GONE);
        }

        // 4. 스토리 표시 (스토리 우선, 없으면 캡션 사용)
        if (item.getStory() != null && !item.getStory().isEmpty()) {
            // 스토리가 있는 경우
            holder.captionTextView.setText(item.getStory());
            holder.captionTextView.setVisibility(View.VISIBLE);
            holder.captionProgressBar.setVisibility(View.GONE);
        } else if (item.getCaption() != null && !item.getCaption().isEmpty()) {
            // 캡션만 있는 경우
            holder.captionTextView.setText(item.getCaption());
            holder.captionTextView.setVisibility(View.VISIBLE);
            holder.captionProgressBar.setVisibility(View.GONE);
        } else {
            // 스토리와 캡션 모두 없는 경우 "생성 중..." 표시
            holder.captionTextView.setText("스토리 생성 중...");
            holder.captionTextView.setVisibility(View.VISIBLE);
            holder.captionProgressBar.setVisibility(View.VISIBLE);

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
        } else {
            holder.tagsTextView.setVisibility(View.GONE);
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
    public void updateItems(List<TimelineItem> newItems) {
        this.timelineItems = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
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