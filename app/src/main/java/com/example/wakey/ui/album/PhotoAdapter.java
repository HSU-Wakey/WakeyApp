package com.example.wakey.ui.album;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;

import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    private List<String> photos;
    private List<String> selectedPhotos = new ArrayList<>();
    private boolean selectionMode = false;
    private OnPhotoClickListener listener;

    /**
     * Interface for photo click events
     */
    public interface OnPhotoClickListener {
        void onPhotoClick(String photoPath, int position);
        void onPhotoLongClick(String photoPath, int position);
    }

    public PhotoAdapter(List<String> photos) {
        this.photos = photos;
    }

    public void setOnItemClickListener(OnPhotoClickListener listener) {
        this.listener = listener;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        notifyDataSetChanged();
    }

    public void toggleSelection(String photoPath) {
        if (selectedPhotos.contains(photoPath)) {
            selectedPhotos.remove(photoPath);
        } else {
            selectedPhotos.add(photoPath);
        }
        notifyDataSetChanged();
    }

    public List<String> getSelectedPhotos() {
        return new ArrayList<>(selectedPhotos);
    }

    public void clearSelections() {
        selectedPhotos.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        String photoUrl = photos.get(position);

        // For real paths, load with Glide
        if (photoUrl.startsWith("/") || photoUrl.startsWith("content:")) {
            Glide.with(holder.itemView.getContext())
                    .load(Uri.parse(photoUrl))
                    .centerCrop()
                    .into(holder.photoImageView);
        } else {
            // For demo/placeholder paths
            holder.photoImageView.setImageResource(R.drawable.placeholder_image);
        }

        // Set selection state
        boolean isSelected = selectedPhotos.contains(photoUrl);
        holder.photoCheckbox.setChecked(isSelected);
        holder.photoCheckbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);

        // Setup click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPhotoClick(photoUrl, position);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onPhotoLongClick(photoUrl, position);
                return true;
            }
            return false;
        });

        // Handle video items (if needed)
        if (photoUrl.endsWith(".mp4") || photoUrl.endsWith(".mov")) {
            holder.videoDurationLayout.setVisibility(View.VISIBLE);
            // In a real app, you would get the actual duration
            holder.videoDurationText.setText("0:30");
        } else {
            holder.videoDurationLayout.setVisibility(View.GONE);
        }

        // Handle favorite status (not implemented yet)
        holder.favoriteIcon.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        CheckBox photoCheckbox;
        ImageView favoriteIcon;
        LinearLayout videoDurationLayout;
        TextView videoDurationText;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImageView = itemView.findViewById(R.id.photoImageView);
            photoCheckbox = itemView.findViewById(R.id.photoCheckbox);
            favoriteIcon = itemView.findViewById(R.id.favoriteIcon);
            videoDurationLayout = itemView.findViewById(R.id.videoDurationLayout);
            videoDurationText = itemView.findViewById(R.id.videoDurationText);
        }
    }
}