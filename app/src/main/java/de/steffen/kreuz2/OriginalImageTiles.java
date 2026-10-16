package de.steffen.kreuz2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** File-backed detail tiles. Grid coordinates stay in preview space; original pixels are never discarded. */
final class OriginalImageTiles {
    private static final int TILE = 512;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable invalidate;
    private final ErrorListener error;
    private final Paint paint = new Paint();
    private final Set<String> pending = new HashSet<>();
    private final LruCache<String, Bitmap> cache;
    private volatile int generation;
    private volatile Rect wanted;
    private volatile int wantedSample;
    private boolean closed;
    private BitmapRegionDecoder decoder; // Accessed only by worker.
    private int originalWidth, originalHeight, previewWidth, previewHeight;

    interface ErrorListener { void failed(String message); }
    OriginalImageTiles(Runnable invalidate, ErrorListener errorListener) {
        this.invalidate = invalidate; this.error = errorListener;
        int budget = (int) Math.max(8L * 1024 * 1024, Math.min(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 10));
        cache = new LruCache<String, Bitmap>(budget) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
        };
    }
    void setSource(File file, int width, int height) {
        if (closed) return;
        final int version = ++generation;
        originalWidth = originalHeight = 0; previewWidth = width; previewHeight = height;
        wanted = null;
        cache.evictAll(); pending.clear();
        worker.execute(() -> {
            if (decoder != null) { decoder.recycle(); decoder = null; }
            if (version != generation || file == null) return;
            try {
                decoder = BitmapRegionDecoder.newInstance(file.getAbsolutePath(), false);
                if (decoder == null) throw new java.io.IOException("Originalbild lässt sich nicht öffnen.");
                int w = decoder.getWidth(), h = decoder.getHeight();
                ui.post(() -> { if (!closed && version == generation) { originalWidth = w; originalHeight = h; invalidate.run(); } });
            } catch (Exception | OutOfMemoryError failure) {
                ui.post(() -> { if (!closed && version == generation) error.failed("Originaldetails konnten nicht geladen werden. Bitte das Original als PNG oder JPEG erneut zuordnen."); });
            }
        });
    }
    static int sampleSize(float nativeScale) {
        int sample = 1;
        while (sample < 128 && sample * 2 * nativeScale <= 1f) sample *= 2;
        return sample;
    }
    static int boundedSample(Rect visible, float nativeScale, int budget) {
        int sample = sampleSize(nativeScale);
        while (sample < 256) {
            int step = TILE * sample;
            long columns = (visible.right - 1) / step - visible.left / step + 1;
            long rows = (visible.bottom - 1) / step - visible.top / step + 1;
            if (columns * rows * TILE * TILE * 4 <= budget) break;
            sample *= 2;
        }
        return sample;
    }
    static Rect visibleSource(int viewWidth, int viewHeight, float scale, float ox, float oy,
                              int previewWidth, int previewHeight, int originalWidth, int originalHeight) {
        float sx = originalWidth / (float) previewWidth, sy = originalHeight / (float) previewHeight;
        return new Rect(Math.max(0, (int) Math.floor(-ox / scale * sx)), Math.max(0, (int) Math.floor(-oy / scale * sy)),
                Math.min(originalWidth, (int) Math.ceil((viewWidth - ox) / scale * sx)),
                Math.min(originalHeight, (int) Math.ceil((viewHeight - oy) / scale * sy)));
    }
    void draw(Canvas canvas, int viewWidth, int viewHeight, float scale, float ox, float oy) {
        if (closed || originalWidth == 0 || scale < .65f) { wanted = null; return; }
        // If preview pixels are already the original pixels, the base bitmap is sufficient.
        if (originalWidth <= previewWidth && originalHeight <= previewHeight) return;
        float nativeScale = scale * previewWidth / originalWidth;
        Rect visible = visibleSource(viewWidth, viewHeight, scale, ox, oy, previewWidth, previewHeight, originalWidth, originalHeight);
        if (visible.isEmpty()) return;
        int sample = boundedSample(visible, nativeScale, cache.maxSize()), step = TILE * sample;
        wantedSample = sample; wanted = visible;
        paint.setFilterBitmap(nativeScale * sample < 1f);
        for (int top = visible.top / step * step; top < visible.bottom; top += step) {
            for (int left = visible.left / step * step; left < visible.right; left += step) {
                String key = sample + ":" + left + ":" + top;
                Rect source = new Rect(left, top, Math.min(originalWidth, left + step), Math.min(originalHeight, top + step));
                Bitmap tile = cache.get(key);
                if (tile != null) {
                    RectF destination = new RectF(source.left * previewWidth / (float) originalWidth, source.top * previewHeight / (float) originalHeight,
                            source.right * previewWidth / (float) originalWidth, source.bottom * previewHeight / (float) originalHeight);
                    canvas.drawBitmap(tile, null, destination, paint);
                } else if (pending.add(key)) request(key, source, sample);
            }
        }
    }
    private void request(String key, Rect source, int sample) {
        final int version = generation;
        worker.execute(() -> {
            if (version != generation) return;
            Rect current = wanted;
            if (current == null || sample != wantedSample || !Rect.intersects(source, current)) {
                ui.post(() -> { if (!closed && version == generation) { pending.remove(key); invalidate.run(); } });
                return;
            }
            Bitmap result = null;
            try {
                if (decoder != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888; options.inScaled = false;
                    result = decoder.decodeRegion(source, options);
                }
            } catch (Exception | OutOfMemoryError ignored) { /* Keep the overview tile when resources are temporarily unavailable. */ }
            Bitmap ready = result;
            ui.post(() -> {
                if (closed || version != generation) { if (ready != null) ready.recycle(); return; }
                // A failed request stays pending until the source changes, avoiding redraw/retry loops.
                if (ready != null) { pending.remove(key); cache.put(key, ready); invalidate.run(); }
            });
        });
    }
    void close() {
        if (closed) return;
        closed = true; generation++; cache.evictAll(); pending.clear();
        worker.execute(() -> { if (decoder != null) { decoder.recycle(); decoder = null; } });
        worker.shutdown();
    }
}
