package de.steffen.kreuz2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class AndroidEditorTest {
    private Puzzle puzzle() {
        Puzzle p = new Puzzle(new float[] {0, 40, 80}, new float[] {0, 40, 80});
        p.name = "Ärzte & Wörter"; p.letters[0] = "Ü"; p.types[1] = Puzzle.CLUE; p.arrows[1] = 2;
        p.selected = 2; p.direction = Puzzle.DOWN; p.automatic = false; p.showGrid = false;
        return p;
    }
    private Bitmap image() { Bitmap b = Bitmap.createBitmap(81, 81, Bitmap.Config.ARGB_8888); b.eraseColor(Color.WHITE); return b; }
    private void same(Puzzle a, Puzzle b) {
        assertEquals(a.name, b.name); assertArrayEquals(a.xs, b.xs, .001f); assertArrayEquals(a.ys, b.ys, .001f);
        assertArrayEquals(a.letters, b.letters); assertArrayEquals(a.types, b.types); assertArrayEquals(a.arrows, b.arrows);
        assertEquals(a.selected, b.selected); assertEquals(a.direction, b.direction); assertEquals(a.automatic, b.automatic); assertEquals(a.showGrid, b.showGrid);
    }
    @Test public void localSaveAndLoadKeepsImageAndAllEditingData() throws Exception {
        Puzzle p = puzzle(); Bitmap b = image(); PuzzleStore store = new PuzzleStore(RuntimeEnvironment.getApplication());
        store.save(PuzzleStore.toJson(p).toString(), b, p.id);
        PuzzleStore.Loaded loaded = store.load(p.id); same(p, loaded.puzzle); assertEquals(p.id, loaded.puzzle.id);
        assertEquals(81, loaded.bitmap.getWidth()); assertEquals(Color.WHITE, loaded.bitmap.getPixel(10, 10));
        p.letters[2] = "Z"; store.save(PuzzleStore.toJson(p).toString(), b, p.id);
        assertEquals("Z", store.load(p.id).puzzle.letters[2]); assertTrue(store.list().size() >= 1);
    }
    @Test public void exportImportIsPortableAndDoesNotOverwriteSourceId() throws Exception {
        Puzzle p = puzzle(); PuzzleStore.Loaded copy = PuzzleStore.importDocument(PuzzleStore.export(p, image()));
        same(p, copy.puzzle); assertNotEquals(p.id, copy.puzzle.id); assertEquals(81, copy.bitmap.getHeight());
    }
    @Test(expected = java.io.IOException.class) public void corruptCellArraysAreRejected() throws Exception {
        JSONObject json = PuzzleStore.toJson(puzzle()); json.put("letters", new org.json.JSONArray()); PuzzleStore.fromJson(json);
    }
    @Test public void committedAndComposedKeyboardTextIsDeliveredOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LetterInput input = new LetterInput(activity); activity.setContentView(input); input.requestFocus();
        StringBuilder received = new StringBuilder(); int[] erases = {0};
        input.setListener(new LetterInput.Listener() {
            public void text(String value) { received.append(value); }
            public void erase() { erases[0]++; }
        });
        InputConnection connection = input.onCreateInputConnection(new EditorInfo()); assertNotNull(connection);
        connection.setComposingText("h", 1); connection.setComposingText("ha", 1); assertEquals("", received.toString());
        connection.commitText("haus", 1); connection.finishComposingText(); assertEquals("haus", received.toString());
        connection.commitText("ü", 1); assertEquals("hausü", received.toString()); assertEquals(0, input.length());
        connection.deleteSurroundingText(1, 0); assertEquals(1, erases[0]);
    }
    @Test public void mainScreenCanStartWithoutAPuzzle() {
        org.robolectric.android.controller.ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        assertNotNull(controller.get().getWindow().getDecorView()); controller.pause().stop().destroy();
    }
}
