package de.steffen.kreuz2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/** Only recognized text is used in the browser URL; the cropped image stays on-device. */
public final class ClueSearch {
    public interface Callback { void success(String text); void failure(Exception error); }
    public interface Reader { void read(Bitmap image, Puzzle puzzle, int cell, Callback callback); }
    public static final class OnDeviceReader implements Reader {
        @Override public void read(Bitmap image, Puzzle puzzle, int cell, Callback callback) {
            Bitmap prepared = prepare(image, puzzle, cell);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            try {
                recognizer.process(InputImage.fromBitmap(prepared, 0))
                        .addOnSuccessListener(result -> callback.success(clean(result.getText())))
                        .addOnFailureListener(callback::failure)
                        .addOnCompleteListener(task -> { recognizer.close(); prepared.recycle(); });
            } catch (RuntimeException error) {
                recognizer.close(); prepared.recycle(); callback.failure(error);
            }
        }
    }
    public static Rect bounds(Bitmap image, Puzzle puzzle, int cell) {
        if (cell < 0 || cell >= puzzle.types.length) throw new IllegalArgumentException("Bitte ein Feld auswählen.");
        int c = cell % puzzle.cols, r = cell / puzzle.cols;
        int left = Math.max(0, (int) Math.ceil(puzzle.xs[c]) + 2);
        int top = Math.max(0, (int) Math.ceil(puzzle.ys[r]) + 2);
        int right = Math.min(image.getWidth(), (int) Math.floor(puzzle.xs[c + 1]) - 1);
        int bottom = Math.min(image.getHeight(), (int) Math.floor(puzzle.ys[r + 1]) - 1);
        if (right - left < 3 || bottom - top < 3) throw new IllegalArgumentException("Der Fragetext-Ausschnitt ist zu klein.");
        return new Rect(left, top, right, bottom);
    }
    public static Bitmap prepare(Bitmap image, Puzzle puzzle, int cell) {
        Rect area = bounds(image, puzzle, cell);
        float factor = Math.min(8f, Math.max(1f, 720f / Math.max(area.width(), area.height())));
        int w = Math.round(area.width() * factor), h = Math.round(area.height() * factor), padding = 24;
        Bitmap out = Bitmap.createBitmap(w + padding * 2, h + padding * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out); canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(image, area, new RectF(padding, padding, padding + w, padding + h), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return out;
    }
    public static String clean(String text) {
        if (text == null) return "";
        // Join wrapped words before joining the lines of a printed clue.
        return text.replaceAll("(?<=\\p{L})[-‐]\\s*\\n\\s*(?=\\p{Ll})", "")
                .replaceAll("[→↓←↑↗↘↙↖►▼◄▲]", " ").replaceAll("\\s+", " ").trim();
    }
    public static boolean readable(String text) {
        String cleaned = clean(text);
        int letters = 0;
        for (int i = 0; i < cleaned.length();) { int cp = cleaned.codePointAt(i); if (Character.isLetter(cp)) letters++; i += Character.charCount(cp); }
        return cleaned.length() <= 600 && letters >= 2;
    }
    public static Uri googleUrl(String text) {
        return new Uri.Builder().scheme("https").authority("www.google.com").path("/search")
                .appendQueryParameter("q", clean(text) + " Kreuzworträtsel Lösung").build();
    }
}
