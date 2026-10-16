package de.steffen.kreuz2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Saves the copied image and an atomic JSON document; no dependency on the original URI. */
public final class PuzzleStore {
    public static final int MAX_IMAGE_BYTES = 35 * 1024 * 1024;
    public static final int MAX_DOCUMENT_BYTES = 80 * 1024 * 1024;
    private final File root;
    public PuzzleStore(Context context) { root = new File(context.getFilesDir(), "puzzles"); }
    public static final class Entry {
        public String id, name; public long updated;
        @Override public String toString() { return name; }
    }
    public static final class Loaded {
        public final Puzzle puzzle; public final Bitmap bitmap;
        public final byte[] original;
        Loaded(Puzzle puzzle, Bitmap bitmap) { this(puzzle, bitmap, null); }
        Loaded(Puzzle puzzle, Bitmap bitmap, byte[] original) { this.puzzle = puzzle; this.bitmap = bitmap; this.original = original; }
    }
    private File folder(String id) throws IOException {
        if (!id.matches("[a-fA-F0-9-]{36}")) throw new IOException("Ungültige Spielstand-ID.");
        File dir = new File(root, id);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Speicher nicht verfügbar.");
        return dir;
    }
    public File originalFile(String id) {
        if (id == null || !id.matches("[a-fA-F0-9-]{36}")) return null;
        File source = new File(new File(root, id), "original.image");
        return source.isFile() ? source : null;
    }
    public void saveOriginal(String id, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw new IOException("Originalbild ist leer oder größer als 35 MB.");
        BitmapFactory.Options bounds = imageBounds(bytes);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Keine unterstützte Originalbilddatei.");
        AtomicFile target = new AtomicFile(new File(folder(id), "original.image"));
        FileOutputStream out = target.startWrite();
        try { out.write(bytes); target.finishWrite(out); }
        catch (Exception error) { target.failWrite(out); throw new IOException(error); }
    }
    public void save(String state, Bitmap bitmap, String id) throws IOException {
        File dir = folder(id);
        File image = new File(dir, "image.png");
        if (!image.isFile()) {
            AtomicFile target = new AtomicFile(image); FileOutputStream output = target.startWrite();
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Bild konnte nicht gespeichert werden.");
                target.finishWrite(output);
            } catch (Exception error) { target.failWrite(output); throw new IOException(error); }
        }
        AtomicFile target = new AtomicFile(new File(dir, "state.json"));
        FileOutputStream out = target.startWrite();
        try { out.write(state.getBytes(StandardCharsets.UTF_8)); target.finishWrite(out); }
        catch (Exception error) { target.failWrite(out); throw new IOException(error); }
    }
    public Loaded load(String id) throws Exception {
        File dir = folder(id);
        Puzzle p;
        try (InputStream in = new AtomicFile(new File(dir, "state.json")).openRead()) {
            p = fromJson(new JSONObject(new String(readLimited(in, 2 * 1024 * 1024), StandardCharsets.UTF_8)));
        }
        Bitmap bitmap;
        try (InputStream in = new FileInputStream(new File(dir, "image.png"))) { bitmap = decode(readLimited(in, MAX_IMAGE_BYTES)); }
        validateBounds(p, bitmap);
        return new Loaded(p, bitmap);
    }
    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>(); File[] dirs = root.listFiles();
        if (dirs != null) for (File dir : dirs) {
            try (InputStream in = new AtomicFile(new File(dir, "state.json")).openRead()) {
                JSONObject json = new JSONObject(new String(readLimited(in, 2 * 1024 * 1024), StandardCharsets.UTF_8));
                Entry e = new Entry(); e.id = dir.getName(); e.name = json.getString("name"); e.updated = json.optLong("updated"); entries.add(e);
            } catch (Exception ignored) { /* An incomplete image import is not a saved puzzle. */ }
        }
        Collections.sort(entries, (a, b) -> Long.compare(b.updated, a.updated));
        return entries;
    }
    public static JSONObject toJson(Puzzle p) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("format", "kreuz2"); json.put("version", 1); json.put("id", p.id); json.put("name", p.name);
        json.put("updated", p.updated); json.put("xs", floats(p.xs)); json.put("ys", floats(p.ys));
        json.put("types", new JSONArray(p.types)); json.put("arrows", new JSONArray(p.arrows));
        json.put("letters", new JSONArray(p.letters)); json.put("selected", p.selected); json.put("direction", p.direction);
        json.put("automatic", p.automatic); json.put("showGrid", p.showGrid); return json;
    }
    private static JSONArray floats(float[] values) throws JSONException { JSONArray a = new JSONArray(); for (float value : values) a.put((double) value); return a; }
    private static float[] floats(JSONArray a) throws JSONException {
        if (a.length() < 2 || a.length() > 101) throw new JSONException("Ungültige Rastergröße.");
        float[] values = new float[a.length()]; for (int i = 0; i < values.length; i++) values[i] = (float) a.getDouble(i); return values;
    }
    public static Puzzle fromJson(JSONObject json) throws Exception {
        if (!"kreuz2".equals(json.optString("format")) || json.getInt("version") != 1) throw new IOException("Unbekanntes Spielstandformat.");
        Puzzle p = new Puzzle(floats(json.getJSONArray("xs")), floats(json.getJSONArray("ys")));
        p.id = json.getString("id"); p.name = json.getString("name"); p.updated = json.optLong("updated");
        if (p.name.length() > 100) p.name = p.name.substring(0, 100);
        JSONArray types = json.getJSONArray("types"), arrows = json.getJSONArray("arrows"), letters = json.getJSONArray("letters");
        if (types.length() != p.types.length || arrows.length() != p.types.length || letters.length() != p.types.length) throw new IOException("Spielstand unvollständig.");
        for (int i = 0; i < p.types.length; i++) {
            p.types[i] = types.getInt(i); p.arrows[i] = arrows.getInt(i); p.letters[i] = letters.getString(i);
            if (p.types[i] < 0 || p.types[i] > 2 || p.arrows[i] < 0 || p.arrows[i] > 15
                    || (!p.letters[i].isEmpty() && !p.letters[i].matches("[\\p{L}\\p{N}]"))) throw new IOException("Ungültige Felddaten.");
        }
        p.selected = json.optInt("selected", -1); p.direction = json.optInt("direction", 0);
        if (!p.valid(p.selected)) p.selected = -1;
        if (p.direction < 0 || p.direction > 3) p.direction = 0;
        p.automatic = json.optBoolean("automatic", true); p.showGrid = json.optBoolean("showGrid", true);
        return p;
    }
    public static void validateBounds(Puzzle p, Bitmap bitmap) throws IOException {
        if (p.xs[p.cols] > bitmap.getWidth() || p.ys[p.rows] > bitmap.getHeight()) throw new IOException("Raster liegt außerhalb des Bildes.");
    }
    public static byte[] readLimited(InputStream in, int limit) throws IOException {
        if (in == null) throw new IOException("Datei lässt sich nicht öffnen.");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) { if (out.size() + n > limit) throw new IOException("Datei ist zu groß (max. " + limit / 1024 / 1024 + " MB)."); out.write(buffer, 0, n); }
        return out.toByteArray();
    }
    public static Bitmap decode(byte[] bytes) throws IOException {
        BitmapFactory.Options bounds = imageBounds(bytes);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Keine unterstützte Bilddatei.");
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2400) options.inSampleSize *= 2;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Bild konnte nicht geladen werden.");
        return bitmap;
    }
    public static BitmapFactory.Options imageBounds(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds); return bounds;
    }
    public static byte[] export(Puzzle p, Bitmap bitmap) throws Exception {
        return export(p, bitmap, null);
    }
    public static byte[] export(Puzzle p, Bitmap bitmap, File original) throws Exception {
        JSONObject json = toJson(p); ByteArrayOutputStream image = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, image)) throw new IOException("Bildexport fehlgeschlagen.");
        json.put("image", Base64.encodeToString(image.toByteArray(), Base64.NO_WRAP));
        if (original != null) {
            try (InputStream in = new FileInputStream(original)) {
                json.put("originalImage", Base64.encodeToString(readLimited(in, MAX_IMAGE_BYTES), Base64.NO_WRAP));
            }
        }
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static Loaded importDocument(byte[] bytes) throws Exception {
        JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8)); Puzzle p = fromJson(json);
        byte[] image = Base64.decode(json.getString("image"), Base64.DEFAULT); Bitmap bitmap = decode(image);
        validateBounds(p, bitmap);
        // Imported copies must not overwrite another local puzzle's image/state.
        p.id = java.util.UUID.randomUUID().toString();
        byte[] original = null;
        if (json.has("originalImage")) {
            String encoded = json.getString("originalImage");
            if (encoded.length() > (MAX_IMAGE_BYTES / 3L + 1) * 4) throw new IOException("Originalbild im Spielstand ist zu groß.");
            original = Base64.decode(encoded, Base64.DEFAULT);
            BitmapFactory.Options bounds = imageBounds(original);
            if (original.length > MAX_IMAGE_BYTES || bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Ungültiges Originalbild im Spielstand.");
            double ratio = (bounds.outWidth / (double) bounds.outHeight) / (bitmap.getWidth() / (double) bitmap.getHeight());
            if (Math.abs(ratio - 1) > .02) throw new IOException("Originalbild und Vorschaubild passen nicht zusammen.");
        }
        return new Loaded(p, bitmap, original);
    }
}
