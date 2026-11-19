package cz.lollywood.townbuilder.phase;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import cz.lollywood.townbuilder.config.Defs;
import cz.lollywood.townbuilder.config.TagMatcher;
import cz.lollywood.townbuilder.geom.Dir;
import cz.lollywood.townbuilder.geom.PlotPolygon;
import cz.lollywood.townbuilder.world.Blocks;
import cz.lollywood.townbuilder.world.Paster;
import cz.lollywood.townbuilder.world.Surface;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.*;

/** Phase 3: trees, bushes and clutter on the free natural ground inside the fence. */
public final class FoliagePhase {
    private FoliagePhase() {}

    public static boolean run(Kit k) throws WorldEditException {
        Surface s = k.surface();
        Defs.PlotLayout layout = TagMatcher.best(k.store.layouts, k.tags, k.rnd);
        double density = layout != null ? layout.foliageDensity : 0.1;
        List<Defs.ObjectDef> objs = new ArrayList<>();
        for (Defs.ObjectDef o : TagMatcher.all(k.store.objects, k.tags))
            if (o.category.equals("foliage") || o.category.equals("clutter")) objs.add(o);
        if (objs.isEmpty()) {
            k.warn.add("No foliage or clutter objects match " + k.tags + ".");
            return false;
        }
        // the fence encloses the plot: flood from the selection's edge, stopped by fences, walls, gates and the blocks of the
        // matching fence preset and its gate schematic (a gate's posts may be plain stone)
        Set<Material> fenceBlocks = new HashSet<>();
        Defs.FencePreset f = TagMatcher.best(k.store.fences, k.tags, k.rnd);
        if (f != null) {
            for (String b : List.of(f.postBlock, f.postCapBlock == null ? "" : f.postCapBlock, f.fillBlock, f.gateBlock))
                if (b != null && !b.isBlank()) fenceBlocks.add(Blocks.data(b).getMaterial());
            if (f.gateSchematic != null && !f.gateSchematic.isBlank()) {
                Clipboard gate = BuildPhase.load(k, k.store.objectDir(), f.gateSchematic.replaceFirst("\\.schem(atic)?$", ""));
                if (gate != null)
                    for (BlockVector3 p : gate.getRegion()) {
                        Material m = BukkitAdapter.adapt(gate.getBlock(p).getBlockType());
                        if (m != null && !m.isAir()) fenceBlocks.add(m);
                    }
            }
        }
        Set<Long> barrier = new HashSet<>();
        for (int x = k.x0; x <= k.x1; x++)
            for (int z = k.z0; z <= k.z1; z++) {
                int y = s.y(x, z);
                // the scan's top block of a fence column IS the fence (or its cap): look at it and just below
                for (int h = 0; h <= 3 && y != Integer.MIN_VALUE; h++) {
                    Material m = k.world.getBlockAt(x, y - h, z).getType();
                    if (Tag.FENCES.isTagged(m) || Tag.WALLS.isTagged(m) || Tag.FENCE_GATES.isTagged(m) || fenceBlocks.contains(m)) {
                        barrier.add(PlotPolygon.key(x, z));
                        break;
                    }
                }
            }
        Set<Long> outside = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = k.x0; x <= k.x1; x++) { q.add(new int[]{x, k.z0}); q.add(new int[]{x, k.z1}); }
        for (int z = k.z0; z <= k.z1; z++) { q.add(new int[]{k.x0, z}); q.add(new int[]{k.x1, z}); }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] < k.x0 || c[0] > k.x1 || c[1] < k.z0 || c[1] > k.z1) continue;
            long key = PlotPolygon.key(c[0], c[1]);
            if (barrier.contains(key) || !outside.add(key)) continue;
            for (Dir d : Dir.values()) q.add(new int[]{c[0] + d.dx, c[1] + d.dz});
        }
        boolean fenced = outside.size() + barrier.size() < (long) (k.x1 - k.x0 + 1) * (k.z1 - k.z0 + 1);
        if (!fenced) k.warn.add("no closed fence found: the whole selection is planted");
        // free cells: natural ground with open air above, one cell away from anything built
        Set<Long> freeCells = new HashSet<>();
        for (int x = k.x0; x <= k.x1; x++)
            for (int z = k.z0; z <= k.z1; z++) {
                long key = PlotPolygon.key(x, z);
                if (fenced && (outside.contains(key) || barrier.contains(key))) continue;
                if (open(k, s, x, z)) freeCells.add(key);
            }
        Set<Long> usable = new HashSet<>();
        for (long c : freeCells) {
            boolean ok = true;
            for (Dir d : Dir.values()) if (!freeCells.contains(PlotPolygon.key(PlotPolygon.kx(c) + d.dx, PlotPolygon.kz(c) + d.dz))) ok = false;
            if (ok) usable.add(c);
        }
        List<Long> order = new ArrayList<>(usable);
        Collections.shuffle(order, k.rnd);
        Set<Long> taken = new HashSet<>();
        int placed = 0;
        for (long c : order) {
            Defs.ObjectDef o = TagMatcher.weighted(objs, x -> x.weight, k.rnd);
            if (k.rnd.nextDouble() >= density / Math.max(1, o.footprintX * o.footprintZ)) continue;
            int turns = o.canRotate ? k.rnd.nextInt(4) : 0;
            int w = turns % 2 == 0 ? o.footprintX : o.footprintZ, d = turns % 2 == 0 ? o.footprintZ : o.footprintX;
            int x0 = PlotPolygon.kx(c), z0 = PlotPolygon.kz(c);
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            boolean ok = true;
            for (int x = x0; x < x0 + w && ok; x++)
                for (int z = z0; z < z0 + d && ok; z++) {
                    long key = PlotPolygon.key(x, z);
                    if (!usable.contains(key) || taken.contains(key)) { ok = false; break; }
                    lo = Math.min(lo, s.y(x, z)); hi = Math.max(hi, s.y(x, z));
                }
            if (!ok || hi - lo > o.maxSlope) continue;
            Clipboard cb = BuildPhase.load(k, k.store.objectDir(), o.name);
            if (cb == null) continue;
            Paster.paste(k.es(), cb, turns, BlockVector3.at(x0, lo + 1 + o.yOffset, z0), true);
            for (int x = x0 - 1; x <= x0 + w; x++) for (int z = z0 - 1; z <= z0 + d; z++) taken.add(PlotPolygon.key(x, z));
            placed++;
        }
        k.info.add("foliage: " + placed + " objects on " + usable.size() + " free blocks (density " + density + ")");
        return true;
    }

    private static boolean open(Kit k, Surface s, int x, int z) {
        int y = s.y(x, z);
        if (y == Integer.MIN_VALUE) return false;
        Material top = s.type(x, z);
        if (top == null || !Blocks.isGround(top)) return false;
        for (int h = 1; h <= 3; h++) {
            Material m = k.world.getBlockAt(x, y + h, z).getType();
            if (!m.isAir() && !Tag.REPLACEABLE.isTagged(m)) return false;
        }
        return true;
    }
}
