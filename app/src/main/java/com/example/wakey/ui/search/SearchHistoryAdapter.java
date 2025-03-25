package com.example.wakey.ui.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wakey.R;
import com.example.wakey.data.model.SearchHistoryItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    private List<SearchHistoryItem> items;
    private Context context;
    private OnHistoryItemClickListener listener;

    public interface OnHistoryItemClickListener {
        void onHistoryItemClick(SearchHistoryItem item);
    }

    public SearchHistoryAdapter(List<SearchHistoryItem> items) {
        this.items = items;
    }

    public void setOnHistoryItemClickListener(OnHistoryItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_search_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchHistoryItem item = items.get(position);

        // 이미지 로드
        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            Glide.with(context)
                    .load(item.getImagePath())
                    .centerCrop()
                    .into(holder.historyImageView);
        } else {
            // 기본 이미지 표시
            holder.historyImageView.setImageResource(R.drawable.default_search_image);
        }

        // 검색어 설정
        holder.historyQueryTextView.setText(item.getQuery());

        // 타임스탬프 설정
        if (holder.timestampTextView != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault());
            holder.timestampTextView.setText(sdf.format(new Date(item.getTimestamp())));
        }

        // 클릭 리스너 설정
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHistoryItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? Math.min(items.size(), 10) : 0; // 최대 10개까지만 표시
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView historyImageView;
        TextView historyQueryTextView;
        TextView timestampTextView;

        ViewHolder(View itemView) {
            super(itemView);
            historyImageView = itemView.findViewById(R.id.historyImageView);
            historyQueryTextView = itemView.findViewById(R.id.historyQueryTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
        }
    }
}