// Create a new file: ui/map/ReviewAdapter.java
package com.example.wakey.ui.map;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wakey.R;

import java.util.List;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private List<Review> reviews;

    public ReviewAdapter(List<Review> reviews) {
        this.reviews = reviews;
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        Review review = reviews.get(position);

        holder.authorTextView.setText(review.getAuthorName());

        // getRelativeTimeDescription 대신 getTime() 사용 또는 직접 포맷팅
        String timeAgo = review.getRelativeTimeDescription() != null ?
                review.getRelativeTimeDescription() :
                "최근";  // getRelativeTimeDescription이 없는 경우 대체
        holder.timeTextView.setText(timeAgo);

        holder.contentTextView.setText(review.getText());
    }

    @Override
    public int getItemCount() {
        return reviews != null ? reviews.size() : 0;
    }

    public void updateReviews(List<Review> newReviews) {
        this.reviews = newReviews;
        notifyDataSetChanged();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        TextView authorTextView;
        RatingBar ratingBar;
        TextView timeTextView;
        TextView contentTextView;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            authorTextView = itemView.findViewById(R.id.review_author);
            ratingBar = itemView.findViewById(R.id.review_rating);
            timeTextView = itemView.findViewById(R.id.review_time);
            contentTextView = itemView.findViewById(R.id.review_content);
        }
    }

    public class Review {
        private String authorName;
        private double rating;
        private String timeDescription;
        private String text;

        public Review(String authorName, double rating, String timeDescription, String text) {
            this.authorName = authorName;
            this.rating = rating;
            this.timeDescription = timeDescription;
            this.text = text;
        }

        public String getAuthorName() {
            return authorName;
        }

        public double getRating() {
            return rating;
        }

        public String getRelativeTimeDescription() {
            return timeDescription;
        }

        public String getText() {
            return text;
        }
    }
}