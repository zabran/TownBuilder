package cz.lollywood.townbuilder.geom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The plot's outline from the builder's REDSTONE_BLOCK markers. The markers come unordered, so they are sorted by their angle
 * round the centre (Arag 11-14: agreed): right for every plot without a deep notch; {@link #selfIntersects()} tells when not.
 */
public final class PlotPolygon {
    /** Ordered vertices as {x, z}. */
    public final List<int[]> v;

    private PlotPolygon(List<int[]> v) {
        this.v = v;
    }

    public static PlotPolygon fromMarkers(List<int[]> markers) {
        if (markers.size() < 3) throw new IllegalArgumentException("at least 3 markers are needed");
        double cx = 0, cz = 0;
        for (int[] p : markers) { cx += p[0] + 0.5; cz += p[1] + 0.5; }
        cx /= markers.size(); cz /= markers.size();
        final double fx = cx, fz = cz;
        List<int[]> s = new ArrayList<>(markers);
        s.sort(Comparator.comparingDouble(p -> Math.atan2(p[1] + 0.5 - fz, p[0] + 0.5 - fx)));
        return new PlotPolygon(s);
    }

    /** Area in blocks (shoelace over the marker cells' centres). */
    public double area() {
        double a = 0;
        for (int i = 0; i < v.size(); i++) {
            int[] p = v.get(i), q = v.get((i + 1) % v.size());
            a += (double) p[0] * q[1] - (double) q[0] * p[1];
        }
        return Math.abs(a) / 2;
    }

    /** Is the point inside (ray casting; the outline runs through the marker cells' centres)? */
    public boolean contains(double x, double z) {
        boolean in = false;
        for (int i = 0, j = v.size() - 1; i < v.size(); j = i++) {
            double xi = v.get(i)[0] + 0.5, zi = v.get(i)[1] + 0.5, xj = v.get(j)[0] + 0.5, zj = v.get(j)[1] + 0.5;
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    public boolean containsCell(int x, int z) {
        return contains(x + 0.5, z + 0.5);
    }

    public boolean selfIntersects() {
        int n = v.size();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(i - j) <= 1 || (i == 0 && j == n - 1)) continue;
                if (segmentsCross(v.get(i), v.get((i + 1) % n), v.get(j), v.get((j + 1) % n))) return true;
            }
        return false;
    }

    private static boolean segmentsCross(int[] a, int[] b, int[] c, int[] d) {
        long d1 = cross(c, d, a), d2 = cross(c, d, b), d3 = cross(a, b, c), d4 = cross(a, b, d);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static long cross(int[] o, int[] a, int[] b) {
        return (long) (a[0] - o[0]) * (b[1] - o[1]) - (long) (a[1] - o[1]) * (b[0] - o[0]);
    }

    /**
     * The fence line: the edges drawn cell by cell, 4-connected (a step in x or in z, never both, so fence blocks touch), as one
     * closed loop without repeats.
     */
    public List<int[]> boundaryCells() {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < v.size(); i++) {
            for (int[] c : line4(v.get(i), v.get((i + 1) % v.size()))) {
                if (seen.add(key(c[0], c[1]))) out.add(c);
            }
        }
        return out;
    }

    /** A 4-connected line from a to b, both ends included. */
    public static List<int[]> line4(int[] a, int[] b) {
        List<int[]> out = new ArrayList<>();
        int x = a[0], z = a[1];
        int dx = Math.abs(b[0] - x), dz = Math.abs(b[1] - z);
        int sx = Integer.signum(b[0] - x), sz = Integer.signum(b[1] - z);
        out.add(new int[]{x, z});
        int ix = 0, iz = 0;
        while (ix < dx || iz < dz) {
            // step along the axis whose next step keeps us nearest the ideal line
            if (ix < dx && (iz >= dz || (0.5 + ix) * dz < (0.5 + iz) * dx)) { x += sx; ix++; } else { z += sz; iz++; }
            out.add(new int[]{x, z});
        }
        return out;
    }

    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public static int kx(long k) { return (int) (k >> 32); }
    public static int kz(long k) { return (int) k; }

    /** The bounding box {minX, minZ, maxX, maxZ}. */
    public int[] bounds() {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int[] p : v) {
            b[0] = Math.min(b[0], p[0]); b[1] = Math.min(b[1], p[1]);
            b[2] = Math.max(b[2], p[0]); b[3] = Math.max(b[3], p[1]);
        }
        return b;
    }
}
