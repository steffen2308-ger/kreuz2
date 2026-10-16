package de.steffen.kreuz2;

import java.util.Arrays;
import java.util.UUID;

/** Image coordinates are independent of the current screen/zoom. */
public final class Puzzle {
    public static final int OPEN = 0, BLOCK = 1, CLUE = 2;
    public static final int RIGHT = 0, DOWN = 1, LEFT = 2, UP = 3;
    public String id = UUID.randomUUID().toString();
    public String name = "Mein Kreuzworträtsel";
    public long updated;
    public final float[] xs, ys;
    public final int cols, rows;
    public final int[] types, arrows;
    public final String[] letters;
    public int selected = -1, direction = RIGHT;
    public boolean automatic = true, showGrid = true;

    public Puzzle(float[] xs, float[] ys) {
        validateLines(xs);
        validateLines(ys);
        this.xs = xs.clone(); this.ys = ys.clone();
        cols = xs.length - 1; rows = ys.length - 1;
        types = new int[cols * rows]; arrows = new int[types.length];
        letters = new String[types.length]; Arrays.fill(letters, "");
    }

    private static void validateLines(float[] lines) {
        if (lines.length < 2 || lines.length > 101) throw new IllegalArgumentException("1 bis 100 Zeilen/Spalten erforderlich.");
        for (int i = 0; i < lines.length; i++) {
            if (Float.isNaN(lines[i]) || Float.isInfinite(lines[i]) || lines[i] < 0
                    || (i > 0 && lines[i] - lines[i - 1] < 2)) {
                throw new IllegalArgumentException("Ungültige Rastergrenzen.");
            }
        }
    }

    public static float[] evenly(float start, float end, int count) {
        float[] out = new float[count + 1];
        for (int i = 0; i <= count; i++) out[i] = start + (end - start) * i / count;
        return out;
    }

    public boolean open(int cell) { return cell >= 0 && cell < types.length && types[cell] == OPEN; }
    public int neighbor(int cell, int dir) {
        if (cell < 0 || cell >= types.length) return -1;
        int r = cell / cols, c = cell % cols;
        if (dir == RIGHT) c++; else if (dir == DOWN) r++; else if (dir == LEFT) c--; else r--;
        return r < 0 || c < 0 || r >= rows || c >= cols ? -1 : r * cols + c;
    }
    public static int opposite(int dir) { return (dir + 2) % 4; }
    public int run(int cell, int dir) {
        int n = 0;
        for (int next = neighbor(cell, dir); open(next); next = neighbor(next, dir)) n++;
        return n;
    }
    public int preferred(int cell) {
        // A clue's arrow is stronger evidence than the length of a blank run.
        for (int dir = 0; dir < 4; dir++) {
            int source = neighbor(cell, opposite(dir));
            if (source >= 0 && types[source] == CLUE && (arrows[source] & (1 << dir)) != 0) return dir;
        }
        if (run(cell, direction) > 0) return direction;
        int best = RIGHT, length = -1;
        for (int dir : new int[] { RIGHT, DOWN }) {
            int n = run(cell, dir);
            if (n > length) { length = n; best = dir; }
        }
        return best;
    }
    public void select(int cell, boolean toggle) {
        if (cell < 0 || cell >= types.length) return;
        if (types[cell] == CLUE) {
            for (int dir = 0; dir < 4; dir++) {
                int target = neighbor(cell, dir);
                if ((arrows[cell] & (1 << dir)) != 0 && open(target)) {
                    selected = target; direction = dir; return;
                }
            }
        }
        if (!open(cell)) return;
        if (toggle && selected == cell) direction = direction % 2 == 0 ? DOWN : RIGHT;
        else if (automatic) direction = preferred(cell);
        selected = cell;
    }
    public void advance() {
        int next = neighbor(selected, direction);
        if (open(next)) { selected = next; return; }
        if (!automatic) return;
        // At a horizontal end, continue down if possible (and vice versa).
        int turn = direction % 2 == 0 ? DOWN : RIGHT;
        next = neighbor(selected, turn);
        if (open(next)) { direction = turn; selected = next; return; }
        for (int i = 1; i < types.length; i++) {
            next = (selected + i) % types.length;
            if (open(next) && letters[next].isEmpty()) { select(next, false); return; }
        }
    }
    public void put(String letter) {
        if (!open(selected)) return;
        letters[selected] = letter;
        advance();
    }
    public void backspace() {
        if (!open(selected)) return;
        if (letters[selected].isEmpty()) {
            int prev = neighbor(selected, opposite(direction));
            if (open(prev)) selected = prev;
        }
        letters[selected] = "";
    }
    public int filled() {
        int n = 0;
        for (int i = 0; i < types.length; i++) if (open(i) && !letters[i].isEmpty()) n++;
        return n;
    }
}
