// ui/timeline/TimelineAdapter.java
package com.example.wakey.ui.timeline;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.model.TimelineItem;
import com.example.wakey.data.util.DateUtil;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {

    private List<TimelineItem> items;
    private OnTimelineItemClickListener listener;
    private Context context;

    public interface OnTimelineItemClickListener {
        void onTimelineItemClick(TimelineItem item, int position);
    }

    public TimelineAdapter(List<TimelineItem> items) {
        this.items = items;
    }

    public void setOnTimelineItemClickListener(OnTimelineItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_timeline, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimelineItem item = items.get(position);

        // Format time
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String formattedTime = timeFormat.format(item.getTime());
        holder.timeTextView.setText(formattedTime);

        // 장소 이름
        holder.locationTextView.setText(item.getLocation());

        // 캡션/설명
        holder.captionTextView.setText(item.getDescription());

        // 활동 유형 태그 (있는 경우)
        if (item.getActivityType() != null && !item.getActivityType().isEmpty()) {
            holder.activityTagContainer.setVisibility(View.VISIBLE);
            holder.activityText.setText(item.getActivityType());
        } else {
            holder.activityTagContainer.setVisibility(View.GONE);
        }

        // 사진 로드 (있는 경우)
        if (item.getPhotoPath() != null) {
            holder.photoImageView.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(item.getPhotoPath())
                    .centerCrop()
                    .into(holder.photoImageView);
        } else {
            holder.photoImageView.setVisibility(View.GONE);
        }

        // 타임라인 라인 표시 (마지막 항목 제외)
        if (position == getItemCount() - 1) {
            holder.timelineView.setVisibility(View.INVISIBLE);
        } else {
            holder.timelineView.setVisibility(View.VISIBLE);
        }

        // 클릭 리스너 설정
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTimelineItemClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void updateItems(List<TimelineItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView timeTextView;
        TextView locationTextView;
        TextView captionTextView;
        ImageView photoImageView;
        View timelineView;
        View timelineDot;
        LinearLayout activityTagContainer;
        TextView activityText;
        CardView cardView;

        ViewHolder(View itemView) {
            super(itemView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            locationTextView = itemView.findViewById(R.id.locationTextView);
            captionTextView = itemView.findViewById(R.id.captionTextView);
            photoImageView = itemView.findViewById(R.id.photoImageView);
            timelineView = itemView.findViewById(R.id.timelineView);
            timelineDot = itemView.findViewById(R.id.timelineDot);
            activityTagContainer = itemView.findViewById(R.id.activityTagContainer);
            activityText = itemView.findViewById(R.id.activityText);
            cardView = itemView.findViewById(R.id.cardView);
        }
    }
}