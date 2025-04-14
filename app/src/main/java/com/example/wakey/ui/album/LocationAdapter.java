package com.example.wakey.ui.album;

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

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.LocationViewHolder> {

    private List<SmartAlbumActivity.LocationItem> locations;
    private OnLocationClickListener listener;

    public interface OnLocationClickListener {
        void onLocationClick(SmartAlbumActivity.LocationItem item);
    }

    public LocationAdapter(List<SmartAlbumActivity.LocationItem> locations, OnLocationClickListener listener) {
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
        SmartAlbumActivity.LocationItem item = locations.get(position);
        holder.locationName.setText(item.getName());

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

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            locationThumbnail = itemView.findViewById(R.id.locationThumbnail);
            locationName = itemView.findViewById(R.id.locationName);
        }
    }
}