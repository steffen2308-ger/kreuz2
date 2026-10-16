package de.steffen.kreuz2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClueSearchTest {
    private Puzzle grid() { return new Puzzle(new float[] {0, 40, 80}, new float[] {0, 40, 80}); }
    private Bitmap image() { return Bitmap.createBitmap(81, 81, Bitmap.Config.ARGB_8888); }
    private PuzzleView view(Activity activity, int[] holds, int[] selections) {
        PuzzleView view = new PuzzleView(activity); activity.setContentView(view);
        // Finish the Activity's first layout before sending timed touch events.
        idle(100);
        view.layout(0, 0, 200, 200); view.setPuzzle(grid(), image(), true);
        view.setListener(new PuzzleView.Listener() {
            public void selected() { selections[0]++; }
            public void longPress(int cell) { holds[0]++; assertEquals(0, cell); }
        });
        return view;
    }
    private void event(PuzzleView view, int action, float x, float y, long down) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        view.onTouchEvent(event); event.recycle();
    }
    private void idle(long ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
    @Test public void holdsForOneSecondAndSearchesOnceBeforeRelease() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        int[] holds = {0}, selections = {0}; PuzzleView view = view(activity, holds, selections);
        long down = SystemClock.uptimeMillis(); event(view, MotionEvent.ACTION_DOWN, 50, 50, down);
        idle(999); assertEquals(0, holds[0]); idle(1); assertEquals(1, holds[0]);
        idle(2500); assertEquals(1, holds[0]); event(view, MotionEvent.ACTION_UP, 50, 50, down);
        assertEquals(0, selections[0]);
    }
    @Test public void releasingEarlyKeepsNormalTapAndDoesNotSearch() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        int[] holds = {0}, selections = {0}; PuzzleView view = view(activity, holds, selections);
        long down = SystemClock.uptimeMillis(); event(view, MotionEvent.ACTION_DOWN, 50, 50, down); idle(700);
        event(view, MotionEvent.ACTION_UP, 50, 50, down); idle(2500);
        assertEquals("Early release must cancel the search", 0, holds[0]);
        assertEquals("Early release must still select the tapped cell", 1, selections[0]);
    }
    @Test public void draggingOrCancellingNeverStartsSearch() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        int[] holds = {0}, selections = {0}; PuzzleView view = view(activity, holds, selections);
        long down = SystemClock.uptimeMillis(); event(view, MotionEvent.ACTION_DOWN, 50, 50, down);
        event(view, MotionEvent.ACTION_MOVE, 120, 50, down); idle(2200); assertEquals(0, holds[0]);
        event(view, MotionEvent.ACTION_CANCEL, 120, 50, down);
        down = SystemClock.uptimeMillis(); event(view, MotionEvent.ACTION_DOWN, 50, 50, down);
        view.cancelPendingHold(); idle(2200); assertEquals(0, holds[0]);
    }
    @Test public void cropRemovesRasterLinesAndEnlargesOnlySelectedCell() {
        Bitmap original = image(); Puzzle p = grid(); android.graphics.Rect crop = ClueSearch.bounds(original, p, 1);
        assertEquals(new android.graphics.Rect(42, 2, 79, 39), crop);
        Bitmap prepared = ClueSearch.prepare(original, p, 1);
        assertTrue(prepared.getWidth() > crop.width()); assertTrue(prepared.getWidth() <= 768);
        assertFalse(original.isRecycled()); prepared.recycle();
    }
    @Test public void searchEncodesQuestionAndKeepsGermanCharacters() {
        String clue = ClueSearch.clean("franzö-\nsischer\nFluss →"); assertEquals("französischer Fluss", clue);
        android.net.Uri uri = ClueSearch.googleUrl(clue + " & See?");
        assertEquals("https", uri.getScheme()); assertEquals("www.google.com", uri.getHost());
        assertEquals("französischer Fluss & See? Kreuzworträtsel Lösung", uri.getQueryParameter("q"));
        assertFalse(ClueSearch.readable("→ 1")); assertFalse(ClueSearch.readable("")); assertTrue(ClueSearch.readable(clue));
    }
    private ActivityController<MainActivity> editor(ClueSearch.Reader reader) throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        Method show = MainActivity.class.getDeclaredMethod("showPuzzle", Puzzle.class, Bitmap.class, boolean.class); show.setAccessible(true);
        Puzzle p = grid(); p.types[0] = Puzzle.CLUE; show.invoke(c.get(), p, image(), true);
        Field field = MainActivity.class.getDeclaredField("clueReader"); field.setAccessible(true); field.set(c.get(), reader);
        return c;
    }
    private void search(MainActivity activity) throws Exception {
        Method search = MainActivity.class.getDeclaredMethod("searchClue", int.class); search.setAccessible(true); search.invoke(activity, 0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void readableClueOpensGoogleWithoutAnExtraConfirmation() throws Exception {
        ActivityController<MainActivity> c = editor((image, puzzle, cell, callback) -> {
            assertEquals(0, cell); callback.success("Hauptstadt von Frankreich");
        });
        try {
            search(c.get()); Intent opened = Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(opened); assertEquals(Intent.ACTION_VIEW, opened.getAction());
            assertEquals("Hauptstadt von Frankreich Kreuzworträtsel Lösung", opened.getData().getQueryParameter("q"));
        } finally { c.pause().stop().destroy(); }
    }
    @Test public void unreadableClueOffersManualQueryInsteadOfSearchingGarbage() throws Exception {
        ActivityController<MainActivity> c = editor((image, puzzle, cell, callback) -> callback.success("→"));
        try {
            search(c.get()); assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertTrue(dialog.isShowing());
            assertEquals("Google suchen", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString()); dialog.dismiss();
        } finally { c.pause().stop().destroy(); }
    }
    @Test public void recognizerFailureKeepsAppUsableAndOffersManualQuery() throws Exception {
        ActivityController<MainActivity> c = editor((image, puzzle, cell, callback) -> callback.failure(new Exception("OCR unavailable")));
        try {
            search(c.get()); assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog(); assertTrue(dialog.isShowing()); dialog.dismiss();
            Field busy = MainActivity.class.getDeclaredField("busy"); busy.setAccessible(true); assertFalse(busy.getBoolean(c.get()));
        } finally { c.pause().stop().destroy(); }
    }
}
