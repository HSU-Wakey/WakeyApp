package com.example.wakey.ui.search;

import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.local.Photo;
import com.example.wakey.ui.photo.PhotoDetailDialogFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HashtagPhotoAdapter extends RecyclerView.Adapter<HashtagPhotoAdapter.ViewHolder> {

    private List<Photo> photos;
    private FragmentManager fragmentManager;

    public HashtagPhotoAdapter(List<Photo> photos) {
        this.photos = photos;
    }

    public HashtagPhotoAdapter(List<Photo> photos, FragmentManager fragmentManager) {
        this.photos = photos;
        this.fragmentManager = fragmentManager;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hashtag_photo, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Photo photo = photos.get(position);

        // 이미지 로드
        Glide.with(holder.itemView.getContext())
                .load(Uri.parse(photo.getFilePath()))
                .centerCrop()
                .into(holder.photoImageView);

        // 클릭 리스너 설정 - 사진 상세 보기 다이얼로그 표시
        holder.itemView.setOnClickListener(v -> {
            if (fragmentManager != null) {
                showPhotoDetailDialog(photo);
            } else {
                Log.e("HashtagPhotoAdapter", "FragmentManager is null. Cannot show detail dialog.");
            }
        });
    }

    private void showPhotoDetailDialog(Photo photo) {
        try {
            // 파일 이름 추출
            String fileName = new File(photo.getFilePath()).getName();

            // 태그 목록 준비 (이제 getTags() 메소드 사용)
            List<String> tags = photo.getTags();

            // 다이얼로그 프래그먼트 생성 및 표시
            PhotoDetailDialogFragment dialogFragment = PhotoDetailDialogFragment.newInstance(
                    photo.getFilePath(),
                    fileName,
                    photo.getTimestamp(), // getTimestamp() 메소드 사용
                    tags
            );

            dialogFragment.show(fragmentManager, "PhotoDetail");
        } catch (Exception e) {
            Log.e("HashtagPhotoAdapter", "Error showing photo detail: " + e.getMessage(), e);
        }
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    public void updatePhotos(List<Photo> newPhotos) {
        this.photos = newPhotos;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView similarityTextView;

        ViewHolder(View itemView) {
            super(itemView);
            photoImageView = itemView.findViewById(R.id.photoImageView);
            similarityTextView = itemView.findViewById(R.id.similarityTextView);
        }
    }
}