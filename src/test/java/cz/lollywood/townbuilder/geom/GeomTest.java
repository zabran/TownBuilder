package cz.lollywood.townbuilder.geom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GeomTest {
    @Test
    void markersAreOrderedAroundTheirCentre() {
        // a 10 x 10 square given in a scrambled order
        PlotPolygon p = PlotPolygon.fromMarkers(List.of(new int[]{10, 0}, new int[]{0, 10}, new int[]{0, 0}, new int[]{10, 10}));
        assertFalse(p.selfIntersects());
        assertEquals(100.0, p.area(), 1e-9);
        assertTrue(p.containsCell(5, 5));
        assertFalse(p.containsCell(12, 5));
    }

    @Test
    void anLShapedPlotWithoutADeepNotchStaysSimple() {
        PlotPolygon p = PlotPolygon.fromMarkers(List.of(new int[]{0, 0}, new int[]{20, 0}, new int[]{20, 8}, new int[]{9, 8},
                new int[]{9, 16}, new int[]{0, 16}));
        assertFalse(p.selfIntersects());
        assertTrue(p.containsCell(4, 14));
        assertFalse(p.containsCell(15, 14));
    }

    @Test
    void theFenceLineIsFourConnectedAndClosed() {
        PlotPolygon p = PlotPolygon.fromMarkers(List.of(new int[]{0, 0}, new int[]{13, 3}, new int[]{9, 12}, new int[]{-2, 9}));
        List<int[]> b = p.boundaryCells();
        for (int i = 0; i < b.size(); i++) {
            int[] a = b.get(i), c = b.get((i + 1) % b.size());
            assertEquals(1, Math.abs(a[0] - c[0]) + Math.abs(a[1] - c[1]), "cells " + i + " and the next must touch by a side");
        }
    }

    @Test
    void segmentsAddUpExactly() {
        record M(String n, int len, int w) {}
        List<M> mods = List.of(new M("a", 2, 10), new M("b", 3, 10));
        Random rnd = new Random(1);
        for (int L = 2; L <= 30; L++) {
            List<M> f = SegmentFiller.fill(L, mods, M::len, M::w, m -> true, rnd);
            assertNotNull(f, "length " + L);
            assertEquals(L, f.stream().mapToInt(M::len).sum());
        }
        assertNull(SegmentFiller.fill(1, mods, M::len, M::w, m -> true, rnd));
        boolean[] r = SegmentFiller.reachable(List.of(2, 3), 20);
        assertEquals(4, SegmentFiller.doorStart(11, 3, r)); // 4 + 3 + 4
    }

    @Test
    void aRectangleHasFourOuterCorners() {
        Footprint.Ring r = Footprint.rectangle(0, 0, 7, 5).ring();
        assertEquals(4, r.corners().size());
        assertTrue(r.corners().stream().allMatch(Footprint.Corner::outer));
        List<Integer> lens = new ArrayList<>();
        r.segments().forEach(s -> lens.add(s.length()));
        assertEquals(List.of(5, 3, 5, 3), lens);
        assertEquals(Dir.NORTH, r.segments().get(0).normal());
        assertEquals(Dir.EAST, r.segments().get(1).normal());
    }

    @Test
    void anLHasOneInnerCornerAndATTwo() {
        Footprint l = new Footprint(List.of(new Footprint.Rect(0, 0, 9, 5), new Footprint.Rect(0, 6, 4, 9)));
        Footprint.Ring rl = l.ring();
        assertEquals(6, rl.corners().size());
        assertEquals(1, rl.corners().stream().filter(c -> !c.outer()).count());
        assertEquals(l.area() - 0, l.area());
        Footprint t = new Footprint(List.of(new Footprint.Rect(0, 0, 12, 5), new Footprint.Rect(4, 6, 8, 10)));
        Footprint.Ring rt = t.ring();
        assertEquals(8, rt.corners().size());
        assertEquals(2, rt.corners().stream().filter(c -> !c.outer()).count());
        int ringCells = rt.cells().size();
        assertEquals(ringCells, rt.corners().size() + rt.segments().stream().mapToInt(Footprint.Segment::length).sum());
    }

    @Test
    void anLWithOneModuleLengthFallsBackToARectangle() {
        // a 36 x 35 plot, walls only 3 long (+ 3-long doors): every seed must still give a house (0.4.1's fix)
        java.util.Set<Long> inside = new java.util.HashSet<>();
        for (int x = 0; x < 36; x++) for (int z = 0; z < 35; z++) inside.add(PlotPolygon.key(x, z));
        boolean[] reach = SegmentFiller.reachable(List.of(3), 80);
        for (int seed = 0; seed < 40; seed++) {
            Planner pl = new Planner(inside, 2, new Random(seed));
            assertNotNull(pl.house(List.of("rectangle", "L_shape"), reach, List.of(3), 470, new double[]{18, 34}), "seed " + seed);
        }
    }

    @Test
    void wideOuterCornersShortenTheWallsAndSitInside() {
        // a 9 x 7 rectangle at (0,0): sides of 7 and 5 cells between the corners; with 2-wide corners 5 and 3
        Footprint.Ring r = Footprint.rectangle(0, 0, 9, 7).ring();
        Footprint.Ring w = Footprint.trimmed(r, 2);
        assertEquals(r.corners(), w.corners());
        for (int i = 0; i < 4; i++) assertEquals(r.segments().get(i).length() - 2, w.segments().get(i).length());
        // every trimmed wall starts one cell later along its own direction
        for (int i = 0; i < 4; i++) {
            Footprint.Segment a = r.segments().get(i), b = w.segments().get(i);
            assertEquals(a.x() + a.along().dx, b.x());
            assertEquals(a.z() + a.along().dz, b.z());
        }
        // each corner's 2 x 2 box lies inside the house, its corner cell outermost
        for (Footprint.Corner c : r.corners()) {
            int[] m = Footprint.cornerMin(c, 2);
            for (int x = m[0]; x <= m[0] + 1; x++) for (int z = m[1]; z <= m[1] + 1; z++) assertTrue(x >= 0 && x <= 8 && z >= 0 && z <= 6, c + " box at " + x + "," + z);
            assertTrue(c.x() == m[0] || c.x() == m[0] + 1);
            assertTrue(c.z() == m[1] || c.z() == m[1] + 1);
        }
        // the boxes and the trimmed walls cover the ring exactly once
        java.util.Set<Long> covered = new java.util.HashSet<>();
        for (Footprint.Corner c : r.corners()) {
            int[] m = Footprint.cornerMin(c, 2);
            for (int x = m[0]; x <= m[0] + 1; x++) for (int z = m[1]; z <= m[1] + 1; z++) if (x == 0 || x == 8 || z == 0 || z == 6) assertTrue(covered.add(PlotPolygon.key(x, z)));
        }
        for (Footprint.Segment s : w.segments()) for (int i = 0; i < s.length(); i++) assertTrue(covered.add(PlotPolygon.key(s.cellX(i), s.cellZ(i))));
        assertEquals(r.cells().size(), covered.size());
        // an inner corner stays one block; a wall too short for its corners is refused
        Footprint l = new Footprint(List.of(new Footprint.Rect(0, 0, 8, 4), new Footprint.Rect(0, 5, 4, 10)));
        Footprint.Ring rl = Footprint.trimmed(l.ring(), 2);
        assertEquals(6, rl.corners().size());
        assertThrows(IllegalStateException.class, () -> Footprint.trimmed(Footprint.rectangle(0, 0, 4, 4).ring(), 3));
        // the planner then only offers sides of 2 corners + what the modules make
        boolean[] reach = SegmentFiller.reachable(List.of(3), 60);
        java.util.Set<Long> inside = new java.util.HashSet<>();
        for (int x = 0; x < 30; x++) for (int z = 0; z < 30; z++) inside.add(PlotPolygon.key(x, z));
        Planner pl = new Planner(inside, 1, new Random(3));
        pl.cornerWidth = 2;
        Planner.House h = pl.house(List.of("rectangle"), reach, List.of(3), 400, null);
        assertNotNull(h);
        Footprint.Rect b = h.fp().bounds();
        assertEquals(0, (b.width() - 4) % 3);
        assertEquals(0, (b.depth() - 4) % 3);
    }

    @Test
    void wideInnerCornersCoverTheirArmsAndTheNotch() {
        // the L: a 9 x 5 top wing and a 5 x 6 leg below its west part; the inner corner at (4,4), its notch to the south-east
        Footprint l = new Footprint(List.of(new Footprint.Rect(0, 0, 8, 4), new Footprint.Rect(0, 5, 4, 10)));
        Footprint.Ring r = l.ring();
        Footprint.Ring w = Footprint.trimmed(r, 2, 2);
        java.util.Set<Long> house = new java.util.HashSet<>();
        for (Footprint.Rect q : l.wings) for (int x = q.x0(); x <= q.x1(); x++) for (int z = q.z0(); z <= q.z1(); z++) house.add(PlotPolygon.key(x, z));
        java.util.Set<Long> ring = new java.util.HashSet<>();
        for (int[] c : r.cells()) ring.add(PlotPolygon.key(c[0], c[1]));
        // the boxes on the ring and the trimmed walls cover the ring exactly once
        java.util.Set<Long> covered = new java.util.HashSet<>();
        int notchCells = 0;
        for (Footprint.Corner c : r.corners()) {
            int[] m = Footprint.cornerMin(c, 2);
            assertTrue(c.x() == m[0] || c.x() == m[0] + 1);
            assertTrue(c.z() == m[1] || c.z() == m[1] + 1);
            for (int x = m[0]; x <= m[0] + 1; x++)
                for (int z = m[1]; z <= m[1] + 1; z++) {
                    long k = PlotPolygon.key(x, z);
                    if (ring.contains(k)) assertTrue(covered.add(k), c + " overlaps at " + x + "," + z);
                    else if (!house.contains(k)) { assertFalse(c.outer(), "an outer corner's box sticks out at " + x + "," + z); notchCells++; }
                }
        }
        assertEquals(1, notchCells);   // the inner corner's one notch cell, outside the house
        for (Footprint.Segment s : w.segments()) for (int i = 0; i < s.length(); i++) assertTrue(covered.add(PlotPolygon.key(s.cellX(i), s.cellZ(i))));
        assertEquals(ring.size(), covered.size());
    }

    // ------------------------------------------------------------------ 0.7.0: fence posts (Arag 11-15)
    private static int[] at(List<int[]> loop, int i) { return loop.get(i); }

    @Test
    void aRectangularFenceHasPostsOnItsFourCornersAndEvenlyBetween() {
        List<int[]> loop = PlotPolygon.fromMarkers(List.of(new int[]{0, 0}, new int[]{12, 0}, new int[]{12, 8}, new int[]{0, 8})).boundaryCells();
        List<Integer> corners = FencePosts.corners(loop);
        assertEquals(4, corners.size(), "the four corners and nothing else");
        var posts = FencePosts.posts(loop, 3);
        for (int c : corners) assertTrue(posts.contains(c));
        // 12 long: 3 gaps of 4; 8 long: 2 gaps of 4 -> 4 corners + 2 x 2 + 2 x 1 between
        assertEquals(10, posts.size());
    }

    @Test
    void aSlantedFenceGetsNoPostOnEveryStairStep() {
        // Arag's plot: a diagonal run of 14 x 14 blocks, as a 4-connected staircase of 29 cells
        List<int[]> loop = PlotPolygon.fromMarkers(List.of(new int[]{0, 0}, new int[]{10, 0}, new int[]{24, 14}, new int[]{24, 22},
                new int[]{0, 22})).boundaryCells();
        assertEquals(5, FencePosts.corners(loop).size(), "the five markers' corners, not every step");
        var posts = FencePosts.posts(loop, 4);
        List<int[]> diag = new ArrayList<>();
        for (int i : posts) {
            int[] c = at(loop, i);
            if (c[0] - c[1] >= 9 && c[0] - c[1] <= 11 && c[0] > 10 && c[0] < 24) diag.add(c);
        }
        // 14 * sqrt 2 = 19.8 blocks: 4 gaps of ~5 -> 3 posts between the two corners
        assertEquals(3, diag.size(), "posts between the diagonal's ends");
        for (int a : posts)
            for (int b : posts)
                if (a < b) {
                    int[] p = at(loop, a), q = at(loop, b);
                    assertFalse(Math.abs(p[0] - q[0]) <= 1 && Math.abs(p[1] - q[1]) <= 1, "no two posts side by side");
                }
    }

    @Test
    void anOuterCornerWithAnOffsetSticksOutOfBothWalls() {
        // 0.8.0 (Arag 11-17): a corner 1 wide on the wall line with offset 1 is a 2 x 2 box reaching 1 outside on both faces
        Footprint fp = Footprint.rectangle(0, 0, 10, 8);
        java.util.Map<String, int[]> got = new java.util.HashMap<>();
        for (Footprint.Corner c : fp.ring().corners()) got.put(c.x() + "," + c.z(), Footprint.cornerMin(c, 1, 1));
        assertArrayEquals(new int[]{-1, -1}, got.get("0,0"));   // north-west: out to the west and north
        assertArrayEquals(new int[]{9, -1}, got.get("9,0"));    // north-east: the box 9..10, -1..0
        assertArrayEquals(new int[]{9, 7}, got.get("9,7"));     // south-east: 9..10, 7..8
        assertArrayEquals(new int[]{-1, 7}, got.get("0,7"));    // south-west
        // 2 wide on the wall line, offset 1: a 3 x 3 box, the corner cell 1 in from its outer edges
        for (Footprint.Corner c : fp.ring().corners()) {
            int[] m = Footprint.cornerMin(c, 2, 1);
            assertTrue(c.x() - m[0] == 1 || m[0] + 2 - c.x() == 1, "x: the corner cell one in from the outside edge");
            assertTrue(c.z() - m[1] == 1 || m[1] + 2 - c.z() == 1, "z: the corner cell one in from the outside edge");
            for (int x = m[0]; x < m[0] + 3; x++)
                for (int z = m[1]; z < m[1] + 3; z++)
                    if (fp.has(x, z)) assertTrue(Math.abs(x - c.x()) <= 1 && Math.abs(z - c.z()) <= 1, "inside the house only next to its corner");
        }
        // an inner corner ignores the offset
        Footprint l = new Footprint(List.of(new Footprint.Rect(0, 0, 9, 4), new Footprint.Rect(0, 5, 4, 9)));
        for (Footprint.Corner c : l.ring().corners())
            if (!c.outer()) assertArrayEquals(Footprint.cornerMin(c, 2), Footprint.cornerMin(c, 2, 3));
    }
}
