package cz.lollywood.townbuilder.geom;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Fills a straight wall segment of a given length with modules whose lengths add up to it exactly (a small integer sum
 * problem: which totals are reachable is worked out first, then modules are drawn by weight among those that still leave a
 * reachable rest).
 */
public final class SegmentFiller {
    private SegmentFiller() {}

    public static boolean[] reachable(List<Integer> lengths, int max) {
        boolean[] r = new boolean[max + 1];
        r[0] = true;
        for (int s = 1; s <= max; s++)
            for (int l : lengths) if (l >= 1 && l <= s && r[s - l]) { r[s] = true; break; }
        return r;
    }

    /**
     * @param ok a per-pick check (the caller's max_per_building count); if it blocks every way, the fill is retried ignoring it
     * @return the modules in order along the segment, or null if the length cannot be made at all
     */
    public static <T> List<T> fill(int length, List<T> items, ToIntFunction<T> len, ToIntFunction<T> weight, Predicate<T> ok, Random rnd) {
        List<T> out = tryFill(length, items, len, weight, ok, rnd);
        if (out == null) out = tryFill(length, items, len, weight, t -> true, rnd);
        return out;
    }

    private static <T> List<T> tryFill(int length, List<T> items, ToIntFunction<T> len, ToIntFunction<T> weight, Predicate<T> ok, Random rnd) {
        if (length == 0) return new ArrayList<>();
        List<T> usable = new ArrayList<>();
        for (T t : items) if (ok.test(t)) usable.add(t);
        List<Integer> lens = usable.stream().map(len::applyAsInt).toList();
        boolean[] r = reachable(lens, length);
        if (!r[length]) return null;
        List<T> out = new ArrayList<>();
        int rest = length;
        while (rest > 0) {
            List<T> cand = new ArrayList<>();
            for (T t : usable) {
                int l = len.applyAsInt(t);
                if (l <= rest && r[rest - l] && ok.test(t)) cand.add(t);
            }
            if (cand.isEmpty()) return null;
            T pick = weighted(cand, weight, rnd);
            out.add(pick);
            rest -= len.applyAsInt(pick);
        }
        return out;
    }

    /** Where a door of length {@code doorLen} can stand so both sides are fillable: the start nearest the middle, or -1. */
    public static int doorStart(int length, int doorLen, boolean[] reach) {
        int best = -1;
        double mid = (length - doorLen) / 2.0;
        for (int p = 0; p + doorLen <= length; p++) {
            int right = length - doorLen - p;
            if (p < reach.length && right < reach.length && reach[p] && reach[right]
                    && (best < 0 || Math.abs(p - mid) < Math.abs(best - mid))) best = p;
        }
        return best;
    }

    static <T> T weighted(List<T> items, ToIntFunction<T> weight, Random rnd) {
        int total = 0;
        for (T t : items) total += Math.max(1, weight.applyAsInt(t));
        int x = rnd.nextInt(total);
        for (T t : items) {
            x -= Math.max(1, weight.applyAsInt(t));
            if (x < 0) return t;
        }
        return items.get(items.size() - 1);
    }
}
