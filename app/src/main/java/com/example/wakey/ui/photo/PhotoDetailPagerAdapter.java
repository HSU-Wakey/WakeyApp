package com.example.wakey.ui.photo;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.local.Photo;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

public class PhotoDetailPagerAdapter extends RecyclerView.Adapter<PhotoDetailPagerAdapter.PhotoViewHolder> {

    private final Context context;
    private final List<Photo> photos;

    public PhotoDetailPagerAdapter(Context context, List<Photo> photos) {
        this.context = context;
        this.photos = photos;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_photo_detail_page, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Photo photo = photos.get(position);

        // 이미지 로드
        Glide.with(context)
                .load(Uri.parse(photo.getFilePath()))
                .into(holder.photoView);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        PhotoView photoView;

        PhotoViewHolder(View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photoView);
        }
    }
}