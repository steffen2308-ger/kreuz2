package de.steffen.kreuz2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Independent synthetic screenshots, also runnable without Android or JUnit for local comparison. */
public final class GridDetectionScenarios {
    public static final class Scene {
        final String name;
        final int width, height, left, top, cols, rows;
        final float cw, ch;
        final int[] pixels;
        Scene(String name, int width, int height, int left, int top, int cols, int rows, float cw, float ch) {
            this.name = name; this.width = width; this.height = height; this.left = left; this.top = top;
            this.cols = cols; this.rows = rows; this.cw = cw; this.ch = ch;
            pixels = new int[width * height]; Arrays.fill(pixels, 0xffffffff);
        }
        void rect(int l, int t, int r, int b, int color) {
            for (int y = Math.max(0, t); y <= Math.min(height - 1, b); y++) for (int x = Math.max(0, l); x <= Math.min(width - 1, r); x++) pixels[y * width + x] = color;
        }
        void grid(int color) {
            for (int c = 0; c <= cols; c++) rect(Math.round(left + c * cw), top, Math.round(left + c * cw) + 1, Math.round(top + rows * ch) + 1, color);
            for (int r = 0; r <= rows; r++) rect(left, Math.round(top + r * ch), Math.round(left + cols * cw) + 1, Math.round(top + r * ch) + 1, color);
        }
        void eraseColumn(int col) { rect(Math.round(left + col * cw), top, Math.round(left + col * cw) + 1, Math.round(top + rows * ch) + 1, 0xffffffff); }
        void eraseRow(int row) { rect(left, Math.round(top + row * ch), Math.round(left + cols * cw) + 1, Math.round(top + row * ch) + 1, 0xffffffff); }
    }
    public static List<Scene> scenes() {
        List<Scene> scenes = new ArrayList<>(); Scene s;
        s = new Scene("pale-gray", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xffe5e5e5); scenes.add(s);
        s = new Scene("colored-faint", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xffcadce6); scenes.add(s);
        s = new Scene("several-missing", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xff222222);
        s.eraseColumn(2); s.eraseColumn(6); s.eraseColumn(10); s.eraseRow(3); s.eraseRow(8); scenes.add(s);
        s = new Scene("two-adjacent-missing", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xff222222); s.eraseColumn(4); s.eraseColumn(5); scenes.add(s);
        s = new Scene("missing-external-borders", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xff222222); s.eraseColumn(0); s.eraseRow(0); scenes.add(s);
        s = new Scene("interrupted-lines", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xff333333);
        for (int c = 0; c <= s.cols; c++) for (int y = s.top + 16; y < s.top + s.rows * s.ch - 5; y += 42) s.rect(s.left + c * 42, y, s.left + c * 42 + 1, y + 6, 0xffffffff);
        scenes.add(s);
        s = new Scene("rectangular-resized", 800, 1000, 61, 137, 15, 18, 38.6f, 35.4f); s.grid(0xff555555); scenes.add(s);
        s = new Scene("small-grid-large-screenshot", 1000, 1400, 360, 560, 10, 10, 24, 24); s.grid(0xff444444);
        s.rect(10, 20, 11, 1380, 0xff000000); s.rect(20, 50, 980, 51, 0xff000000); scenes.add(s);
        s = new Scene("partial-clue-dividers", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xff333333);
        for (int r = 0; r < s.rows; r++) s.rect(s.left, s.top + r * 42 + 21, s.left + 42 * 4, s.top + r * 42 + 22, 0xff333333);
        scenes.add(s);
        s = new Scene("large-black-patch", 800, 1000, 80, 180, 12, 14, 42, 42); s.grid(0xff222222);
        s.rect(s.left + 1, s.top + 1, s.left + 42 * 4 - 1, s.top + 42 * 5 - 1, 0xff222222); scenes.add(s);
        s = new Scene("surrounding-thumbnail-grid", 800, 1100, 120, 330, 12, 14, 42, 42); s.grid(0xff333333);
        for (int c = 0; c <= 5; c++) s.rect(20 + c * 12, 30, 21 + c * 12, 91, 0xff333333);
        for (int r = 0; r <= 5; r++) s.rect(20, 30 + r * 12, 81, 31 + r * 12, 0xff333333);
        scenes.add(s);
        s = new Scene("uneven-paper", 800, 1000, 80, 180, 12, 14, 42, 42);
        for (int y = 0; y < s.height; y++) for (int x = 0; x < s.width; x++) { int gray = 202 + x * 45 / s.width; s.pixels[y * s.width + x] = 0xff000000 | gray * 0x010101; }
        s.grid(0xffa6a6a6); scenes.add(s);
        return scenes;
    }
    public static String mismatch(Scene s, Puzzle p) {
        if (p == null) return "no grid";
        if (s.cols != p.cols || s.rows != p.rows) return p.cols + "x" + p.rows + " instead of " + s.cols + "x" + s.rows;
        if (Math.abs(p.xs[0] - s.left) > 2 || Math.abs(p.ys[0] - s.top) > 2) return "wrong external boundaries";
        for (int i = 0; i <= s.cols; i++) if (Math.abs(p.xs[i] - (s.left + i * s.cw)) > 2.5) return "misaligned column " + i;
        for (int i = 0; i <= s.rows; i++) if (Math.abs(p.ys[i] - (s.top + i * s.ch)) > 2.5) return "misaligned row " + i;
        return null;
    }
    public static List<Scene> negativeScenes() {
        List<Scene> out = new ArrayList<>(); Scene s;
        s = new Scene("uniform-gray", 800, 1000, 0, 0, 0, 0, 0, 0); Arrays.fill(s.pixels, 0xffbcbcbc); out.add(s);
        s = new Scene("horizontal-rules-only", 800, 1000, 0, 0, 0, 0, 0, 0);
        for (int y = 100; y < 900; y += 40) s.rect(50, y, 750, y + 1, 0xff111111); out.add(s);
        s = new Scene("vertical-rules-only", 800, 1000, 0, 0, 0, 0, 0, 0);
        for (int x = 100; x < 700; x += 40) s.rect(x, 50, x + 1, 950, 0xff111111); out.add(s);
        s = new Scene("sparse-noise", 800, 1000, 0, 0, 0, 0, 0, 0); java.util.Random random = new java.util.Random(314);
        for (int i = 0; i < s.pixels.length; i++) if (random.nextInt(20) == 0) s.pixels[i] = 0xff222222; out.add(s);
        s = new Scene("paragraphs-without-grid", 800, 1000, 0, 0, 0, 0, 0, 0);
        for (int y = 100; y < 900; y += 17 + random.nextInt(7)) {
            for (int x = 60; x < 670 - random.nextInt(30); x += 7 + random.nextInt(6)) {
                int ch = 5 + random.nextInt(5); s.rect(x, y, x + 1, y + ch, 0xff333333); s.rect(x, y + ch, x + 3, y + ch + 1, 0xff333333);
            }
        }
        out.add(s); return out;
    }
    public static void main(String[] args) throws Exception {
        int failed = 0;
        for (Scene s : scenes()) {
            long start = System.nanoTime(); Puzzle p;
            if (args.length > 0) {
                Object detector = Class.forName(args[0]).getConstructor(int[].class, int.class, int.class).newInstance(s.pixels, s.width, s.height);
                p = (Puzzle) detector.getClass().getMethod("detect").invoke(detector);
            } else p = new GridDetector(s.pixels, s.width, s.height).detect();
            String problem = mismatch(s, p); if (problem != null) failed++;
            System.out.println(s.name + ": " + (problem == null ? "OK" : problem) + " (" + (System.nanoTime() - start) / 1000000 + " ms)");
            if (problem != null && p != null) System.out.println("x=" + Arrays.toString(p.xs) + " y=" + Arrays.toString(p.ys));
        }
        if (args.length == 0) for (Scene s : negativeScenes()) {
            Puzzle p = new GridDetector(s.pixels, s.width, s.height).detect();
            System.out.println(s.name + ": " + (p == null ? "OK (no grid)" : "FALSE GRID")); if (p != null) failed++;
        }
        if (failed > 0) throw new AssertionError(failed + " detection scenarios failed");
    }
}
