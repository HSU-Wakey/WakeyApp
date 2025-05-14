package com.example.wakey.ui.search;

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
import com.example.wakey.data.local.Photo;
import com.example.wakey.ui.photo.PhotoDetailFragment;
import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private List<SearchResult> searchResults;

    public static class SearchResult {
        public Photo photo;
        public float similarity;

        public SearchResult(Photo photo, float similarity) {
            this.photo = photo;
            this.similarity = similarity;
        }
    }

    public SearchResultAdapter(List<SearchResult> searchResults) {
        this.searchResults = searchResults;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 격자형 아이템 레이아웃 사용
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_grid_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchResult result = searchResults.get(position);

        // 이미지 로드
        Glide.with(holder.itemView.getContext())
                .load(Uri.parse(result.photo.getFilePath()))
                .centerCrop()
                .into(holder.imageView);

        // 유사도 표시
        holder.similarityTextView.setText(String.format("유사도: %.3f", result.similarity));

        // 사진 정보 표시 (필요에 따라)
        // holder.photoInfoTextView.setText(result.photo.getPhotoDate());

        // 클릭 리스너 설정 - PhotoDetailFragment로 이동 (해시태그 포함)
        holder.itemView.setOnClickListener(v -> {
            // PhotoDetailFragment로 이동
            PhotoDetailFragment detailFragment = PhotoDetailFragment.newInstance(
                    convertToTimelineItem(result.photo)
            );

            // Fragment를 사용하는 Activity의 FragmentManager로 표시
            if (v.getContext() instanceof androidx.fragment.app.FragmentActivity) {
                androidx.fragment.app.FragmentActivity activity =
                        (androidx.fragment.app.FragmentActivity) v.getContext();
                detailFragment.show(activity.getSupportFragmentManager(), "PHOTO_DETAIL");
            }
        });
    }

    // Photo를 TimelineItem으로 변환하는 헬퍼 메서드
    private com.example.wakey.data.model.TimelineItem convertToTimelineItem(Photo photo) {
        // Date 변환
        Date date = null;
        if (photo.dateTaken != null && !photo.dateTaken.isEmpty()) {
            try {
                // 날짜 형식에 맞는 SimpleDateFormat 사용 (Photo의 dateTaken 형식에 따라 조정)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
                date = sdf.parse(photo.dateTaken);
            } catch (java.text.ParseException e) {
                // 파싱 실패 시 현재 시간 사용
                date = new Date();
            }
        } else {
            date = new Date();
        }

        // LatLng 객체 생성
        LatLng latLng = null;
        if (photo.latitude != null && photo.longitude != null) {
            latLng = new LatLng(photo.latitude, photo.longitude);
        }

        // 주소 정보 설정
        String location = "";
        if (photo.locationDo != null) location += photo.locationDo + " ";
        if (photo.locationSi != null) location += photo.locationSi + " ";
        if (photo.locationGu != null) location += photo.locationGu;
        location = location.trim();

        // TimelineItem 생성 (매개변수가 필요한 생성자 사용)
        com.example.wakey.data.model.TimelineItem item = new com.example.wakey.data.model.TimelineItem(
                date,
                location,
                photo.filePath,
                latLng,
                "" // description (필요에 따라 수정)
        );

        // 해시태그 정보 설정 (Photo 객체에서 가져오기)
        if (photo.hashtags != null && !photo.hashtags.isEmpty()) {
            // hashtags를 detectedObjectPairs로 변환
            String[] tags = photo.hashtags.split("#");
            java.util.List<android.util.Pair<String, Float>> pairs = new java.util.ArrayList<>();
            for (String tag : tags) {
                if (!tag.trim().isEmpty()) {
                    pairs.add(new android.util.Pair<>(tag.trim(), 1.0f));
                }
            }
            item.setDetectedObjectPairs(pairs);
        }

        return item;
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    public void updateResults(List<SearchResult> newResults) {
        this.searchResults = newResults;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView similarityTextView;
        TextView photoInfoTextView;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.searchResultImageView);
            similarityTextView = itemView.findViewById(R.id.similarityTextView);
            photoInfoTextView = itemView.findViewById(R.id.photoInfoTextView);
        }
    }
}