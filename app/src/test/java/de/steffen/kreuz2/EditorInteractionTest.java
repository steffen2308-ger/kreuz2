package de.steffen.kreuz2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class EditorInteractionTest {
    @Test public void compactControlsLeaveMostOfTheScreenForThePuzzle() throws Exception {
        ActivityController<MainActivity> c = editor();
        try {
            android.view.ViewGroup content = c.get().findViewById(android.R.id.content);
            android.view.View root = content.getChildAt(0);
            float density = c.get().getResources().getDisplayMetrics().density;
            int width = Math.round(360 * density), height = Math.round(640 * density);
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
            PuzzleView board = (PuzzleView) field(c.get(), "board");
            assertTrue("Puzzle area should use at least 80% of the screen before system insets/keyboard", board.getHeight() >= height * .80);
            DirectionIndicator arrow = (DirectionIndicator) field(c.get(), "directionIndicator");
            assertTrue("Direction arrow must stay clearly visible", arrow.getHeight() >= 44 * density);
        } finally { c.pause().stop().destroy(); }
    }
    private Object field(MainActivity activity, String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
    }
    private Puzzle model(MainActivity activity) throws Exception { return (Puzzle) field(activity, "puzzle"); }
    private LetterInput input(MainActivity activity) throws Exception { return (LetterInput) field(activity, "keyboard"); }
    private ActivityController<MainActivity> editor() throws Exception {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        Puzzle p = new Puzzle(Puzzle.evenly(0, 200, 5), Puzzle.evenly(0, 120, 3)); p.select(0, false);
        Bitmap image = Bitmap.createBitmap(201, 121, Bitmap.Config.ARGB_8888);
        Method show = MainActivity.class.getDeclaredMethod("showPuzzle", Puzzle.class, Bitmap.class, boolean.class); show.setAccessible(true);
        show.invoke(controller.get(), p, image, true); return controller;
    }
    @Test public void eachComposingLetterAppearsAndAdvancesWithoutEnter() throws Exception {
        ActivityController<MainActivity> c = editor();
        try {
            MainActivity a = c.get(); InputConnection connection = input(a).onCreateInputConnection(new EditorInfo());
            connection.setComposingText("h", 1); assertEquals("H", model(a).letters[0]); assertEquals(1, model(a).selected);
            connection.setComposingText("ha", 1); assertEquals("A", model(a).letters[1]); assertEquals(2, model(a).selected);
            connection.commitText("haus", 1); connection.finishComposingText();
            assertArrayEquals(new String[] {"H", "A", "U", "S", ""}, java.util.Arrays.copyOf(model(a).letters, 5));
            assertEquals(4, model(a).filled()); assertEquals(4, model(a).selected);
        } finally { c.pause().stop().destroy(); }
    }
    @Test public void changingAProvisionalLetterToAnUmlautReplacesItsCell() throws Exception {
        ActivityController<MainActivity> c = editor();
        try {
            MainActivity a = c.get(); InputConnection connection = input(a).onCreateInputConnection(new EditorInfo());
            connection.setComposingText("a", 1); connection.setComposingText("ä", 1); connection.commitText("ä", 1);
            assertEquals("Ä", model(a).letters[0]); assertEquals(1, model(a).filled()); assertEquals(1, model(a).selected);
            connection.commitText("b", 1); assertEquals("B", model(a).letters[1]); assertEquals(2, model(a).filled());
        } finally { c.pause().stop().destroy(); }
    }
    @Test public void lateCommitCannotWriteIntoANewlySelectedCell() throws Exception {
        ActivityController<MainActivity> c = editor();
        try {
            MainActivity a = c.get(); LetterInput input = input(a); InputConnection old = input.onCreateInputConnection(new EditorInfo());
            old.setComposingText("a", 1); input.resetInput(); model(a).select(4, false);
            assertFalse(old.commitText("a", 1)); assertEquals("", model(a).letters[4]);
            input.onCreateInputConnection(new EditorInfo()).commitText("b", 1); assertEquals("B", model(a).letters[4]);
        } finally { c.pause().stop().destroy(); }
    }
    private void tap(PuzzleView view, long time, float x, float y) {
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, x, y, 0);
        view.onTouchEvent(down); view.onTouchEvent(up); down.recycle(); up.recycle();
    }
    @Test public void rapidSecondTapTogglesDirectionExactlyOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get(); PuzzleView view = new PuzzleView(activity);
        Puzzle p = new Puzzle(new float[] {0, 40, 80}, new float[] {0, 40, 80});
        view.layout(0, 0, 200, 200); view.setPuzzle(p, Bitmap.createBitmap(81, 81, Bitmap.Config.ARGB_8888), true);
        long time = SystemClock.uptimeMillis(); tap(view, time, 50, 50); assertEquals(0, p.selected); assertEquals(Puzzle.RIGHT, p.direction);
        tap(view, time + 80, 50, 50); assertEquals(0, p.selected); assertEquals(Puzzle.DOWN, p.direction);
        tap(view, time + 800, 50, 50); assertEquals(Puzzle.RIGHT, p.direction);
    }
    @Test public void secondTapKeepsSameCellWhenKeyboardResizesTheImage() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get(); PuzzleView view = new PuzzleView(activity);
        Puzzle p = new Puzzle(new float[] {0, 40, 80}, new float[] {0, 40, 80});
        view.layout(0, 0, 200, 200); view.setPuzzle(p, Bitmap.createBitmap(81, 81, Bitmap.Config.ARGB_8888), true);
        long time = SystemClock.uptimeMillis(); tap(view, time, 90, 90); assertEquals(0, p.selected);
        view.layout(0, 0, 200, 120); tap(view, time + 80, 90, 90);
        assertEquals(0, p.selected); assertEquals(Puzzle.DOWN, p.direction);
    }
    @Test public void largeArrowUpdatesAfterDirectionButtonIsPressed() throws Exception {
        ActivityController<MainActivity> c = editor();
        try {
            DirectionIndicator arrow = (DirectionIndicator) field(c.get(), "directionIndicator");
            assertTrue(arrow.getContentDescription().toString().contains("nach rechts"));
            arrow.performClick(); assertEquals(Puzzle.DOWN, model(c.get()).direction);
            assertTrue(arrow.getContentDescription().toString().contains("nach unten"));
        } finally { c.pause().stop().destroy(); }
    }
}
