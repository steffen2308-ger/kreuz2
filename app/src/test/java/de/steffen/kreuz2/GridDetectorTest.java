package de.steffen.kreuz2;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class GridDetectorTest {
    private int w = 640, h = 800;
    private int[] image() { int[] pixels = new int[w * h]; Arrays.fill(pixels, 0xffffffff); return pixels; }
    private void box(int[] p, int x0, int y0, int x1, int y1, int color) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) p[y * w + x] = color;
    }
    private void grid(int[] p, int left, int top, int cols, int rows, int cw, int ch, int color) {
        for (int c = 0; c <= cols; c++) box(p, left + c * cw, top, left + c * cw + 1, top + rows * ch + 1, color);
        for (int r = 0; r <= rows; r++) box(p, left, top + r * ch, left + cols * cw + 1, top + r * ch + 1, color);
    }
    @Test public void detectsScreenshotMarginsAndRectangularCells() {
        int[] p = image(); grid(p, 50, 150, 12, 10, 42, 46, 0xff202020);
        // Header text and phone chrome must not enlarge the grid.
        box(p, 20, 30, 620, 31, 0xff000000);
        Puzzle result = new GridDetector(p, w, h).detect(); assertNotNull(result);
        assertEquals(12, result.cols); assertEquals(10, result.rows); assertEquals(50.5f, result.xs[0], 2f); assertEquals(150.5f, result.ys[0], 2f);
    }
    @Test public void detectsThinGrayGrid() {
        int[] p = image(); grid(p, 30, 120, 10, 13, 50, 44, 0xff888888);
        Puzzle result = new GridDetector(p, w, h).detect(); assertNotNull(result); assertEquals(10, result.cols); assertEquals(13, result.rows);
    }
    @Test public void blankScreenshotIsNotAFalseGrid() { assertNull(new GridDetector(image(), w, h).detect()); }
    @Test public void blackAndMultilineClueAreBlockedButCornerNumberIsOpen() {
        int[] p = image(); grid(p, 40, 100, 10, 10, 50, 50, 0xff000000);
        box(p, 91, 101, 138, 148, 0xff000000);
        box(p, 149, 112, 178, 115, 0xff000000); box(p, 149, 125, 181, 128, 0xff000000);
        box(p, 194, 104, 197, 108, 0xff000000);
        Puzzle result = new GridDetector(p, w, h).detect(); assertNotNull(result);
        assertEquals(Puzzle.BLOCK, result.types[1]); assertEquals(Puzzle.CLUE, result.types[2]); assertEquals(Puzzle.OPEN, result.types[3]);
    }
    @Test public void canInferOneMissingInternalLine() {
        int[] p = image(); grid(p, 40, 100, 10, 10, 50, 50, 0xff000000);
        box(p, 290, 99, 291, 602, 0xffffffff);
        Puzzle result = new GridDetector(p, w, h).detect(); assertNotNull(result); assertEquals(10, result.cols);
    }
    @Test public void recognizesRightArrowAtClueEdge() {
        int[] p = image(); grid(p, 40, 100, 10, 10, 50, 50, 0xff000000);
        int cx = 80, cy = 126, radius = 4;
        for (int u = -radius; u <= radius; u++) for (int v = -radius; v <= radius; v++) {
            if ((u >= 0 && Math.abs(v) <= radius - u) || (u < 0 && Math.abs(v) <= 1)) p[(cy + v) * w + cx + u] = 0xff000000;
        }
        Puzzle result = new GridDetector(p, w, h).detect(); assertNotNull(result);
        assertEquals(Puzzle.CLUE, result.types[0]); assertTrue((result.arrows[0] & 1) != 0);
    }
    @Test public void blankCellsDoNotInventArrows() {
        int[] p = image(); grid(p, 40, 100, 10, 10, 50, 50, 0xff000000);
        Puzzle result = new GridDetector(p, w, h).detect(); assertNotNull(result);
        for (int arrow : result.arrows) assertEquals(0, arrow);
    }
}
