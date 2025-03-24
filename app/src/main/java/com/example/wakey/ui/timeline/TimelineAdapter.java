package com.example.wakey.ui.timeline;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.model.TimelineItem;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {

    private List<TimelineItem> items;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnTimelineItemClickListener listener;

    public interface OnTimelineItemClickListener {
        void onTimelineItemClick(TimelineItem item, int position);
    }

    public TimelineAdapter(List<TimelineItem> items) {
        this.items = items;
    }

    public TimelineAdapter(List<TimelineItem> items, OnTimelineItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timeline, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimelineItem item = items.get(position);

        // Set time
        holder.timeTextView.setText(timeFormat.format(item.getTime()));

        // Set location
        holder.locationTextView.setText(item.getLocation());

        // Set description
        holder.descriptionTextView.setText(item.getDescription());

        // Load image if available
        if (item.getPhotoPath() != null && !item.getPhotoPath().isEmpty()) {
            holder.photoImageView.setVisibility(View.VISIBLE);
            Glide.with(holder.photoImageView.getContext())
                    .load(item.getPhotoPath())
                    .centerCrop()
                    .into(holder.photoImageView);
        } else {
            // No photo available (might be a POI without photo)
            holder.photoImageView.setVisibility(View.GONE);
        }

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTimelineItemClick(item, holder.getAdapterPosition());
            }
        });

        // Show timeline line for all but the last item
        if (position == getItemCount() - 1) {
            holder.timelineView.setVisibility(View.INVISIBLE);
        } else {
            holder.timelineView.setVisibility(View.VISIBLE);
        }

        // Special styling for first and last items
        if (position == 0) {
            // First item - start of journey
            holder.timelineDot.setBackgroundResource(R.drawable.timeline_dot_start);
        } else if (position == getItemCount() - 1) {
            // Last item - end of journey
            holder.timelineDot.setBackgroundResource(R.drawable.timeline_dot_end);
        } else {
            // Middle items
            holder.timelineDot.setBackgroundResource(R.drawable.timeline_dot);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItems(List<TimelineItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    public void setOnTimelineItemClickListener(OnTimelineItemClickListener listener) {
        this.listener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView timeTextView;
        TextView locationTextView;
        TextView descriptionTextView;
        ImageView photoImageView;
        View timelineView;
        View timelineDot;

        ViewHolder(View itemView) {
            super(itemView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            locationTextView = itemView.findViewById(R.id.locationTextView);
            descriptionTextView = itemView.findViewById(R.id.descriptionTextView);
            photoImageView = itemView.findViewById(R.id.photoImageView);
            timelineView = itemView.findViewById(R.id.timelineView);
            timelineDot = itemView.findViewById(R.id.timelineDot);
        }
    }
}