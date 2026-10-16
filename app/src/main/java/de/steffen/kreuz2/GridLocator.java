package de.steffen.kreuz2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds a mutually supported pair of periodic line families, then recovers missing borders. */
final class GridLocator {
    private final boolean[] ink;
    private final int width, height;
    private static final class Peak {
        final float position, strength;
        Peak(float position, float strength) { this.position = position; this.strength = strength; }
    }
    private static final class Axis {
        final float[] lines;
        final double score;
        Axis(float[] lines, double score) { this.lines = lines; this.score = score; }
    }
    GridLocator(boolean[] ink, int width, int height) { this.ink = ink; this.width = width; this.height = height; }
    private boolean dark(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height && ink[y * width + x]; }
    Puzzle locate() {
        List<Axis> vertical = candidates(true), horizontal = candidates(false);
        Puzzle best = null; double bestScore = 0;
        for (Axis x : vertical) for (Axis y : horizontal) {
            float[] xs = trim(x.lines, y.lines[0], y.lines[y.lines.length - 1], true);
            if (xs.length < 4) continue;
            float[] ys = trim(y.lines, xs[0], xs[xs.length - 1], false);
            if (ys.length < 4) continue;
            xs = trim(xs, ys[0], ys[ys.length - 1], true);
            if (xs.length < 4) continue;
            double area = (xs[xs.length - 1] - xs[0]) * (ys[ys.length - 1] - ys[0]) / (width * (double) height);
            if (area < .018) continue;
            double vx = support(xs, ys[0], ys[ys.length - 1], true), hy = support(ys, xs[0], xs[xs.length - 1], false);
            if (vx < .40 || hy < .40) continue;
            double score = Math.sqrt(xs.length * (double) ys.length * area) * vx * hy;
            if (score > bestScore) { bestScore = score; best = new Puzzle(xs, ys); }
        }
        if (best == null) return null;
        float[] xs = extend(best.xs, best.ys, true), ys = extend(best.ys, xs, false);
        xs = extend(xs, ys, true);
        return new Puzzle(xs, ys);
    }
    private List<Peak> peaks(boolean vertical) {
        int across = vertical ? width : height, along = vertical ? height : width;
        double[] scores = new double[across];
        int minimumRun = Math.max(6, along / 100), fullRun = Math.max(28, along / 9);
        for (int pos = 0; pos < across; pos++) {
            int first = -1, last = -1;
            for (int q = 0; q <= along + 3; q++) {
                boolean on = q < along && (vertical ? dark(pos, q) : dark(q, pos));
                if (on) { if (first < 0) first = q; last = q; }
                if (first >= 0 && q - last > 3) {
                    int length = last - first + 1;
                    if (length >= minimumRun) scores[pos] += length * Math.min(1.0, length / (double) fullRun);
                    first = -1;
                }
            }
        }
        List<Peak> found = new ArrayList<>();
        for (int i = 0; i < across;) {
            int start = i; double level = scores[i];
            while (++i < across && Math.abs(scores[i] - level) < .001) { }
            if (level < Math.max(10, along * .014) || i - start > Math.max(12, across / 30)) continue;
            if ((start > 0 && scores[start - 1] > level) || (i < across && scores[i] > level)) continue;
            boolean stronger = false; double low = level;
            for (int k = Math.max(0, start - 5); k < Math.min(across, i + 5); k++) {
                if (scores[k] > level * 1.05) stronger = true;
                low = Math.min(low, scores[k]);
            }
            if (!stronger && level - low >= Math.max(5, level * .12)) found.add(new Peak((start + i - 1) / 2f, (float) level));
        }
        // Retain the strongest evidence if an image contains a large amount of print.
        if (found.size() > 350) {
            Collections.sort(found, (a, b) -> Float.compare(b.strength, a.strength));
            found = new ArrayList<>(found.subList(0, 350));
            Collections.sort(found, (a, b) -> Float.compare(a.position, b.position));
        }
        return found;
    }
    private List<Axis> candidates(boolean vertical) {
        List<Peak> peaks = peaks(vertical); int across = vertical ? width : height;
        List<Axis> out = new ArrayList<>();
        for (int i = 0; i + 2 < peaks.size(); i++) {
            for (int j = i + 1; j < Math.min(i + 7, peaks.size()); j++) for (int divisor = 1; divisor <= 3; divisor++) {
                float pitch = (peaks.get(j).position - peaks.get(i).position) / divisor;
                if (pitch < 7 || pitch > across / 2f) continue;
                ArrayList<Float> lines = new ArrayList<>(); lines.add(peaks.get(i).position);
                float predicted = lines.get(0) + pitch;
                int consecutiveMisses = 0, matched = 1; double error = 0;
                while (predicted < across && lines.size() <= 100) {
                    Peak nearest = null; float distance = Math.max(1.6f, pitch * .15f);
                    for (Peak peak : peaks) {
                        if (peak.position < predicted - distance) continue;
                        if (peak.position > predicted + distance) break;
                        float delta = Math.abs(peak.position - predicted);
                        if (delta < distance) { nearest = peak; distance = delta; }
                    }
                    if (nearest == null) {
                        if (consecutiveMisses >= 2) break;
                        consecutiveMisses++;
                        lines.add(predicted); predicted += pitch;
                    } else {
                        consecutiveMisses = 0; matched++; error += distance / pitch;
                        lines.add(nearest.position);
                        pitch = (nearest.position - lines.get(0)) / (lines.size() - 1);
                        predicted = nearest.position + pitch;
                    }
                }
                for (int n = 0; n < consecutiveMisses && lines.size() > 1; n++) lines.remove(lines.size() - 1);
                if (matched < 4 || lines.size() < 4 || matched / (double) lines.size() < .65) continue;
                double span = lines.get(lines.size() - 1) - lines.get(0);
                double score = matched * Math.sqrt(span / across) * (matched / (double) lines.size()) - error * .5;
                float[] coords = new float[lines.size()]; for (int k = 0; k < coords.length; k++) coords[k] = lines.get(k);
                addCandidate(out, new Axis(coords, score));
            }
        }
        return out;
    }
    private void addCandidate(List<Axis> out, Axis candidate) {
        for (int i = 0; i < out.size(); i++) {
            Axis old = out.get(i);
            float pitch = (old.lines[old.lines.length - 1] - old.lines[0]) / (old.lines.length - 1);
            float otherPitch = (candidate.lines[candidate.lines.length - 1] - candidate.lines[0]) / (candidate.lines.length - 1);
            if (Math.abs(pitch - otherPitch) < pitch * .08f && Math.abs(old.lines[0] - candidate.lines[0]) < pitch * .6f
                    && Math.abs(old.lines[old.lines.length - 1] - candidate.lines[candidate.lines.length - 1]) < pitch * .6f) {
                if (candidate.score <= old.score) return;
                out.remove(i); break;
            }
        }
        out.add(candidate); Collections.sort(out, (a, b) -> Double.compare(b.score, a.score));
        if (out.size() > 18) out.remove(out.size() - 1);
    }
    private double coverage(float line, float start, float end, boolean vertical) {
        int hits = 0, total = 0;
        for (int q = Math.max(0, Math.round(start)); q <= Math.round(end); q++) {
            int p = Math.round(line); boolean on = false;
            for (int offset = -1; offset <= 1; offset++) on |= vertical ? dark(p + offset, q) : dark(q, p + offset);
            if (on) hits++; total++;
        }
        return hits / (double) Math.max(1, total);
    }
    private float[] trim(float[] lines, float start, float end, boolean vertical) {
        int first = 0, last = lines.length - 1;
        while (first <= last && coverage(lines[first], start, end, vertical) < .24) first++;
        while (last >= first && coverage(lines[last], start, end, vertical) < .24) last--;
        return java.util.Arrays.copyOfRange(lines, first, last + 1);
    }
    private double support(float[] lines, float start, float end, boolean vertical) {
        double sum = 0; int supported = 0;
        for (float line : lines) {
            double fraction = coverage(line, start, end, vertical);
            if (fraction >= .24) supported++;
            double evidence = Math.min(1, fraction / .80);
            sum += evidence * evidence;
        }
        return supported < Math.ceil(lines.length * .65) ? 0 : sum / lines.length;
    }
    private float[] extend(float[] lines, float[] cross, boolean vertical) {
        ArrayList<Float> out = new ArrayList<>(); for (float line : lines) out.add(line);
        float pitch = (lines[lines.length - 1] - lines[0]) / (lines.length - 1);
        int limit = vertical ? width : height;
        for (int side = 0; side < 2; side++) for (int step = 0; step < 4 && out.size() <= 100; step++) {
            float edge = side == 0 ? out.get(0) : out.get(out.size() - 1);
            float predicted = edge + (side == 0 ? -pitch : pitch);
            if (predicted < 0 || predicted >= limit) break;
            int supported = 0;
            for (float line : cross) if (coverage(line, Math.min(predicted, edge) + 2, Math.max(predicted, edge) - 2, !vertical) >= .72) supported++;
            if (supported < Math.ceil(cross.length * .7)) break;
            if (side == 0) out.add(0, predicted); else out.add(predicted);
        }
        float[] result = new float[out.size()]; for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }
}
