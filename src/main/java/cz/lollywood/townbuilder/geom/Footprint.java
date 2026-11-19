package cz.lollywood.townbuilder.geom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A house's ground plan: a union of axis-aligned rectangles (the main body first, then wings: rectangle, L or T). The walls run
 * on its RING (every cell with an outside cell among its 8 neighbours); walking the ring clockwise gives the corners (a right
 * turn = an outer corner, a left turn = an inner one) and the straight segments between them, each with its outward face.
 * A segment's length is the cells strictly between its two corners; with corners k blocks wide (0.5.0 outer, 0.6.0 inner) see {@link #trimmed}.
 */
public final class Footprint {
    public record Rect(int x0, int z0, int x1, int z1) {
        public Rect {
            if (x1 < x0 || z1 < z0) throw new IllegalArgumentException("empty rect");
        }
        public int width() { return x1 - x0 + 1; }
        public int depth() { return z1 - z0 + 1; }
        public int area() { return width() * depth(); }
        public boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
        public Rect shifted(int dx, int dz) { return new Rect(x0 + dx, z0 + dz, x1 + dx, z1 + dz); }
    }

    /** A corner of the ring: {@code in} = the walking direction arriving, {@code out} = leaving (clockwise walk). */
    public record Corner(int x, int z, boolean outer, Dir in, Dir out) {
        /** The outward face of the wall that starts here. */
        public Dir face() { return out.ccw(); }
    }

    /** {@code length} cells from (x, z) stepping {@code along}; the facade looks {@code normal} (outward). */
    public record Segment(int x, int z, Dir along, int length, Dir normal) {
        public int cellX(int i) { return x + along.dx * i; }
        public int cellZ(int i) { return z + along.dz * i; }
    }

    public record Ring(List<Corner> corners, List<Segment> segments, List<int[]> cells) {}

    public final List<Rect> wings;
    private final Set<Long> cells = new HashSet<>();

    public Footprint(List<Rect> wings) {
        this.wings = List.copyOf(wings);
        for (Rect r : wings)
            for (int x = r.x0; x <= r.x1; x++)
                for (int z = r.z0; z <= r.z1; z++) cells.add(PlotPolygon.key(x, z));
    }

    public static Footprint rectangle(int x0, int z0, int w, int d) {
        return new Footprint(List.of(new Rect(x0, z0, x0 + w - 1, z0 + d - 1)));
    }

    public boolean has(int x, int z) { return cells.contains(PlotPolygon.key(x, z)); }
    public Set<Long> cells() { return cells; }
    public int area() { return cells.size(); }

    public Rect bounds() {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Rect r : wings) { x0 = Math.min(x0, r.x0); z0 = Math.min(z0, r.z0); x1 = Math.max(x1, r.x1); z1 = Math.max(z1, r.z1); }
        return new Rect(x0, z0, x1, z1);
    }

    public boolean onRing(int x, int z) {
        if (!has(x, z)) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) if ((dx != 0 || dz != 0) && !has(x + dx, z + dz)) return true;
        return false;
    }

    /** Walks the ring clockwise from its north-west corner. Throws if the plan has an arm thinner than 3 (no clean ring). */
    public Ring ring() {
        int sx = Integer.MAX_VALUE, sz = Integer.MAX_VALUE;
        for (long k : cells) {
            int x = PlotPolygon.kx(k), z = PlotPolygon.kz(k);
            if (z < sz || (z == sz && x < sx)) { sx = x; sz = z; }
        }
        List<int[]> loop = new ArrayList<>();
        List<Dir> outDirs = new ArrayList<>();
        int x = sx, z = sz;
        Dir d = Dir.EAST;
        int guard = cells.size() * 4 + 8;
        do {
            Dir next = null;
            for (Dir t : new Dir[]{d.ccw(), d, d.cw()}) {
                if (onRing(x + t.dx, z + t.dz)) { next = t; break; }
            }
            if (next == null) throw new IllegalStateException("the ring breaks at " + x + "," + z + " (an arm thinner than 3?)");
            loop.add(new int[]{x, z});
            outDirs.add(next);
            x += next.dx; z += next.dz; d = next;
            if (--guard < 0) throw new IllegalStateException("the ring does not close (an arm thinner than 3?)");
        } while (x != sx || z != sz);

        int n = loop.size();
        List<Corner> corners = new ArrayList<>();
        List<Integer> cornerIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Dir in = outDirs.get((i - 1 + n) % n), out = outDirs.get(i);
            if (in != out) {
                corners.add(new Corner(loop.get(i)[0], loop.get(i)[1], out == in.cw(), in, out));
                cornerIdx.add(i);
            }
        }
        List<Segment> segs = new ArrayList<>();
        for (int c = 0; c < cornerIdx.size(); c++) {
            int i = cornerIdx.get(c), j = cornerIdx.get((c + 1) % cornerIdx.size());
            int len = (j - i - 1 + n) % n;
            Dir along = outDirs.get(i);
            int[] first = loop.get((i + 1) % n);
            segs.add(new Segment(first[0], first[1], along, len, along.ccw()));
        }
        return new Ring(corners, segs, loop);
    }

    /** {@link #trimmed(Ring, int, int)} with one-block inner corners. */
    public static Ring trimmed(Ring r, int k) { return trimmed(r, k, 1); }

    /**
     * The ring with outer corners {@code k} and inner corners {@code ki} blocks wide (0.5.0, 0.6.0): a corner module k wide covers
     * its corner cell and k - 1 cells of each wall meeting there, so every segment loses k - 1 cells at each end. Throws when a wall
     * is too short for its corners.
     */
    public static Ring trimmed(Ring r, int k, int ki) {
        if (k <= 1 && ki <= 1) return r;
        List<Corner> cs = r.corners();
        int n = cs.size();
        List<Segment> out = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            Segment s = r.segments().get(c);
            int a = (cs.get(c).outer() ? k : ki) - 1, b = (cs.get((c + 1) % n).outer() ? k : ki) - 1;
            int len = s.length() - a - b;
            if (len < 0) throw new IllegalStateException("a wall of " + s.length() + " blocks between corners is too short for corners " + k + " (inner " + ki + ") wide");
            out.add(new Segment(s.x() + s.along().dx * a, s.z() + s.along().dz * a, s.along(), len, s.normal()));
        }
        return new Ring(cs, out, r.cells());
    }

    /**
     * Where a corner module's turned box starts (its minimum x and z): the k x k box covers the corner cell and k - 1 cells along
     * both walls meeting there (back along the arriving wall, on along the leaving one). An outer corner's box lies inside the house;
     * an inner corner's covers the two wall arms and, between them, the notch outside the house (what the builder put there, air or
     * a buttress, stays there).
     */
    public static int[] cornerMin(Corner c, int k) {
        if (k <= 1) return new int[]{c.x(), c.z()};
        Dir back = c.in().opposite(), on = c.out();
        int mx = c.x() - (back == Dir.WEST || on == Dir.WEST ? k - 1 : 0), mz = c.z() - (back == Dir.NORTH || on == Dir.NORTH ? k - 1 : 0);
        return new int[]{mx, mz};
    }

    /**
     * 0.8.0 (Arag 11-17): an OUTER corner {@code k} wide on the wall line that sticks {@code offset} blocks out of both walls: its
     * (k + offset) square box reaches {@code offset} rows past the wall line on each of its two faces (the arriving wall's face and
     * the leaving one's). Inner corners ignore the offset (their box already reaches k - 1 into the notch).
     */
    public static int[] cornerMin(Corner c, int k, int offset) {
        int[] m = cornerMin(c, k);
        if (offset <= 0 || !c.outer()) return m;
        Dir f1 = c.in().ccw(), f2 = c.out().ccw();
        if (f1 == Dir.WEST || f2 == Dir.WEST) m[0] -= offset;
        if (f1 == Dir.NORTH || f2 == Dir.NORTH) m[1] -= offset;
        return m;
    }
}
