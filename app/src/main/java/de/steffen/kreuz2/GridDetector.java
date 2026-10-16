package de.steffen.kreuz2;

import java.util.ArrayList;
import java.util.List;

/** Offline line/ink analysis for upright, rectangular crossword screenshots. No OCR service. */
public final class GridDetector {
    private final int width, height;
    private final boolean[] ink;
    public GridDetector(int[] pixels, int width, int height) {
        this.width = width; this.height = height;
        ink = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i], a = c >>> 24;
            int gray = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100;
            ink[i] = (gray * a + 255 * (255 - a)) / 255 < 155;
        }
    }
    private boolean dark(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height && ink[y * width + x]; }

    public Puzzle detect() {
        float[] xs = axis(true), ys = axis(false);
        if (xs == null || ys == null) return null;
        // Use the intersection of both sets of lines to reject surrounding UI/text.
        if ((xs[xs.length - 1] - xs[0]) * (ys[ys.length - 1] - ys[0]) < width * height * .035f) return null;
        Puzzle p = new Puzzle(xs, ys);
        classify(p);
        return p;
    }

    private float[] axis(boolean vertical) {
        int across = vertical ? width : height, along = vertical ? height : width;
        int[] scores = new int[across];
        int minimumRun = Math.max(24, along / 12);
        for (int pos = 0; pos < across; pos++) {
            int start = -1, last = -1;
            for (int q = 0; q <= along; q++) {
                boolean on = q < along && (vertical ? dark(pos, q) : dark(q, pos));
                if (on) { if (start < 0) start = q; last = q; }
                if (start >= 0 && (!on && (q - last > 2 || q == along))) {
                    if (last - start + 1 >= minimumRun) scores[pos] += last - start + 1;
                    start = -1;
                }
            }
        }
        int max = 0;
        for (int score : scores) max = Math.max(max, score);
        if (max < minimumRun) return null;
        List<Float> peaks = new ArrayList<>();
        int threshold = Math.max(minimumRun, (int) (max * .26));
        for (int i = 0; i < across;) {
            if (scores[i] < threshold) { i++; continue; }
            int first = i; double sum = 0, weight = 0;
            while (i < across && scores[i] >= threshold) { sum += (double) i * scores[i]; weight += scores[i]; i++; }
            // Filled black squares are broad plateaus, not lines. Keep their edges as candidates.
            if (i - first > Math.max(8, across / 100)) {
                peaks.add((float) first); peaks.add((float) (i - 1));
            } else peaks.add((float) (sum / weight));
        }
        if (peaks.size() < 4 || peaks.size() > 250) return null;
        float[] best = null; double bestScore = 0;
        for (int i = 0; i < peaks.size() - 2; i++) {
            for (int j = i + 1; j < Math.min(i + 5, peaks.size()); j++) {
                float pitch = peaks.get(j) - peaks.get(i);
                if (pitch < 10 || pitch > across / 2f) continue;
                List<Float> lines = new ArrayList<>(); lines.add(peaks.get(i));
                float predicted = peaks.get(i) + pitch;
                int misses = 0, matched = 1; double error = 0;
                while (predicted < across && lines.size() <= 101) {
                    float closest = -1, distance = pitch * .10f;
                    for (float peak : peaks) {
                        float delta = Math.abs(peak - predicted);
                        if (delta < distance) { distance = delta; closest = peak; }
                    }
                    if (closest < 0) {
                        if (++misses > 1) break;
                        lines.add(predicted); predicted += pitch;
                    } else {
                        matched++; error += distance / pitch;
                        lines.add(closest);
                        // Re-estimate pitch over the whole run; avoid accumulated rounding drift.
                        pitch = (closest - lines.get(0)) / (lines.size() - 1);
                        predicted = closest + pitch;
                    }
                }
                // Trim extrapolated lines at the far edge; never guess the external boundary.
                while (lines.size() > 1 && !nearPeak(lines.get(lines.size() - 1), peaks, pitch * .1f)) lines.remove(lines.size() - 1);
                if (matched < 4 || lines.size() < 4 || lines.size() > 101) continue;
                double score = matched * (double) (lines.get(lines.size() - 1) - lines.get(0)) / across - error;
                if (score > bestScore) {
                    bestScore = score; best = new float[lines.size()];
                    for (int k = 0; k < best.length; k++) best[k] = lines.get(k);
                }
            }
        }
        return best;
    }
    private boolean nearPeak(float p, List<Float> peaks, float tolerance) {
        for (float peak : peaks) if (Math.abs(peak - p) <= tolerance) return true;
        return false;
    }

    public void classify(Puzzle p) {
        for (int row = 0; row < p.rows; row++) for (int col = 0; col < p.cols; col++) {
            int cell = row * p.cols + col;
            int cw = Math.round(p.xs[col + 1] - p.xs[col]), ch = Math.round(p.ys[row + 1] - p.ys[row]);
            int mx = Math.max(3, cw / 8), my = Math.max(3, ch / 8);
            int x0 = Math.round(p.xs[col]) + mx, x1 = Math.round(p.xs[col + 1]) - mx;
            int y0 = Math.round(p.ys[row]) + my, y1 = Math.round(p.ys[row + 1]) - my;
            int count = 0, bands = 0; boolean previous = false;
            for (int y = y0; y < y1; y++) {
                int rowInk = 0;
                for (int x = x0; x < x1; x++) if (dark(x, y)) rowInk++;
                count += rowInk;
                boolean occupied = rowInk >= Math.max(2, (x1 - x0) / 10);
                if (occupied && !previous) bands++;
                previous = occupied;
            }
            double density = count / (double) Math.max(1, (x1 - x0) * (y1 - y0));
            if (density > .57) p.types[cell] = Puzzle.BLOCK;
            else if (bands >= 2 && density > .045) p.types[cell] = Puzzle.CLUE;
            else p.types[cell] = Puzzle.OPEN;
            // Standard small right/down arrows near a clue's exit edge.
            int mask = 0;
            for (int dir = 0; dir < 4; dir++) if (arrow(p, col, row, dir)) mask |= 1 << dir;
            p.arrows[cell] = mask;
            if (mask != 0 && p.types[cell] != Puzzle.BLOCK) p.types[cell] = Puzzle.CLUE;
        }
    }

    private boolean arrow(Puzzle p, int col, int row, int dir) {
        float l = p.xs[col], r = p.xs[col + 1], t = p.ys[row], b = p.ys[row + 1];
        boolean horizontal = dir % 2 == 0;
        int side = Math.round(Math.min(r - l, b - t));
        // A filled triangular arrowhead with a stem, tested in several sizes/positions.
        for (int radius = 3; radius <= Math.min(10, side / 5); radius++) {
            for (int edgeOffset = radius + 2; edgeOffset <= Math.min(side / 3, radius + 9); edgeOffset += 2) {
                for (int along = Math.round((horizontal ? t : l) + side * .2f);
                     along < (horizontal ? b : r) - side * .16f; along += 2) {
                    int cx = horizontal ? Math.round(dir == Puzzle.RIGHT ? r - edgeOffset : l + edgeOffset) : along;
                    int cy = horizontal ? along : Math.round(dir == Puzzle.DOWN ? b - edgeOffset : t + edgeOffset);
                    if (!dark(cx, cy) || !dark(cx + (horizontal ? (dir == Puzzle.RIGHT ? radius - 1 : 1 - radius) : 0),
                            cy + (horizontal ? 0 : (dir == Puzzle.DOWN ? radius - 1 : 1 - radius)))) continue;
                    int agree = 0, samples = 0, foreground = 0, hits = 0;
                    for (int u = -radius - 1; u <= radius + 1; u++) for (int v = -radius - 1; v <= radius + 1; v++) {
                        boolean expected = (u >= 0 && u <= radius && Math.abs(v) <= radius - u)
                                || (u >= -radius && u < 0 && Math.abs(v) <= Math.max(0, radius / 4));
                        int xx = cx + (horizontal ? (dir == Puzzle.RIGHT ? u : -u) : v);
                        int yy = cy + (horizontal ? v : (dir == Puzzle.DOWN ? u : -u));
                        boolean actual = dark(xx, yy);
                        if (expected) { foreground++; if (actual) hits++; }
                        if (actual == expected) agree++;
                        samples++;
                    }
                    if (agree / (double) samples > .90 && hits / (double) foreground > .86) return true;
                }
            }
        }
        return false;
    }
}
