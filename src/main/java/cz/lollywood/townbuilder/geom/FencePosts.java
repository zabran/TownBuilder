package cz.lollywood.townbuilder.geom;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where a fence's posts stand (0.7.0, Arag 11-15: on a slanted fence every stair-step had become a post). Posts go on the
 * outline's real corners and evenly between them, measured in straight (Euclidean) distance, about every
 * {@code blocks_between_col + 1} blocks; a post that would stand right beside another (a short side) is left out. The real
 * corners are found by simplifying the loop of fence cells (Douglas-Peucker): a slanted run drawn in blocks wobbles less than a
 * block off its straight line, a real corner much more.
 */
public final class FencePosts {
    private FencePosts() {}

    /** How far (in blocks) a cell may lie off a straight run and still belong to it. */
    public static final double TOLERANCE = 1.0;

    /**
     * @param loop    the fence cells in order round the loop, {x, z} each
     * @param between the blocks wanted between two posts (the preset's blocks_between_col)
     * @return the indices into {@code loop} that get a post
     */
    public static Set<Integer> posts(List<int[]> loop, int between) {
        int n = loop.size();
        Set<Integer> out = new TreeSet<>();
        if (n == 0) return out;
        List<Integer> v = corners(loop);
        out.addAll(v);
        double step = Math.max(1, between + 1);
        for (int a = 0; a < v.size(); a++) {
            int i0 = v.get(a), i1 = v.get((a + 1) % v.size());
            int len = Math.floorMod(i1 - i0, n);
            if (len == 0) len = n;
            int[] p = loop.get(i0), q = loop.get(i1);
            double L = Math.hypot(q[0] - p[0], q[1] - p[1]);
            int parts = (int) Math.round(L / step);
            for (int j = 1; j < parts; j++) {
                double tx = p[0] + (q[0] - p[0]) * (double) j / parts, tz = p[1] + (q[1] - p[1]) * (double) j / parts;
                int best = -1;
                double bd = Double.MAX_VALUE;
                for (int s = 1; s < len; s++) {
                    int i = (i0 + s) % n;
                    int[] c = loop.get(i);
                    double d = (c[0] - tx) * (c[0] - tx) + (c[1] - tz) * (c[1] - tz);
                    if (d < bd) { bd = d; best = i; }
                }
                if (best >= 0 && !besidePost(loop, out, best)) out.add(best);
            }
        }
        return out;
    }

    /** True when a post already stands on this cell or touches it (also diagonally). */
    static boolean besidePost(List<int[]> loop, Set<Integer> posts, int i) {
        int[] c = loop.get(i);
        for (int p : posts) {
            int[] o = loop.get(p);
            if (Math.abs(o[0] - c[0]) <= 1 && Math.abs(o[1] - c[1]) <= 1) return true;
        }
        return false;
    }

    /** The loop's real corners (indices in loop order). */
    public static List<Integer> corners(List<int[]> loop) {
        int n = loop.size();
        List<Integer> all = new ArrayList<>();
        if (n < 4) {
            for (int i = 0; i < n; i++) all.add(i);
            return all;
        }
        int far = 0;
        double fd = -1;
        for (int i = 1; i < n; i++) {
            double d = d2(loop.get(0), loop.get(i));
            if (d > fd) { fd = d; far = i; }
        }
        TreeSet<Integer> keep = new TreeSet<>(List.of(0, far));
        simplify(loop, 0, far, keep);
        simplify(loop, far, n, keep);
        // the two anchors were chosen, not found: drop either when it lies on a straight run between its neighbours
        boolean changed = true;
        while (changed && keep.size() > 3) {
            changed = false;
            List<Integer> k = new ArrayList<>(keep);
            for (int a = 0; a < k.size(); a++) {
                int prev = k.get((a - 1 + k.size()) % k.size()), cur = k.get(a), next = k.get((a + 1) % k.size());
                if (offLine(loop.get(cur), loop.get(prev), loop.get(next)) <= TOLERANCE && straightBetween(loop, prev, next)) {
                    keep.remove(cur);
                    changed = true;
                    break;
                }
            }
        }
        return new ArrayList<>(keep);
    }

    /** Every cell from a to b (round the loop) lies within the tolerance of the line a-b. */
    private static boolean straightBetween(List<int[]> loop, int a, int b) {
        int n = loop.size();
        int len = Math.floorMod(b - a, n);
        for (int s = 1; s < len; s++) if (offLine(loop.get((a + s) % n), loop.get(a), loop.get(b)) > TOLERANCE) return false;
        return true;
    }

    /** Douglas-Peucker between loop indices a and b (b may be n, meaning index 0 again). */
    private static void simplify(List<int[]> loop, int a, int b, Set<Integer> keep) {
        int n = loop.size();
        int[] p = loop.get(a % n), q = loop.get(b % n);
        int best = -1;
        double bd = 0;
        for (int i = a + 1; i < b; i++) {
            double d = offLine(loop.get(i % n), p, q);
            if (d > bd) { bd = d; best = i; }
        }
        if (best >= 0 && bd > TOLERANCE) {
            keep.add(best % n);
            simplify(loop, a, best, keep);
            simplify(loop, best, b, keep);
        }
    }

    /** The distance of point c from the segment p-q. */
    static double offLine(int[] c, int[] p, int[] q) {
        double dx = q[0] - p[0], dz = q[1] - p[1], l2 = dx * dx + dz * dz;
        if (l2 == 0) return Math.sqrt(d2(c, p));
        double t = Math.max(0, Math.min(1, ((c[0] - p[0]) * dx + (c[1] - p[1]) * dz) / l2));
        double x = p[0] + t * dx, z = p[1] + t * dz;
        return Math.hypot(c[0] - x, c[1] - z);
    }

    private static double d2(int[] a, int[] b) {
        return (double) (a[0] - b[0]) * (a[0] - b[0]) + (double) (a[1] - b[1]) * (a[1] - b[1]);
    }
}
