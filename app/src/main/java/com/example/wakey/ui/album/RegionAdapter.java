package com.example.wakey.ui.album;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;

import java.util.List;

public class RegionAdapter extends RecyclerView.Adapter<RegionAdapter.RegionViewHolder> {

    // 지역 아이템 모델 클래스
    public static class RegionItem {
        private String name;
        private String code;
        private String date;
        private String thumbnailUrl;

        public RegionItem(String name, String code, String date, String thumbnailUrl) {
            this.name = name;
            this.code = code;
            this.date = date;
            this.thumbnailUrl = thumbnailUrl;
        }

        public String getName() { return name; }
        public String getCode() { return code; }
        public String getDate() { return date; }
        public String getThumbnailUrl() { return thumbnailUrl; }
    }

    private List<RegionItem> regions;
    private OnRegionClickListener listener;

    public interface OnRegionClickListener {
        void onRegionClick(RegionItem item);
    }

    public RegionAdapter(List<RegionItem> regions, OnRegionClickListener listener) {
        this.regions = regions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RegionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_region, parent, false);
        return new RegionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RegionViewHolder holder, int position) {
        RegionItem item = regions.get(position);
        holder.locationName.setText(item.getName());
        holder.locationDate.setText(item.getDate());

        // 썸네일 로드 (실제 앱에서는 Glide 사용)
        if (item.getThumbnailUrl() != null && !item.getThumbnailUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(Uri.parse(item.getThumbnailUrl()))
                    .centerCrop()
                    .into(holder.locationThumbnail);
        } else {
            // 기본 이미지 설정
            holder.locationThumbnail.setImageResource(R.drawable.placeholder_image);
        }

        holder.locationCardView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRegionClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return regions.size();
    }

    static class RegionViewHolder extends RecyclerView.ViewHolder {
        CardView locationCardView;
        ImageView locationThumbnail;
        TextView locationName;
        TextView locationDate;

        public RegionViewHolder(@NonNull View itemView) {
            super(itemView);
            locationCardView = itemView.findViewById(R.id.locationCardView);
            locationThumbnail = itemView.findViewById(R.id.locationThumbnail);
            locationName = itemView.findViewById(R.id.locationName);
            locationDate = itemView.findViewById(R.id.locationDate);
        }
    }
}