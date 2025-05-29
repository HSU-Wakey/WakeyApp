package com.example.wakey.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.util.LruCache;

import androidx.exifinterface.media.ExifInterface;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Matrix;

public class ThumbnailCache {
    private static final String TAG = "ThumbnailCache";
    private static ThumbnailCache instance;

    private final LruCache<String, Bitmap> memoryCache;
    private final ConcurrentHashMap<String, Boolean> loadingMap;
    private final ExecutorService executorService;
    private final Context context;

    // 썸네일 크기 상수
    private static final int THUMBNAIL_SIZE = 100; // 100x100 픽셀
    private static final int MEMORY_CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    public interface ThumbnailLoadCallback {
        void onThumbnailLoaded(Bitmap bitmap);
        void onLoadFailed();
    }

    private ThumbnailCache(Context context) {
        this.context = context.getApplicationContext();
        this.loadingMap = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(4);

        // LRU 캐시 초기화
        this.memoryCache = new LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
    }

    public static synchronized ThumbnailCache getInstance(Context context) {
        if (instance == null) {
            instance = new ThumbnailCache(context);
        }
        return instance;
    }

    /**
     * 썸네일을 비동기로 로드합니다.
     */
    public void loadThumbnail(String path, ThumbnailLoadCallback callback) {
        if (path == null || path.isEmpty()) {
            callback.onLoadFailed();
            return;
        }

        // 1. 메모리 캐시 확인
        Bitmap cached = memoryCache.get(path);
        if (cached != null && !cached.isRecycled()) {
            callback.onThumbnailLoaded(cached);
            return;
        }

        // 2. 이미 로딩 중인지 확인
        if (loadingMap.containsKey(path)) {
            return;
        }

        loadingMap.put(path, true);

        // 3. 백그라운드에서 로드
        executorService.execute(() -> {
            try {
                Bitmap bitmap = loadThumbnailFromPath(path);

                if (bitmap != null) {
                    memoryCache.put(path, bitmap);

                    // 메인 스레드에서 콜백 실행
                    if (callback != null) {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> callback.onThumbnailLoaded(bitmap));
                    }
                } else {
                    if (callback != null) {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> callback.onLoadFailed());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "썸네일 로드 실패: " + e.getMessage());
                if (callback != null) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> callback.onLoadFailed());
                }
            } finally {
                loadingMap.remove(path);
            }
        });
    }

    /**
     * 동기적으로 썸네일을 가져옵니다 (캐시된 것만)
     */
    public Bitmap getThumbnailSync(String path) {
        if (path == null || path.isEmpty()) return null;
        return memoryCache.get(path);
    }

    /**
     * 실제 썸네일 로딩 로직
     */
    private Bitmap loadThumbnailFromPath(String path) {
        try {
            Log.d(TAG, "📂 썸네일 로드 시작: " + path);

            Uri uri = Uri.parse(path);
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            // 1단계: 이미지 크기만 먼저 읽기
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            // 2단계: 샘플 크기 계산 (더 작은 썸네일을 위해)
            options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565; // 메모리 절약

            // 3단계: 실제 비트맵 디코딩
            inputStream = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            if (bitmap == null) return null;

            // 4단계: 회전 정보 적용
            InputStream exifInput = context.getContentResolver().openInputStream(uri);
            ExifInterface exif = new ExifInterface(exifInput);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            exifInput.close();

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
            }

            if (!matrix.isIdentity()) {
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                bitmap = rotated;
            }

            // 5단계: 정확한 크기로 스케일링
            if (bitmap.getWidth() > THUMBNAIL_SIZE || bitmap.getHeight() > THUMBNAIL_SIZE) {
                float scale = Math.min(
                        (float) THUMBNAIL_SIZE / bitmap.getWidth(),
                        (float) THUMBNAIL_SIZE / bitmap.getHeight()
                );

                int newWidth = Math.round(bitmap.getWidth() * scale);
                int newHeight = Math.round(bitmap.getHeight() * scale);

                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }

            Log.d(TAG, "✅ 썸네일 로드 완료: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "❌ 썸네일 로딩 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * inSampleSize 계산
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * 특정 이미지의 캐시 삭제
     */
    public void removeThumbnail(String path) {
        if (path != null) {
            Bitmap bitmap = memoryCache.remove(path);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    /**
     * 전체 캐시 클리어
     */
    public void clearCache() {
        memoryCache.evictAll();
    }

    /**
     * 리소스 정리
     */
    public void shutdown() {
        executorService.shutdown();
        clearCache();
    }

    /**
     * 캐시 상태 정보
     */
    public String getCacheStats() {
        return String.format("Cache: %d/%d KB used, %d items",
                memoryCache.size() / 1024,
                memoryCache.maxSize() / 1024,
                memoryCache.snapshot().size());
    }
}