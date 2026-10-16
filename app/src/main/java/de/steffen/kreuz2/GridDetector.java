package de.steffen.kreuz2;


/** Offline line/ink analysis for upright, rectangular crossword screenshots. No OCR service. */
public final class GridDetector {
    private final int width, height;
    private final boolean[] ink;
    private final boolean[] lineInk;
    public GridDetector(int[] pixels, int width, int height) {
        this.width = width; this.height = height;
        ink = new boolean[pixels.length];
        lineInk = new boolean[pixels.length];
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i], alpha = c >>> 24;
            int luminance = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100;
            gray[i] = (luminance * alpha + 255 * (255 - alpha)) / 255;
            // Keep light clue-cell shading out of text/black-cell classification.
            ink[i] = gray[i] < 155;
        }
        // Local paper brightness keeps pale/colored grid lines visible on uneven backgrounds.
        int tile = 48;
        for (int top = 0; top < height; top += tile) for (int left = 0; left < width; left += tile) {
            int[] histogram = new int[256]; int count = 0;
            for (int y = top; y < Math.min(height, top + tile); y++) for (int x = left; x < Math.min(width, left + tile); x++) {
                histogram[gray[y * width + x]]++; count++;
            }
            int paper = 255, sum = 0;
            for (int v = 0; v < 256; v++) { sum += histogram[v]; if (sum >= count * .85) { paper = v; break; } }
            int threshold = Math.min(245, paper - 12);
            for (int y = top; y < Math.min(height, top + tile); y++) for (int x = left; x < Math.min(width, left + tile); x++) {
                int at = y * width + x; lineInk[at] = gray[at] < 96 || gray[at] <= threshold;
            }
        }
    }
    private boolean dark(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height && ink[y * width + x]; }

    public Puzzle detect() {
        Puzzle result = new GridLocator(lineInk, width, height).locate();
        if (result != null) classify(result);
        return result;
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
            for (int edgeOffset = radius + 2; edgeOffset <= Math.min(side / 3, radius + 9); edgeOffset++) {
                for (int along = Math.round((horizontal ? t : l) + side * .2f);
                     along < (horizontal ? b : r) - side * .16f; along++) {
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
