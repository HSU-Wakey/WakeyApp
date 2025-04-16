package com.example.wakey.ui.album.domestic;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;

import java.util.List;

public class DomesticLocationAdapter extends RecyclerView.Adapter<DomesticLocationAdapter.LocationViewHolder> {

    private List<DomesticFragment.DomesticLocationItem> locations;
    private OnLocationClickListener listener;

    public interface OnLocationClickListener {
        void onLocationClick(DomesticFragment.DomesticLocationItem item);
    }

    public DomesticLocationAdapter(List<DomesticFragment.DomesticLocationItem> locations, OnLocationClickListener listener) {
        this.locations = locations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location_card, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        DomesticFragment.DomesticLocationItem item = locations.get(position);
        holder.locationName.setText(item.getName());

        // 사진 개수 표시 - 원래 포맷대로 유지
        if (holder.locationPhotoCount != null) {
            holder.locationPhotoCount.setText("사진 " + item.getPhotoCount() + "장");
        }

        // Load thumbnail if available
        if (item.getThumbnailPath() != null && !item.getThumbnailPath().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(Uri.parse(item.getThumbnailPath()))
                    .centerCrop()
                    .into(holder.locationThumbnail);
        } else {
            // Set a placeholder
            holder.locationThumbnail.setImageResource(R.drawable.placeholder_image);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLocationClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        ImageView locationThumbnail;
        TextView locationName;
        TextView locationPhotoCount;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            locationThumbnail = itemView.findViewById(R.id.locationThumbnail);
            locationName = itemView.findViewById(R.id.locationName);
        }
    }
}