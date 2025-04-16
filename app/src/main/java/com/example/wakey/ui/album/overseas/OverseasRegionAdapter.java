package com.example.wakey.ui.album.overseas;

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

public class OverseasRegionAdapter extends RecyclerView.Adapter<OverseasRegionAdapter.RegionViewHolder> {

    private List<OverseasRegionActivity.OverseasRegionItem> regions;
    private OnRegionClickListener listener;

    public interface OnRegionClickListener {
        void onRegionClick(OverseasRegionActivity.OverseasRegionItem item);
    }

    public OverseasRegionAdapter(List<OverseasRegionActivity.OverseasRegionItem> regions, OnRegionClickListener listener) {
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
        OverseasRegionActivity.OverseasRegionItem item = regions.get(position);
        holder.locationName.setText(item.getName());

        // 원래 포맷대로 날짜와 사진 개수 표시
        String dateText = item.getDate();
        if (dateText != null && !dateText.isEmpty()) {
            holder.locationDate.setText(dateText + " • 사진 " + item.getPhotoCount() + "장");
        } else {
            holder.locationDate.setText("사진 " + item.getPhotoCount() + "장");
        }

        // 썸네일 로드
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