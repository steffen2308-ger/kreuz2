package de.steffen.kreuz2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageFidelityTest {
    private byte[] original() {
        Bitmap image = Bitmap.createBitmap(3600, 3000, Bitmap.Config.ARGB_8888); image.eraseColor(Color.WHITE);
        // One-pixel strokes disappear when this 3600-pixel image is reduced to its preview.
        for (int y = 1270; y < 1390; y++) for (int x = 1720; x < 1840; x++) image.setPixel(x, y, x % 2 == 0 ? Color.BLACK : Color.WHITE);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        image.recycle(); return out.toByteArray();
    }
    private Puzzle puzzle(Bitmap preview) {
        return new Puzzle(Puzzle.evenly(0, preview.getWidth() - 1, 2), Puzzle.evenly(0, preview.getHeight() - 1, 2));
    }
    private void stripes(File file) throws Exception {
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(file.getAbsolutePath(), false);
        try {
            assertEquals(3600, decoder.getWidth()); assertEquals(3000, decoder.getHeight());
            Bitmap tile = decoder.decodeRegion(new Rect(1720, 1270, 1840, 1390), new BitmapFactory.Options());
            assertEquals(Color.BLACK, tile.getPixel(0, 0)); assertEquals(Color.WHITE, tile.getPixel(1, 0));
            assertEquals(Color.BLACK, tile.getPixel(2, 0)); tile.recycle();
        } finally { decoder.recycle(); }
    }
    @Test public void originalFileKeepsEveryPixelEvenWhenPreviewIsSmaller() throws Exception {
        byte[] bytes = original(); Bitmap preview = PuzzleStore.decode(bytes);
        assertEquals(1800, preview.getWidth()); assertEquals(1500, preview.getHeight());
        Puzzle p = puzzle(preview); p.letters[3] = "Ö";
        PuzzleStore store = new PuzzleStore(RuntimeEnvironment.getApplication());
        store.save(PuzzleStore.toJson(p).toString(), preview, p.id);
        assertNull(store.originalFile(p.id)); // Existing games are still loadable without an original.
        store.saveOriginal(p.id, bytes);
        try (FileInputStream in = new FileInputStream(store.originalFile(p.id))) {
            assertArrayEquals(bytes, PuzzleStore.readLimited(in, PuzzleStore.MAX_IMAGE_BYTES));
        }
        PuzzleStore.Loaded restored = store.load(p.id);
        assertEquals("Ö", restored.puzzle.letters[3]); assertArrayEquals(p.xs, restored.puzzle.xs, 0);
        stripes(store.originalFile(p.id)); preview.recycle(); restored.bitmap.recycle();
    }
    @Test public void portableExportAndImportKeepOriginalSourcePixels() throws Exception {
        byte[] bytes = original(); Bitmap preview = PuzzleStore.decode(bytes); Puzzle p = puzzle(preview);
        PuzzleStore store = new PuzzleStore(RuntimeEnvironment.getApplication()); store.saveOriginal(p.id, bytes);
        PuzzleStore.Loaded copy = PuzzleStore.importDocument(PuzzleStore.export(p, preview, store.originalFile(p.id)));
        assertArrayEquals(bytes, copy.original); assertEquals(1800, copy.bitmap.getWidth());
        store.saveOriginal(copy.puzzle.id, copy.original); stripes(store.originalFile(copy.puzzle.id));
        preview.recycle(); copy.bitmap.recycle();
    }
    private void set(PuzzleView view, String name, float value) throws Exception {
        Field field = PuzzleView.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(view, value);
    }
    @Test public void zoomedViewActuallyDrawsOriginalOnePixelStrokes() throws Exception {
        byte[] bytes = original(); Bitmap preview = PuzzleStore.decode(bytes); Puzzle p = puzzle(preview); p.showGrid = false;
        PuzzleStore store = new PuzzleStore(RuntimeEnvironment.getApplication()); store.saveOriginal(p.id, bytes);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get(); PuzzleView view = new PuzzleView(activity);
        view.layout(0, 0, 256, 256); view.setPuzzle(p, preview, true);
        view.setOriginalImage(store.originalFile(p.id));
        set(view, "scale", 2); set(view, "ox", -1720); set(view, "oy", -1270);
        Bitmap output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(output);
        try {
            boolean sharp = false;
            for (int i = 0; i < 300; i++) {
                Shadows.shadowOf(Looper.getMainLooper()).idle(); view.draw(canvas);
                if (output.getPixel(0, 0) == Color.BLACK && output.getPixel(1, 0) == Color.WHITE && output.getPixel(2, 0) == Color.BLACK) { sharp = true; break; }
                Thread.sleep(10);
            }
            assertTrue("Native detail tiles must restore alternating original one-pixel lines", sharp);
        } finally { view.releaseImages(); output.recycle(); preview.recycle(); }
    }
    @Test public void sourceCoordinatesAndTileBudgetStayCorrectWhileZooming() {
        assertEquals(new Rect(1720, 1270, 1976, 1526), OriginalImageTiles.visibleSource(256, 256, 2, -1720, -1270, 1800, 1500, 3600, 3000));
        assertEquals(1, OriginalImageTiles.sampleSize(1));
        assertEquals(4, OriginalImageTiles.sampleSize(.25f));
        int sample = OriginalImageTiles.boundedSample(new Rect(0, 0, 5000, 5000), .25f, 8 * 1024 * 1024);
        int tiles = (int) Math.ceil(5000.0 / (512 * sample));
        assertTrue((long) tiles * tiles * 512 * 512 * 4 <= 8 * 1024 * 1024);
    }
}
