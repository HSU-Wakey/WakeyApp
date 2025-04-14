package com.example.wakey.ui.album.diary;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.ui.album.SmartAlbumActivity;

import java.util.List;

public class DiaryAdapter extends RecyclerView.Adapter<DiaryAdapter.DiaryViewHolder> {

    private List<SmartAlbumActivity.DiaryItem> diaryItems;
    private OnDiaryItemClickListener listener;

    public interface OnDiaryItemClickListener {
        void onDiaryItemClick(SmartAlbumActivity.DiaryItem item);
    }

    public DiaryAdapter(List<SmartAlbumActivity.DiaryItem> diaryItems, OnDiaryItemClickListener listener) {
        this.diaryItems = diaryItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DiaryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_diary, parent, false);
        return new DiaryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DiaryViewHolder holder, int position) {
        SmartAlbumActivity.DiaryItem item = diaryItems.get(position);

        holder.diaryTitle.setText(item.getTitle());
        holder.diaryDateRange.setText(item.getDateRange());

        // Load thumbnail if available
        if (item.getThumbnailPath() != null && !item.getThumbnailPath().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(Uri.parse(item.getThumbnailPath()))
                    .centerCrop()
                    .into(holder.diaryThumbnail);
        } else {
            // Set a placeholder
            holder.diaryThumbnail.setImageResource(R.drawable.placeholder_image);
        }

        // Set heart count (rating)
        setupHeartRating(holder.heartContainer, item.getHeartCount());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDiaryItemClick(item);
            }
        });
    }

    private void setupHeartRating(LinearLayout container, int heartCount) {
        // Clear previous hearts
        container.removeAllViews();

        // Add the required number of hearts
        for (int i = 0; i < 5; i++) {
            ImageView heartView = new ImageView(container.getContext());
            heartView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            int paddingDp = 2;
            int paddingPx = (int) (paddingDp * container.getContext().getResources().getDisplayMetrics().density);
            heartView.setPadding(paddingPx, 0, paddingPx, 0);

            // Use filled heart for ratings up to heartCount, outline heart for the rest
            if (i < heartCount) {
                heartView.setImageResource(R.drawable.ic_heart_filled);
            } else {
                heartView.setImageResource(R.drawable.ic_heart_grey);
            }

            container.addView(heartView);
        }
    }

    @Override
    public int getItemCount() {
        return diaryItems.size();
    }

    static class DiaryViewHolder extends RecyclerView.ViewHolder {
        ImageView diaryThumbnail;
        TextView diaryTitle;
        TextView diaryDateRange;
        LinearLayout heartContainer;

        public DiaryViewHolder(@NonNull View itemView) {
            super(itemView);
            diaryThumbnail = itemView.findViewById(R.id.diaryThumbnail);
            diaryTitle = itemView.findViewById(R.id.diaryTitle);
            diaryDateRange = itemView.findViewById(R.id.diaryDateRange);
            heartContainer = itemView.findViewById(R.id.heartContainer);
        }
    }
}