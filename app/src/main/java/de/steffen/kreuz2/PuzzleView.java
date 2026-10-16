package de.steffen.kreuz2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

/** A screenshot with editable cell overlays, pinch zoom, pan and a visual crop editor. */
public final class PuzzleView extends View {
    public interface Listener { void selected(); void longPress(int cell); default void touched(int cell) { } default void imageError(String message) { } }
    public static final long HOLD_TO_SEARCH_MS = 1000;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private final OriginalImageTiles originalTiles;
    private Bitmap bitmap;
    private Puzzle puzzle;
    private Listener listener;
    private float scale = 1, fit = 1, ox, oy;
    private boolean cropping, multitouch;
    private int corner;
    private int lastTappedCell = -1;
    private float lastTapScale, lastTapOx, lastTapOy;
    private float holdX, holdY;
    private int holdCell = -1;
    private boolean holdTriggered;
    private final Runnable held = this::fireHold;
    private void fireHold() {
        if (holdCell >= 0 && isEnabled() && !cropping && !multitouch && listener != null) {
            holdTriggered = true;
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            // Cancel GestureDetector's pending tap/double-tap before opening another screen.
            MotionEvent cancel = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, holdX, holdY, 0);
            gestures.onTouchEvent(cancel); cancel.recycle();
            listener.longPress(holdCell);
        }
    }
    private int previewCols = 10, previewRows = 10;
    public final RectF crop = new RectF();

    public PuzzleView(Context context) {
        super(context);
        originalTiles = new OriginalImageTiles(this::invalidate, message -> { if (listener != null) listener.imageError(message); });
        setContentDescription("Kreuzworträtsel. Feld antippen zum Schreiben. Fragetext eine Sekunde gedrückt halten für eine Google-Suche. Mit zwei Fingern vergrößern.");
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (cropping) return false;
                float old = scale;
                scale = Math.max(fit, Math.min(Math.max(fit * 32, 4f), scale * detector.getScaleFactor()));
                ox = detector.getFocusX() - (detector.getFocusX() - ox) * scale / old;
                oy = detector.getFocusY() - (detector.getFocusY() - oy) * scale / old;
                constrain(); invalidate(); return true;
            }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                return tap(e);
            }
            @Override public boolean onDoubleTap(MotionEvent e) { return true; }
            @Override public boolean onDoubleTapEvent(MotionEvent e) {
                // GestureDetector consumes the second tap, so handle its release explicitly.
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    // Opening the keyboard can resize the image between the two taps.
                    int previous = cellAt(e.getX(), e.getY(), lastTapScale, lastTapOx, lastTapOy);
                    if (previous == lastTappedCell && previous >= 0 && puzzle != null && !cropping && !multitouch && !holdTriggered) {
                        puzzle.select(previous, true); invalidate();
                        if (listener != null) { listener.touched(previous); listener.selected(); }
                        performClick(); return true;
                    }
                    return tap(e);
                }
                return true;
            }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (!cropping && !scaleDetector.isInProgress()) { ox -= dx; oy -= dy; constrain(); invalidate(); }
                return true;
            }
        });
        gestures.setIsLongpressEnabled(false);
    }
    private boolean tap(MotionEvent e) {
        if (cropping || multitouch || holdTriggered || puzzle == null) return true;
        int cell = cellAt(e.getX(), e.getY());
        lastTappedCell = cell; lastTapScale = scale; lastTapOx = ox; lastTapOy = oy;
        if (cell >= 0) { puzzle.select(cell, true); invalidate(); if (listener != null) { listener.touched(cell); listener.selected(); } }
        performClick(); return true;
    }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setOriginalImage(java.io.File file) {
        if (bitmap != null) originalTiles.setSource(file, bitmap.getWidth(), bitmap.getHeight());
        invalidate();
    }
    public void releaseImages() { originalTiles.close(); }
    public void setPuzzle(Puzzle puzzle, Bitmap bitmap, boolean reset) {
        cancelHold();
        this.puzzle = puzzle; this.bitmap = bitmap; cropping = false;
        if (reset) { lastTappedCell = -1; fitImage(); } invalidate();
    }
    public void fitImage() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        fit = Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
        scale = fit; ox = (getWidth() - bitmap.getWidth() * scale) / 2; oy = (getHeight() - bitmap.getHeight() * scale) / 2;
        invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0 || oldh == 0 || Math.abs(scale - fit) < .001f) fitImage();
        else {
            fit = bitmap == null ? 1 : Math.min(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
            scale = Math.max(fit, scale); constrain();
        }
        if (!cropping) revealSelection();
    }
    private void constrain() {
        if (bitmap == null) return;
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        ox = w <= getWidth() ? (getWidth() - w) / 2 : Math.max(getWidth() - w, Math.min(0, ox));
        oy = h <= getHeight() ? (getHeight() - h) / 2 : Math.max(getHeight() - h, Math.min(0, oy));
    }
    public void revealSelection() {
        if (puzzle == null || !puzzle.valid(puzzle.selected)) return;
        int c = puzzle.selected % puzzle.cols, r = puzzle.selected / puzzle.cols;
        float x0 = puzzle.xs[c] * scale + ox, x1 = puzzle.xs[c + 1] * scale + ox;
        float y0 = puzzle.ys[r] * scale + oy, y1 = puzzle.ys[r + 1] * scale + oy;
        float margin = 12 * getResources().getDisplayMetrics().density;
        if (x0 < margin) ox += margin - x0;
        else if (x1 > getWidth() - margin) ox -= x1 - getWidth() + margin;
        if (y0 < margin) oy += margin - y0;
        else if (y1 > getHeight() - margin) oy -= y1 - getHeight() + margin;
        constrain(); invalidate();
    }
    public void startCrop() {
        cancelHold();
        if (bitmap == null) return;
        cropping = true; fitImage();
        if (puzzle != null) crop.set(puzzle.xs[0], puzzle.ys[0], puzzle.xs[puzzle.cols], puzzle.ys[puzzle.rows]);
        else crop.set(0, 0, bitmap.getWidth() - 1, bitmap.getHeight() - 1);
        previewCols = puzzle == null ? 10 : puzzle.cols; previewRows = puzzle == null ? 10 : puzzle.rows;
        invalidate();
    }
    public void preview(int cols, int rows) { previewCols = cols; previewRows = rows; invalidate(); }
    public void endCrop() { cropping = false; invalidate(); }
    private int cellAt(float sx, float sy) {
        return cellAt(sx, sy, scale, ox, oy);
    }
    private int cellAt(float sx, float sy, float imageScale, float imageOx, float imageOy) {
        if (puzzle == null) return -1;
        if (imageScale <= 0) return -1;
        float x = (sx - imageOx) / imageScale, y = (sy - imageOy) / imageScale;
        int c = -1, r = -1;
        for (int i = 0; i < puzzle.cols; i++) if (x >= puzzle.xs[i] && x < puzzle.xs[i + 1]) { c = i; break; }
        for (int i = 0; i < puzzle.rows; i++) if (y >= puzzle.ys[i] && y < puzzle.ys[i + 1]) { r = i; break; }
        return c < 0 || r < 0 ? -1 : r * puzzle.cols + c;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private void cancelHold() { removeCallbacks(held); holdCell = -1; }
    public void cancelPendingHold() { cancelHold(); }
    @Override protected void onDetachedFromWindow() { cancelHold(); super.onDetachedFromWindow(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) { cancelHold(); return true; }
        if (bitmap == null) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            cancelHold(); multitouch = false; holdTriggered = false;
            holdX = event.getX(); holdY = event.getY();
            if (!cropping) { holdCell = cellAt(holdX, holdY); if (holdCell >= 0) postDelayed(held, HOLD_TO_SEARCH_MS); }
        }
        if (event.getPointerCount() > 1) { multitouch = true; cancelHold(); }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - holdX, dy = event.getY() - holdY;
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (dx * dx + dy * dy > slop * slop) cancelHold();
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) cancelHold();
        if (cropping) {
            float x = Math.max(0, Math.min(bitmap.getWidth() - 1, (event.getX() - ox) / scale));
            float y = Math.max(0, Math.min(bitmap.getHeight() - 1, (event.getY() - oy) / scale));
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                corner = (Math.abs(x - crop.right) < Math.abs(x - crop.left) ? 1 : 0)
                        + (Math.abs(y - crop.bottom) < Math.abs(y - crop.top) ? 2 : 0);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (corner % 2 == 0) crop.left = Math.min(x, crop.right - 10); else crop.right = Math.max(x, crop.left + 10);
                if (corner < 2) crop.top = Math.min(y, crop.bottom - 10); else crop.bottom = Math.max(y, crop.top + 10);
                invalidate();
            }
            return true;
        }
        scaleDetector.onTouchEvent(event); gestures.onTouchEvent(event); return true;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(231, 238, 242));
        if (bitmap == null) {
            paint.setColor(Color.rgb(36, 64, 79)); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(20 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText("Dein Rätsel, dein Tempo.", getWidth() / 2f, getHeight() / 2f - 20, paint);
            paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText("Oben einen Screenshot öffnen.", getWidth() / 2f, getHeight() / 2f + 15, paint);
            return;
        }
        canvas.save(); canvas.translate(ox, oy); canvas.scale(scale, scale);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); paint.setAlpha(255);
        paint.setFilterBitmap(scale < 1f);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        originalTiles.draw(canvas, getWidth(), getHeight(), scale, ox, oy);
        if (cropping) {
            paint.setColor(0x880e2633);
            canvas.drawRect(0, 0, bitmap.getWidth(), crop.top, paint);
            canvas.drawRect(0, crop.bottom, bitmap.getWidth(), bitmap.getHeight(), paint);
            canvas.drawRect(0, crop.top, crop.left, crop.bottom, paint);
            canvas.drawRect(crop.right, crop.top, bitmap.getWidth(), crop.bottom, paint);
            drawLines(canvas, Puzzle.evenly(crop.left, crop.right, previewCols), Puzzle.evenly(crop.top, crop.bottom, previewRows));
            paint.setColor(0xff007c91);
            for (float x : new float[] {crop.left, crop.right}) for (float y : new float[] {crop.top, crop.bottom}) canvas.drawCircle(x, y, 9 / scale, paint);
        } else if (puzzle != null) {
            for (int i = 0; i < puzzle.types.length; i++) {
                int c = i % puzzle.cols, r = i / puzzle.cols;
                float l = puzzle.xs[c], t = puzzle.ys[r], right = puzzle.xs[c + 1], bottom = puzzle.ys[r + 1];
                if (puzzle.showGrid && puzzle.types[i] != Puzzle.OPEN) {
                    paint.setColor(puzzle.types[i] == Puzzle.CLUE ? 0x33ffad20 : 0x448c4756);
                    canvas.drawRect(l, t, right, bottom, paint);
                }
                if (!puzzle.letters[i].isEmpty()) {
                    paint.setColor(0xff073e55); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    paint.setTextSize(Math.min(right - l, bottom - t) * .72f); paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(puzzle.letters[i], (l + right) / 2, (t + bottom) / 2 - (paint.ascent() + paint.descent()) / 2, paint);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT);
                }
            }
            if (puzzle.showGrid) drawLines(canvas, puzzle.xs, puzzle.ys);
            if (puzzle.valid(puzzle.selected)) {
                int c = puzzle.selected % puzzle.cols, r = puzzle.selected / puzzle.cols;
                RectF rect = new RectF(puzzle.xs[c], puzzle.ys[r], puzzle.xs[c + 1], puzzle.ys[r + 1]);
                paint.setColor(0x33007c91); paint.setStyle(Paint.Style.FILL); canvas.drawRect(rect, paint);
                paint.setColor(0xff007c91); paint.setStrokeWidth(2.5f / scale); paint.setStyle(Paint.Style.STROKE); canvas.drawRect(rect, paint);
                paint.setStyle(Paint.Style.FILL);
                float x = rect.centerX(), y = rect.centerY(), delta = 5 / scale;
                if (puzzle.direction == Puzzle.RIGHT || puzzle.direction == Puzzle.LEFT) {
                    x = puzzle.direction == Puzzle.RIGHT ? rect.right : rect.left;
                    canvas.drawLine(x, y, x + (puzzle.direction == Puzzle.RIGHT ? -delta : delta), y - delta, paint);
                    canvas.drawLine(x, y, x + (puzzle.direction == Puzzle.RIGHT ? -delta : delta), y + delta, paint);
                } else {
                    y = puzzle.direction == Puzzle.DOWN ? rect.bottom : rect.top;
                    canvas.drawLine(x, y, x - delta, y + (puzzle.direction == Puzzle.DOWN ? -delta : delta), paint);
                    canvas.drawLine(x, y, x + delta, y + (puzzle.direction == Puzzle.DOWN ? -delta : delta), paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL); canvas.restore();
    }
    private void drawLines(Canvas canvas, float[] xs, float[] ys) {
        paint.setColor(0xbb007c91); paint.setStrokeWidth(1.2f / scale);
        for (float x : xs) canvas.drawLine(x, ys[0], x, ys[ys.length - 1], paint);
        for (float y : ys) canvas.drawLine(xs[0], y, xs[xs.length - 1], y, paint);
    }
}
