package cz.lollywood.townbuilder.phase;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.world.block.BlockState;
import cz.lollywood.townbuilder.config.Defs;
import cz.lollywood.townbuilder.config.TagMatcher;
import cz.lollywood.townbuilder.geom.Footprint;
import cz.lollywood.townbuilder.geom.Planner;
import cz.lollywood.townbuilder.geom.PlotPolygon;
import cz.lollywood.townbuilder.geom.SegmentFiller;
import cz.lollywood.townbuilder.world.Blocks;
import cz.lollywood.townbuilder.world.Surface;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phase 1: the markers → the plot polygon → the house's plan and the extras → coloured concrete outlines on the ground. */
public final class OutlinePhase {
    private OutlinePhase() {}

    public static boolean run(Kit k) throws WorldEditException {
        Surface s = k.surface();
        List<int[]> markers = new ArrayList<>(), gates = new ArrayList<>();
        for (int x = k.x0; x <= k.x1; x++)
            for (int z = k.z0; z <= k.z1; z++) {
                Material m = s.type(x, z);
                if (m == Blocks.MARKER) markers.add(new int[]{x, s.y(x, z), z});
                else if (m == Blocks.GATE) gates.add(new int[]{x, s.y(x, z), z});
            }
        if (markers.size() < 3) {
            k.warn.add("Fewer than 3 REDSTONE_BLOCKs found on the ground inside the selection (" + markers.size() + ").");
            return false;
        }
        // the terrain under the markers: a marker on top of the ground is taken away, one set into it is replaced by the block below
        Map<Long, Integer> terrain = new HashMap<>();
        for (int[] m : concat(markers, gates)) {
            List<Integer> ns = new ArrayList<>();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int y = s.y(m[0] + d[0], m[2] + d[1]);
                if (y != Integer.MIN_VALUE) ns.add(y);
            }
            Collections.sort(ns);
            boolean onTop = !ns.isEmpty() && ns.get(ns.size() / 2) < m[1];
            terrain.put(PlotPolygon.key(m[0], m[2]), onTop ? m[1] - 1 : m[1]);
            BlockState fill = onTop ? Blocks.air() : k.get(m[0], m[1] - 1, m[2]);
            k.set(m[0], m[1], m[2], fill);
        }
        List<int[]> xz = new ArrayList<>();
        for (int[] m : markers) xz.add(new int[]{m[0], m[2]});
        PlotPolygon poly = PlotPolygon.fromMarkers(xz);
        if (poly.selfIntersects()) k.warn.add("The plot's outline crosses itself (a deep notch?): check the yellow line and fix it by hand.");
        double area = poly.area();

        List<Defs.PlotLayout> fit = new ArrayList<>();
        for (Defs.PlotLayout l : TagMatcher.all(k.store.layouts, k.tags)) if (l.minPlotArea <= area) fit.add(l);
        if (fit.isEmpty()) {
            k.warn.add("No plot layout matches " + k.tags + " for a plot of " + Math.round(area) + " blocks (check plot_layouts.json tags and min_plot_area).");
            return false;
        }
        Defs.PlotLayout layout = TagMatcher.best(fit, k.tags, k.rnd);
        List<Defs.WallModule> walls = new ArrayList<>(), doors = new ArrayList<>();
        for (Defs.WallModule w : TagMatcher.all(k.store.walls, k.tags)) {
            if (w.isCorner() || !w.allowedFloors.contains("ground")) continue;
            (w.hasDoor ? doors : walls).add(w);
        }
        if (walls.isEmpty()) {
            k.warn.add("No ground-floor wall modules match " + k.tags + " (save their .schem beside the JSON, then /plot reload).");
            return false;
        }
        boolean[] reach = SegmentFiller.reachable(walls.stream().map(w -> w.length).toList(), 256);

        List<int[]> fenceLine = poly.boundaryCells();
        Set<Long> fence = new HashSet<>();
        for (int[] c : fenceLine) fence.add(PlotPolygon.key(c[0], c[1]));
        Set<Long> inside = new HashSet<>();
        int[] b = poly.bounds();
        for (int x = b[0]; x <= b[2]; x++)
            for (int z = b[1]; z <= b[3]; z++)
                if (poly.containsCell(x, z) && !fence.contains(PlotPolygon.key(x, z))) inside.add(PlotPolygon.key(x, z));

        // the plot cleared of trees, flowers, crops and grass first (Arag 11-15), so the later phases build through clean air
        int cleared = clearVegetation(k, s, inside, fence);
        if (cleared > 0) k.info.add(cleared + " blocks of trees, leaves, flowers, crops and grass cleared inside the plot");

        // the gates sit on the fence line, the main one (nearest the plot's middle) sets where the house looks
        List<int[]> gateCells = new ArrayList<>();
        for (int[] g : gates) gateCells.add(gateCell(fenceLine, g[0], g[2]));
        double cx = (b[0] + b[2]) / 2.0, cz = (b[1] + b[3]) / 2.0;
        double[] target = null;
        for (int[] g : gateCells) {
            if (target == null || dist(g, cx, cz) < dist(new int[]{(int) target[0], (int) target[1]}, cx, cz))
                target = new double[]{g[0], g[1]};
        }

        Planner planner = new Planner(inside, Math.max(1, layout.houseMargin), k.rnd);
        planner.maxAspect = Math.max(1.0, layout.maxHouseAspect);
        planner.cornerWidth = BuildPhase.cornerWidth(k);
        planner.innerCornerWidth = BuildPhase.width(k, "inner_corner");
        int maxArea = (int) Math.max(25, layout.maxHouseShare * area);
        Planner.House house = planner.house(layout.houseShapes, reach, doors.stream().map(w -> w.length).toList(), maxArea, target);
        if (house == null) {
            k.warn.add("No house of " + layout.houseShapes + " fits this plot with the modules' lengths " + walls.stream().map(w -> w.length).distinct().toList()
                    + " (try a bigger plot, a smaller house_margin, or more module lengths).");
            return false;
        }

        // what is taken: the house and one free row round it
        Set<Long> taken = new HashSet<>();
        for (long c : house.fp().cells())
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) taken.add(PlotPolygon.key(PlotPolygon.kx(c) + dx, PlotPolygon.kz(c) + dz));
        double tx = target != null ? target[0] : cx, tz = target != null ? target[1] : cz;
        Map<Footprint.Rect, Material> extras = new HashMap<>();
        List<String> opt = new ArrayList<>(layout.optionalElements);
        Collections.shuffle(opt, k.rnd);
        for (String e : opt) {
            Footprint.Rect r = null;
            Material col = null;
            switch (e) {
                case "shed", "gazebo", "pond" -> {
                    List<Defs.ObjectDef> objs = new ArrayList<>();
                    for (Defs.ObjectDef o : TagMatcher.all(k.store.objects, k.tags)) if (o.category.equals(e)) objs.add(o);
                    if (objs.isEmpty()) { k.info.add("no " + e + " object for these tags: skipped"); continue; }
                    Defs.ObjectDef o = TagMatcher.weighted(objs, x -> x.weight, k.rnd);
                    boolean turn = o.canRotate && k.rnd.nextBoolean();
                    int w = turn ? o.footprintZ : o.footprintX, d = turn ? o.footprintX : o.footprintZ;
                    r = planner.farSpot(w, d, tx, tz, taken);
                    if (r == null && o.canRotate) r = planner.farSpot(d, w, tx, tz, taken);
                    col = e.equals("pond") ? Blocks.POND : Blocks.SHED;
                }
                case "garden" -> {
                    for (int[] sz : new int[][]{{9, 7}, {7, 7}, {7, 5}, {5, 5}, {5, 3}, {3, 3}}) {
                        r = planner.farSpot(sz[0], sz[1], tx, tz, taken);
                        if (r == null) r = planner.farSpot(sz[1], sz[0], tx, tz, taken);
                        if (r != null) break;
                    }
                    col = Blocks.GARDEN;
                }
                default -> k.warn.add("unknown optional element " + e);
            }
            if (r == null || col == null) continue;
            extras.put(r, col);
            taken.addAll(Planner.padded(r, 1));
        }

        // draw: one block above the terrain
        BlockState red = Blocks.state(Blocks.HOUSE.createBlockData()), orange = Blocks.state(Blocks.FRONT.createBlockData());
        Footprint.Ring ring = house.fp().ring();
        Set<Long> front = new HashSet<>();
        for (int i = 0; i < house.front().length(); i++) front.add(PlotPolygon.key(house.front().cellX(i), house.front().cellZ(i)));
        for (int[] c : ring.cells()) put(k, s, terrain, c[0], c[1], front.contains(PlotPolygon.key(c[0], c[1])) ? orange : red);
        for (Map.Entry<Footprint.Rect, Material> e : extras.entrySet()) {
            Footprint.Rect r = e.getKey();
            BlockState st = Blocks.state(e.getValue().createBlockData());
            for (int x = r.x0(); x <= r.x1(); x++)
                for (int z = r.z0(); z <= r.z1(); z++)
                    if (x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1()) put(k, s, terrain, x, z, st);
        }
        BlockState yellow = Blocks.state(Blocks.FENCE.createBlockData()), gold = Blocks.state(Blocks.GATE.createBlockData());
        Set<Long> gateSet = new HashSet<>();
        for (int[] g : gateCells) gateSet.add(PlotPolygon.key(g[0], g[1]));
        for (int[] c : fenceLine) put(k, s, terrain, c[0], c[1], gateSet.contains(PlotPolygon.key(c[0], c[1])) ? gold : yellow);

        Footprint.Rect hb = house.fp().bounds();
        k.info.add(String.format("layout %s, plot %d blocks; house %s %dx%d (%d blocks) facing %s; %s",
                layout.id, Math.round(area), house.fp().wings.size() == 1 ? "rectangle" : (house.fp().wings.size() + " wings"),
                hb.width(), hb.depth(), house.fp().area(), house.frontDir(), extras.isEmpty() ? "no extras" : extras.values().stream().map(m -> Blocks.OUTLINE.get(m)).toList()));
        if (gates.isEmpty()) k.warn.add("No GOLD_BLOCK gate: the house faces the plot's middle and the fence gets no gate.");
        return true;
    }

    /**
     * What the outline phase clears (Arag 11-15: *„logs, leaves, flowers, crop a replaceable_by_trees“*): the vanilla block tags
     * {@code logs}, {@code leaves}, {@code flowers}, {@code crops} and {@code replaceable_by_trees}, except water and the plants
     * that live in it (the last tag holds water, seagrass and tall seagrass: a pond or a stream in the plot stays).
     */
    public static boolean vegetation(Material m) {
        if (m == Material.WATER || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS || m == Material.KELP || m == Material.KELP_PLANT)
            return false;
        return org.bukkit.Tag.LOGS.isTagged(m) || org.bukkit.Tag.LEAVES.isTagged(m) || org.bukkit.Tag.FLOWERS.isTagged(m)
                || org.bukkit.Tag.CROPS.isTagged(m) || org.bukkit.Tag.REPLACEABLE_BY_TREES.isTagged(m);
    }

    /** Clears {@link #vegetation} above the ground of every cell of the plot (inside and on the fence line), up to the treetops. */
    private static int clearVegetation(Kit k, Surface s, Set<Long> inside, Set<Long> fence) throws WorldEditException {
        Set<Long> all = new HashSet<>(inside);
        all.addAll(fence);
        int n = 0;
        for (long c : all) {
            int x = PlotPolygon.kx(c), z = PlotPolygon.kz(c), g = s.y(x, z);
            if (g == Integer.MIN_VALUE) continue;
            int top = k.world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.WORLD_SURFACE);
            for (int y = g + 1; y <= top; y++)
                if (vegetation(k.world.getBlockAt(x, y, z).getType())) { k.clear(x, y, z); n++; }
        }
        return n;
    }

    private static void put(Kit k, Surface s, Map<Long, Integer> terrain, int x, int z, BlockState st) throws WorldEditException {
        Integer t = terrain.get(PlotPolygon.key(x, z));
        int y = t != null ? t : s.y(x, z);
        if (y == Integer.MIN_VALUE) return;
        k.set(x, y + 1, z, st);
    }

    /** The fence cell for a gate: the nearest one on a straight run (a gate in a stair-step of a slanted fence would not close it). */
    private static int[] gateCell(List<int[]> loop, int x, int z) {
        int n = loop.size();
        int[] straight = null, any = null;
        for (int i = 0; i < n; i++) {
            int[] c = loop.get(i), p = loop.get((i - 1 + n) % n), q = loop.get((i + 1) % n);
            boolean st = p[0] - c[0] == c[0] - q[0] && p[1] - c[1] == c[1] - q[1];
            if (any == null || dist(c, x, z) < dist(any, x, z)) any = c;
            if (st && (straight == null || dist(c, x, z) < dist(straight, x, z))) straight = c;
        }
        return straight != null && dist(straight, x, z) <= dist(any, x, z) + 16 ? straight : any;
    }

    private static int[] nearest(List<int[]> cells, int x, int z) {
        int[] best = cells.get(0);
        for (int[] c : cells) if (dist(c, x, z) < dist(best, x, z)) best = c;
        return best;
    }

    private static double dist(int[] c, double x, double z) {
        return (c[0] - x) * (c[0] - x) + (c[1] - z) * (c[1] - z);
    }

    private static List<int[]> concat(List<int[]> a, List<int[]> b) {
        List<int[]> o = new ArrayList<>(a);
        o.addAll(b);
        return o;
    }
}
