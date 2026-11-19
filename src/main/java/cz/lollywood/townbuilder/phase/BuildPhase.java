package cz.lollywood.townbuilder.phase;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import cz.lollywood.townbuilder.config.Defs;
import cz.lollywood.townbuilder.config.TagMatcher;
import cz.lollywood.townbuilder.geom.Dir;
import cz.lollywood.townbuilder.geom.Footprint;
import cz.lollywood.townbuilder.geom.PlotPolygon;
import cz.lollywood.townbuilder.geom.SegmentFiller;
import cz.lollywood.townbuilder.world.Blocks;
import cz.lollywood.townbuilder.world.Paster;
import cz.lollywood.townbuilder.world.Surface;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Phase 2: the concrete outlines → the house (foundation, walls by floors, roof per wing), objects, the garden, the fence. */
public final class BuildPhase {
    private BuildPhase() {}

    public static boolean run(Kit k) throws WorldEditException {
        Surface s = k.surface();
        Map<Material, Set<Long>> col = new HashMap<>();
        Map<Long, Integer> ground = new HashMap<>();
        for (int x = k.x0; x <= k.x1; x++)
            for (int z = k.z0; z <= k.z1; z++) {
                Material m = s.type(x, z);
                if (m == null || !Blocks.OUTLINE.containsKey(m)) continue;
                long key = PlotPolygon.key(x, z);
                col.computeIfAbsent(m, q -> new HashSet<>()).add(key);
                ground.put(key, s.y(x, z) - 1);
                k.set(x, s.y(x, z), z, Blocks.air());
            }
        if (col.isEmpty()) {
            k.warn.add("No outlines found in the selection: run /plot outline first.");
            return false;
        }
        Defs.PlotLayout layout = TagMatcher.best(k.store.layouts, k.tags, k.rnd);
        Defs.BuildingStyle style = TagMatcher.best(k.store.styles, k.tags, k.rnd);
        if (style == null) { style = new Defs.BuildingStyle(); k.warn.add("no building style matches the tags: the defaults are used"); }

        Set<Long> ring = new HashSet<>(col.getOrDefault(Blocks.HOUSE, Set.of()));
        ring.addAll(col.getOrDefault(Blocks.FRONT, Set.of()));
        if (!ring.isEmpty()) house(k, s, ring, col.getOrDefault(Blocks.FRONT, Set.of()), ground, layout, style);
        for (Material m : List.of(Blocks.SHED, Blocks.POND))
            for (Footprint.Rect r : rects(col.getOrDefault(m, Set.of()))) object(k, s, r, m == Blocks.POND ? Set.of("pond") : Set.of("shed", "gazebo"), ground);
        for (Footprint.Rect r : rects(col.getOrDefault(Blocks.GARDEN, Set.of()))) garden(k, s, r, ground, style);
        Set<Long> fence = new HashSet<>(col.getOrDefault(Blocks.FENCE, Set.of()));
        fence.addAll(col.getOrDefault(Blocks.GATE, Set.of()));
        if (!fence.isEmpty()) fence(k, fence, col.getOrDefault(Blocks.GATE, Set.of()), ground);
        return true;
    }

    // ------------------------------------------------------------------ the house
    private static void house(Kit k, Surface s, Set<Long> ringCells, Set<Long> frontCells, Map<Long, Integer> ground,
                              Defs.PlotLayout layout, Defs.BuildingStyle style) throws WorldEditException {
        Set<Long> cells = fillRing(ringCells);
        List<Footprint.Rect> wings = decompose(cells);
        Footprint fp = new Footprint(wings);
        Footprint.Ring ring;
        int cw = cornerWidth(k), cwi = width(k, "inner_corner");
        try {
            ring = Footprint.trimmed(fp.ring(), cw, cwi);
        } catch (IllegalStateException e) {
            k.warn.add("The house outline is not a clean rectangle, L or T, or a wall is too short for the corners (" + e.getMessage() + "): fix the red line.");
            return;
        }
        Footprint.Segment front = null;
        int best = 0;
        for (Footprint.Segment sg : ring.segments()) {
            int n = 0;
            for (int i = 0; i < sg.length(); i++) if (frontCells.contains(PlotPolygon.key(sg.cellX(i), sg.cellZ(i)))) n++;
            if (n > best) { best = n; front = sg; }
        }
        // the terrain under the house, the floor's level, the foundation
        int floorY = Integer.MIN_VALUE;
        Map<Long, Integer> t = new HashMap<>();
        for (long c : cells) {
            int x = PlotPolygon.kx(c), z = PlotPolygon.kz(c);
            int g = ground.containsKey(c) ? ground.get(c) : s.y(x, z);
            if (g == Integer.MIN_VALUE) g = k.y0;
            t.put(c, g);
            floorY = Math.max(floorY, g + 1);
        }
        int[] stories = layout != null ? layout.storiesRange : new int[]{1, 1};
        int nStories = stories[0] + (stories.length > 1 && stories[1] > stories[0] ? k.rnd.nextInt(stories[1] - stories[0] + 1) : 0);

        Map<String, List<Defs.WallModule>> byFloor = new HashMap<>();
        Map<String, Integer> height = new HashMap<>();
        for (String fl : List.of("ground", "upper")) {
            List<Defs.WallModule> ms = new ArrayList<>();
            for (Defs.WallModule w : TagMatcher.all(k.store.walls, k.tags)) if (w.allowedFloors.contains(fl)) ms.add(w);
            Map<Integer, Integer> hc = new HashMap<>();
            for (Defs.WallModule w : ms) if (!w.isCorner()) hc.merge(w.height, 1, Integer::sum);
            int h = hc.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(4);
            List<Defs.WallModule> same = new ArrayList<>();
            for (Defs.WallModule w : ms) if (w.height == h) same.add(w);
            byFloor.put(fl, same);
            height.put(fl, h);
        }
        if (byFloor.get("ground").stream().noneMatch(w -> !w.isCorner())) {
            k.warn.add("No ground-floor wall modules match " + k.tags + ".");
            return;
        }
        int total = 0;
        for (int i = 0; i < nStories; i++) total += height.get(i == 0 ? "ground" : "upper");
        Footprint.Rect bb = fp.bounds();
        int roofTop = floorY + total + Math.max(bb.width(), bb.depth()) / 2 + 3;
        BlockState foundation = Blocks.state(style.foundationBlock), floor = Blocks.state(style.floorBlock), air = Blocks.air();
        int o = Math.max(0, style.roofOverhang);
        // clear the space (trees, grass) over the plan and its eaves, then the foundation and the ground floor's floor
        for (int x = bb.x0() - o; x <= bb.x1() + o; x++)
            for (int z = bb.z0() - o; z <= bb.z1() + o; z++) {
                boolean in = fp.has(x, z);
                int g = in ? t.get(PlotPolygon.key(x, z)) : floorY + total - 1;
                for (int y = Math.max(g + 1, in ? g + 1 : floorY + total - 1); y <= roofTop; y++)
                    if (!k.world.getBlockAt(x, y, z).getType().isAir()) k.clear(x, y, z);
                // a tree taller than the roof: its top goes too (0.7.0: a fancy oak's crown was left floating over the roof)
                int top = k.world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.WORLD_SURFACE);
                for (int y = roofTop + 1; y <= top; y++)
                    if (OutlinePhase.vegetation(k.world.getBlockAt(x, y, z).getType())) k.clear(x, y, z);
            }
        for (long c : cells) {
            int x = PlotPolygon.kx(c), z = PlotPolygon.kz(c);
            for (int y = t.get(c) + 1; y <= floorY - 1; y++) k.set(x, y, z, foundation);
            if (!fp.onRing(x, z)) k.set(x, floorY - 1, z, floor);
        }
        Map<String, Integer> used = new HashMap<>();
        Set<Long> covered = new HashSet<>();
        boolean cellarDoor = basement(k, fp, ring, t, s, floorY, used, covered, style);
        if (!cellarDoor) basementDoor(k, fp, ring, t, ground, s, floorY, style, covered);

        // the walls, floor by floor
        int y = floorY;
        List<Footprint.Segment> segs = ring.segments();
        List<Footprint.Corner> corners = ring.corners();
        int fi = front == null ? -1 : segs.indexOf(front), ns = segs.size();
        for (int i = 0; i < nStories; i++) {
            String fl = i == 0 ? "ground" : "upper";
            List<Defs.WallModule> ms = byFloor.get(fl).isEmpty() ? byFloor.get("ground") : byFloor.get(fl);
            if (i > 0)
                for (long c : cells) if (!fp.onRing(PlotPolygon.kx(c), PlotPolygon.kz(c))) k.set(PlotPolygon.kx(c), y - 1, PlotPolygon.kz(c), floor);
            // an overhang or arcade floor (0.7.0): the deepest overhang_depth among this floor's modules sets the front row's inset
            int inset = 0;
            for (Defs.WallModule w : ms) if (!w.isCorner()) inset = Math.max(inset, w.overhangDepth);
            if (fi < 0) inset = 0;
            // segment fi runs from corner fi to corner fi+1; the side wall before it ends at corner fi, the one after starts at fi+1
            boolean inStart = inset > 0 && corners.get(fi).outer(), inEnd = inset > 0 && corners.get((fi + 1) % ns).outer();
            if (inset > 0 && (!inStart || !inEnd))
                k.warn.add("the front wall ends at an inner corner: the overhang's side walls are shortened only at outer corners");
            for (int ci = 0; ci < corners.size(); ci++) {
                Footprint.Corner c = corners.get(ci);
                int[] shift = (inStart && ci == fi) || (inEnd && ci == (fi + 1) % ns) ? new int[]{-front.normal().dx * inset, -front.normal().dz * inset} : new int[]{0, 0};
                corner(k, c, ms, y, c.outer() ? cw : cwi, shift);
            }
            for (int si = 0; si < ns; si++) {
                Footprint.Segment sg = segs.get(si);
                boolean isFront = si == fi;
                int trimStart = inStart || inEnd ? (si == (fi + 1) % ns && inEnd ? inset : 0) : 0;
                int trimEnd = si == (fi - 1 + ns) % ns && inStart ? inset : 0;
                if (sg.length() - trimStart - trimEnd < 0) { k.warn.add("a side wall is shorter than the overhang " + inset + ": left empty"); continue; }
                Map<BlockVector3, String> door = segment(k, sg, ms, y, i == 0 && isFront, isFront, isFront ? inset : 0, trimStart, trimEnd, used);
                if (door != null) steps(k, s, sg, door, y, style);
            }
            if (inset > 0) k.info.add("floor " + (i + 1) + ": the front row stands " + inset + " block(s) in (overhang_depth), its side walls shortened");
            y += height.get(fl);
        }
        roof(k, fp, y, style);
        k.info.add(String.format("house: %d floor(s) on %d blocks, %d wing(s), floor at y %d, roof from y %d", nStories, fp.area(), wings.size(), floorY, y));
    }

    /**
     * How wide the outer corners are (0.5.0): the most common width of the ground floor's outer corner modules for these tags
     * (their {@code length}; 1 when there are none). The planner and the build both use it, so a side = 2 corners + modules.
     */
    static int cornerWidth(Kit k) { return width(k, "outer_corner"); }

    /** The same for either kind of corner ({@code outer_corner} or {@code inner_corner}, 0.6.0). */
    static int width(Kit k, String type) {
        Map<Integer, Integer> n = new HashMap<>();
        for (Defs.WallModule w : TagMatcher.all(k.store.walls, k.tags))
            if (w.moduleType.equals(type) && w.allowedFloors.contains("ground")) n.merge(Math.max(1, w.length), 1, Integer::sum);
        return n.entrySet().stream().max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(1);
    }

    private static void corner(Kit k, Footprint.Corner c, List<Defs.WallModule> ms, int y, int cw, int[] shift) throws WorldEditException {
        String type = c.outer() ? "outer_corner" : "inner_corner";
        List<Defs.WallModule> cs = new ArrayList<>();
        for (Defs.WallModule w : ms) if (w.moduleType.equals(type) && Math.max(1, w.length) == cw) cs.add(w);
        if (cs.isEmpty()) {
            k.warn.add("no " + type + " module: the corner at " + c.x() + "," + c.z() + " stays empty");
            return;
        }
        Defs.WallModule w = TagMatcher.weighted(cs, q -> q.weight, k.rnd);
        Clipboard cb = clip(k, w);
        if (cb == null) return;
        int turns = Paster.turns(Dir.parse(w.frontFacing), c.face());
        // 0.8.0 (Arag 11-17): an outer corner with an offset sticks that many blocks out of both walls
        int[] m = Footprint.cornerMin(c, cw, c.outer() ? w.offset : 0);
        Paster.paste(k.es(), cb, turns, BlockVector3.at(m[0] + shift[0], y, m[1] + shift[1]), true);
    }

    /**
     * @param front     this segment gets the door (the ground floor's front wall)
     * @param frontWall the front wall on any floor: only it may use overhang modules
     * @param inset     how far in the row stands (overhang_depth, 0 = on the wall line)
     * @param trimStart cells left out at the segment's start (a side wall beside an overhang), {@code trimEnd} at its end
     * @return where the door module's blocks landed, or null when this segment got no door
     */
    private static Map<BlockVector3, String> segment(Kit k, Footprint.Segment sg, List<Defs.WallModule> ms, int y, boolean front, boolean frontWall,
                                                     int inset, int trimStart, int trimEnd, Map<String, Integer> used) throws WorldEditException {
        int length = sg.length() - trimStart - trimEnd;
        if (length <= 0) return null;
        boolean withDoor = false;
        List<Defs.WallModule> walls = new ArrayList<>(), doors = new ArrayList<>();
        for (Defs.WallModule w : ms) {
            if (w.isCorner()) continue;
            if (w.hasDoor) { if (frontWall || w.overhangDepth == 0) doors.add(w); }
            else if (inset > 0 ? w.overhangDepth == inset : w.overhangDepth == 0) walls.add(w);
        }
        if (walls.isEmpty() && inset > 0) {
            for (Defs.WallModule w : ms) if (!w.isCorner() && !w.hasDoor && w.overhangDepth == 0) walls.add(w);
            k.warn.add("no plain overhang module of depth " + inset + ": the front row uses plain walls set " + inset + " in");
        }
        boolean[] reach = SegmentFiller.reachable(walls.stream().map(w -> w.length).toList(), length);
        Defs.WallModule filler = filler(k, walls);
        List<Defs.WallModule> row = new ArrayList<>();
        if (front && !doors.isEmpty()) {
            List<Defs.WallModule> fitting = new ArrayList<>();
            for (Defs.WallModule d : doors) if (SegmentFiller.doorStart(length, d.length, reach) >= 0) fitting.add(d);
            Defs.WallModule door = null;
            int p = -1;
            if (!fitting.isEmpty()) {
                door = TagMatcher.weighted(fitting, q -> q.weight, k.rnd);
                p = SegmentFiller.doorStart(length, door.length, reach);
            } else if (filler != null) {
                // no exact arrangement (an outline moved by hand): the door in the middle, filler closes both sides
                List<Defs.WallModule> fit = new ArrayList<>();
                for (Defs.WallModule d : doors) if (d.length <= length) fit.add(d);
                if (!fit.isEmpty()) {
                    door = TagMatcher.weighted(fit, q -> q.weight, k.rnd);
                    p = (length - door.length) / 2;
                }
            }
            if (door != null) {
                List<Defs.WallModule> left = fillAny(k, p, walls, used, filler), right = fillAny(k, length - p - door.length, walls, used, filler);
                if (left != null && right != null) {
                    row.addAll(left); row.add(door); row.addAll(right);
                    used.merge(door.name, 1, Integer::sum);
                    withDoor = true;
                }
            } else k.warn.add("no door module fits the front wall of length " + length);
        }
        if (row.isEmpty()) {
            List<Defs.WallModule> r = fillAny(k, length, walls, used, filler);
            if (r == null) {
                k.warn.add("a wall of length " + length + " cannot be made from the modules' lengths " + walls.stream().map(w -> w.length).distinct().toList()
                        + " and no plain module gives a filler column");
                return null;
            }
            row = r;
        }
        Map<BlockVector3, String> landed = placeRow(k, sg, row, trimStart, y, inset);
        return withDoor ? landed : null;
    }

    /**
     * Steps from a door down to the ground outside, when the door stands above it (a house on a slope). {@code mods} = where the
     * door module's blocks landed (from the clipboard, see Paster.landing): the door block at the door's level, then stairs
     * outward and down from the first cell in front of it that the module leaves empty, the foundation block under each.
     */
    private static void steps(Kit k, Surface s, Footprint.Segment sg, Map<BlockVector3, String> mods, int y, Defs.BuildingStyle st) throws WorldEditException {
        Dir n = sg.normal();
        boolean ns = n == Dir.NORTH || n == Dir.SOUTH;
        int dx = Integer.MIN_VALUE, dz = 0;
        for (Map.Entry<BlockVector3, String> e : mods.entrySet()) {
            String id = e.getValue();
            if (e.getKey().y() != y || !id.endsWith("_door") || id.endsWith("trapdoor")) continue;
            // its column on the wall line
            dx = ns ? e.getKey().x() : sg.cellX(0);
            dz = ns ? sg.cellZ(0) : e.getKey().z();
            break;
        }
        if (dx == Integer.MIN_VALUE) { k.warn.add("no door block found in the door module at y " + y + ": no steps"); return; }
        BlockState stair = Blocks.state(Blocks.facing(st.entranceStairs, n.opposite())), fill = Blocks.state(st.foundationBlock);
        int d = 1;
        while (d <= 4 && mods.containsKey(BlockVector3.at(dx + n.dx * d, y, dz + n.dz * d))) d++;   // past the module's facade
        int sy = y - 1, made = 0;
        for (; d <= 14; d++, sy--) {
            int cx = dx + n.dx * d, cz = dz + n.dz * d, g = s.y(cx, cz);
            if (g == Integer.MIN_VALUE || sy <= g) break;
            if (!k.freeForRoof(cx, sy, cz)) {
                Material m = k.world.getBlockAt(cx, sy, cz).getType();
                if (!Tag.REPLACEABLE.isTagged(m)) { k.warn.add("the steps to the door at " + dx + "," + dz + " are blocked"); break; }
            }
            k.set(cx, sy, cz, stair);
            for (int yy = g + 1; yy < sy; yy++) k.set(cx, yy, cz, fill);
            made++;
        }
        if (made > 0) k.info.add(made + " step(s) down from the door at " + dx + "," + y + "," + dz);
    }

    /**
     * Pastes a row of wall modules along a segment from its cell {@code at}, the back of each on the wall line.
     * @return where the door modules' blocks landed (empty when the row has none)
     */
    private static Map<BlockVector3, String> placeRow(Kit k, Footprint.Segment sg, List<Defs.WallModule> row, int at, int y, int inset) throws WorldEditException {
        Map<BlockVector3, String> doors = new HashMap<>();
        for (Defs.WallModule w : row) {
            Clipboard cb = clip(k, w);
            if (cb != null) {
                int turns = Paster.turns(Dir.parse(w.frontFacing), sg.normal());
                int[] size = Paster.size(cb, turns);
                int depth = (sg.normal() == Dir.NORTH || sg.normal() == Dir.SOUTH) ? size[2] : size[0];
                int along = (sg.normal() == Dir.NORTH || sg.normal() == Dir.SOUTH) ? size[0] : size[2];
                if (along != w.length) k.warn.add(w.name + ".schem is " + along + " long, its JSON says " + w.length);
                int ax = sg.cellX(at), az = sg.cellZ(at), bx = sg.cellX(at + w.length - 1), bz = sg.cellZ(at + w.length - 1);
                int mx = Math.min(ax, bx), mz = Math.min(az, bz);
                // the back of the module stands on the wall line; extra depth sticks out of the house
                if (sg.normal() == Dir.NORTH) mz -= depth - 1;
                if (sg.normal() == Dir.WEST) mx -= depth - 1;
                // an overhang row (0.7.0): everything moves {@code inset} blocks into the house
                mx -= sg.normal().dx * inset;
                mz -= sg.normal().dz * inset;
                Paster.paste(k.es(), cb, turns, BlockVector3.at(mx, y, mz), true);
                if (w.hasDoor) doors.putAll(Paster.landing(cb, turns, BlockVector3.at(mx, y, mz)));
                decorate(k, sg, w, at, y, inset);
            }
            at += w.length;
        }
        return doors;
    }

    /**
     * 0.8.0 (Arag 11-17: *"Ideální by bylo mít systém, kde si vytvořím zvlášť zeď a zvlášť dekoraci a jsem schopný je mixovat
     * dohromady."*): a module with {@code decoration: true} gets a decoration (schematics/decorations) of these tags, at most
     * {@code decoration_max_width} wide (default its length), of one of its {@code decoration_flavours} if it lists any, chosen by
     * weight, centred on the module, its back row on the first row outside the wall line ({@code depth_offset} further out), its
     * foot {@code y_offset} blocks above the module's foot; air in it is skipped.
     */
    private static void decorate(Kit k, Footprint.Segment sg, Defs.WallModule w, int at, int y, int inset) throws WorldEditException {
        if (!w.decoration) return;
        if (w.decorationChance < 1 && k.rnd.nextDouble() >= w.decorationChance) return;
        int maxW = w.decorationMaxWidth > 0 ? Math.min(w.decorationMaxWidth, w.length) : w.length;
        Set<String> want = new HashSet<>();
        for (String f : w.decorationFlavours) want.add(f.trim().toLowerCase(java.util.Locale.ROOT));
        List<Defs.Decoration> fit = new ArrayList<>();
        for (Defs.Decoration d : TagMatcher.all(k.store.decorations, k.tags)) {
            if (d.width > maxW) continue;
            if (!want.isEmpty() && d.flavours.stream().noneMatch(f -> want.contains(f.trim().toLowerCase(java.util.Locale.ROOT)))) continue;
            fit.add(d);
        }
        if (fit.isEmpty()) { k.decorationMissing.add(w.name + " (" + maxW + " wide" + (want.isEmpty() ? "" : ", " + want) + ")"); return; }
        Defs.Decoration d = TagMatcher.weighted(fit, q -> q.weight, k.rnd);
        Clipboard cb = load(k, k.store.decorationDir(), d.name);
        if (cb == null) return;
        int turns = Paster.turns(Dir.parse(d.frontFacing), sg.normal());
        int[] size = Paster.size(cb, turns);
        boolean ns = sg.normal() == Dir.NORTH || sg.normal() == Dir.SOUTH;
        int depth = ns ? size[2] : size[0], along = ns ? size[0] : size[2];
        if (along != d.width) k.warn.add("decoration " + d.name + ".schem is " + along + " wide, its JSON says " + d.width);
        int start = at + Math.max(0, (w.length - along) / 2);
        int ax = sg.cellX(start), az = sg.cellZ(start), bx = sg.cellX(start + along - 1), bz = sg.cellZ(start + along - 1);
        int mx = Math.min(ax, bx), mz = Math.min(az, bz);
        // its back row: the first row outside the wall line, depth_offset further out, the overhang row's inset back in
        int out = 1 + d.depthOffset - inset;
        if (ns) {
            mz = sg.cellZ(0) + sg.normal().dz * out;
            if (sg.normal() == Dir.NORTH) mz -= depth - 1;
        } else {
            mx = sg.cellX(0) + sg.normal().dx * out;
            if (sg.normal() == Dir.WEST) mx -= depth - 1;
        }
        Paster.paste(k.es(), cb, turns, BlockVector3.at(mx, y + d.yOffset, mz), true);
        k.decorations++;
    }

    /**
     * The basement floor: where the foundation stands out of the ground at least one basement module high, that band right under
     * the ground floor is built from the {@code basement} modules (corners too, where both sides are that high) and the
     * foundation behind it becomes a cellar room. One basement door module, if any, goes on the most exposed run.
     * Returns true when a door module was placed (then no door is carved). {@code covered} gets the ring cells it built.
     */
    private static boolean basement(Kit k, Footprint fp, Footprint.Ring ring, Map<Long, Integer> t, Surface s, int floorY,
                                    Map<String, Integer> used, Set<Long> covered, Defs.BuildingStyle style) throws WorldEditException {
        List<Defs.WallModule> all = new ArrayList<>();
        for (Defs.WallModule w : TagMatcher.all(k.store.walls, k.tags)) if (w.allowedFloors.contains("basement")) all.add(w);
        Map<Integer, Integer> hc = new HashMap<>();
        for (Defs.WallModule w : all) if (!w.isCorner()) hc.merge(w.height, 1, Integer::sum);
        if (hc.isEmpty()) return false;
        int hb = hc.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(3);
        List<Defs.WallModule> walls = new ArrayList<>(), doors = new ArrayList<>(), corners = new ArrayList<>();
        for (Defs.WallModule w : all) {
            if (w.height != hb) continue;
            (w.isCorner() ? corners : w.hasDoor ? doors : walls).add(w);
        }
        if (walls.isEmpty()) return false;
        int yb = floorY - hb;
        boolean[] reach = SegmentFiller.reachable(walls.stream().map(w -> w.length).toList(), 4096);
        record Run(Footprint.Segment sg, int start, int len, int exposure) {}
        List<Run> runs = new ArrayList<>();
        for (Footprint.Segment sg : ring.segments()) {
            int a = -1, low = Integer.MAX_VALUE;
            for (int i = 0; i <= sg.length(); i++) {
                boolean ok = false;
                if (i < sg.length()) {
                    int g = s.y(sg.cellX(i) + sg.normal().dx, sg.cellZ(i) + sg.normal().dz);
                    ok = g != Integer.MIN_VALUE && g + 1 <= yb;
                    if (ok) low = Math.min(low, floorY - 1 - g);
                }
                if (ok && a < 0) a = i;
                if (!ok && a >= 0) {
                    // the longest length the modules can make, centred in the run
                    int len = i - a;
                    while (len > 0 && !reach[len]) len--;
                    if (len > 0) runs.add(new Run(sg, a + (i - a - len) / 2, len, low));
                    a = -1; low = Integer.MAX_VALUE;
                }
            }
        }
        if (runs.isEmpty()) return false;
        Run doorRun = null;
        Defs.WallModule door = null;
        if (!doors.isEmpty()) {
            runs.sort((p, q) -> Integer.compare(q.exposure, p.exposure));
            for (Run r : runs) {
                List<Defs.WallModule> fitting = new ArrayList<>();
                for (Defs.WallModule d : doors) if (SegmentFiller.doorStart(r.len, d.length, reach) >= 0) fitting.add(d);
                if (!fitting.isEmpty()) { doorRun = r; door = TagMatcher.weighted(fitting, q -> q.weight, k.rnd); break; }
            }
        }
        int built = 0;
        for (Run r : runs) {
            List<Defs.WallModule> row = null;
            if (r == doorRun) {
                int p = SegmentFiller.doorStart(r.len, door.length, reach);
                List<Defs.WallModule> left = fill(k, p, walls, used), right = fill(k, r.len - p - door.length, walls, used);
                if (left != null && right != null) {
                    row = new ArrayList<>(left); row.add(door); row.addAll(right);
                    used.merge(door.name, 1, Integer::sum);
                } else door = null;
            }
            if (row == null) row = fill(k, r.len, walls, used);
            if (row == null) continue;
            Map<BlockVector3, String> landed = placeRow(k, r.sg, row, r.start, yb, 0);
            if (r == doorRun && door != null) steps(k, s, r.sg, landed, yb, style);
            for (int i = r.start; i < r.start + r.len; i++) covered.add(PlotPolygon.key(r.sg.cellX(i), r.sg.cellZ(i)));
            built += r.len;
        }
        // corners: where every outside cell next to the corner is low enough
        int cornersBuilt = 0;
        for (Footprint.Corner c : ring.corners()) {
            String type = c.outer() ? "outer_corner" : "inner_corner";
            List<Defs.WallModule> cs = new ArrayList<>();
            int cw = width(k, type);
            for (Defs.WallModule w : corners) if (w.moduleType.equals(type) && Math.max(1, w.length) == cw) cs.add(w);
            if (cs.isEmpty()) continue;
            Dir n1 = c.in().ccw(), n2 = c.out().ccw();
            boolean ok = true;
            for (int[] d : new int[][]{{n1.dx, n1.dz}, {n2.dx, n2.dz}, {n1.dx + n2.dx, n1.dz + n2.dz}}) {
                int ox = c.x() + d[0], oz = c.z() + d[1];
                if (fp.has(ox, oz)) continue;
                int g = s.y(ox, oz);
                if (g == Integer.MIN_VALUE || g + 1 > yb) ok = false;
            }
            if (!ok) continue;
            Defs.WallModule w = TagMatcher.weighted(cs, q -> q.weight, k.rnd);
            Clipboard cb = clip(k, w);
            if (cb == null) continue;
            int[] m = Footprint.cornerMin(c, cw, c.outer() ? w.offset : 0);
            Paster.paste(k.es(), cb, Paster.turns(Dir.parse(w.frontFacing), c.face()), BlockVector3.at(m[0], yb, m[1]), true);
            covered.add(PlotPolygon.key(c.x(), c.z()));
            cornersBuilt++;
        }
        if (built == 0) return false;
        // the cellar: the foundation under the floor, inside the ring, from the band's foot up (never into the ground)
        for (long c : fp.cells()) {
            int cx = PlotPolygon.kx(c), cz = PlotPolygon.kz(c);
            if (fp.onRing(cx, cz)) continue;
            for (int yy = yb; yy <= floorY - 2; yy++) if (yy > t.get(c)) k.set(cx, yy, cz, Blocks.air());
        }
        k.info.add("basement: " + built + " blocks of basement wall and " + cornersBuilt + " corner(s), " + hb + " high"
                + (door != null ? ", the cellar door " + door.name : ""));
        return door != null;
    }

    /**
     * Fills {@code len} with modules; when their lengths cannot make it exactly (an outline moved by hand), the longest length they
     * can make, centred, and FILLER columns at both ends for the rest (a slice of a plain module, see {@link #filler}); null only
     * when there is no filler either.
     */
    private static List<Defs.WallModule> fillAny(Kit k, int len, List<Defs.WallModule> walls, Map<String, Integer> used, Defs.WallModule filler) {
        if (len <= 0) return new ArrayList<>();
        List<Defs.WallModule> r = fill(k, len, walls, used);
        if (r != null || filler == null) return r;
        boolean[] reach = SegmentFiller.reachable(walls.stream().map(w -> w.length).toList(), len);
        int best = 0;
        for (int n = len; n > 0; n--) if (reach[n]) { best = n; break; }
        List<Defs.WallModule> core = best > 0 ? fill(k, best, walls, used) : new ArrayList<>();
        if (core == null) { core = new ArrayList<>(); best = 0; }
        int gap = len - best, left = gap / 2;
        List<Defs.WallModule> out = new ArrayList<>();
        for (int i = 0; i < left; i++) out.add(filler);
        out.addAll(core);
        for (int i = 0; i < gap - left; i++) out.add(filler);
        k.fillerColumns += gap;
        return out;
    }

    /**
     * A one-block wall piece cut from the middle of the shortest PLAIN module (no door, no window if there is such a one), so a wall
     * of any length can be closed; cached per run. Null when no wall module has a schematic. 0.8.0: the module's
     * {@code filler_column} picks another column than the middle (a module with a post in its middle).
     */
    static Defs.WallModule filler(Kit k, List<Defs.WallModule> walls) {
        Defs.WallModule src = null;
        for (Defs.WallModule w : walls) {
            if (w.hasDoor) continue;
            int rank = (w.hasWindow ? 1000 : 0) + w.length;
            int best = src == null ? Integer.MAX_VALUE : (src.hasWindow ? 1000 : 0) + src.length;
            if (rank < best) src = w;
        }
        if (src == null) return null;
        String key = "~filler~" + src.name + "~" + src.fillerColumn;
        if (!k.clips.containsKey(key)) {
            Clipboard cb = clip(k, src);
            if (cb == null) return null;
            try {
                k.clips.put(key, Paster.slice(cb, Dir.parse(src.frontFacing), src.fillerColumn));
            } catch (Exception e) {
                k.warn.add("could not cut a filler column from " + src.name + ": " + e.getMessage());
                return null;
            }
        }
        Defs.WallModule f = new Defs.WallModule();
        f.name = key;
        f.length = 1;
        f.height = src.height;
        f.frontFacing = src.frontFacing;
        f.tags = src.tags;
        return f;
    }

    private static List<Defs.WallModule> fill(Kit k, int len, List<Defs.WallModule> walls, Map<String, Integer> used) {
        List<Defs.WallModule> r = SegmentFiller.fill(len, walls, w -> w.length, w -> w.weight,
                w -> w.maxPerBuilding <= 0 || used.getOrDefault(w.name, 0) < w.maxPerBuilding, k.rnd);
        if (r != null) for (Defs.WallModule w : r) used.merge(w.name, 1, Integer::sum);
        return r;
    }

    private static Clipboard clip(Kit k, Defs.WallModule w) {
        Clipboard f = k.clips.get(w.name);
        if (f != null) return f;
        return load(k, k.store.wallDir(), w.name);
    }

    static Clipboard load(Kit k, Path dir, String name) {
        Path p = k.store.schematic(dir, name);
        if (p == null) { k.warn.add("missing " + name + ".schem"); return null; }
        try {
            return k.paster.load(p);
        } catch (IOException e) {
            k.warn.add(name + ".schem: " + e.getMessage());
            return null;
        }
    }

    /** Where the foundation stands 3+ blocks out of the ground, a door into it and a room behind. */
    private static void basementDoor(Kit k, Footprint fp, Footprint.Ring ring, Map<Long, Integer> t, Map<Long, Integer> ground,
                                     Surface s, int floorY, Defs.BuildingStyle style, Set<Long> covered) throws WorldEditException {
        Footprint.Segment bestSeg = null;
        int bestI = -1, bestExposed = 2, bestOut = 0;
        for (Footprint.Segment sg : ring.segments())
            for (int i = 1; i < sg.length() - 1; i++) {
                boolean free = true;
                for (int j = -1; j <= 1; j++) if (covered.contains(PlotPolygon.key(sg.cellX(i + j), sg.cellZ(i + j)))) free = false;
                if (!free) continue;
                int ox = sg.cellX(i) + sg.normal().dx, oz = sg.cellZ(i) + sg.normal().dz;
                int g = s.y(ox, oz);
                if (g == Integer.MIN_VALUE) continue;
                int exposed = floorY - 1 - g;
                if (exposed > bestExposed) { bestExposed = exposed; bestSeg = sg; bestI = i; bestOut = g; }
            }
        if (bestSeg == null) return;
        int x = bestSeg.cellX(bestI), z = bestSeg.cellZ(bestI), dy = bestOut + 1;
        BlockState frame = Blocks.state(style.basementFrameBlock);
        for (int j = -1; j <= 1; j++)
            for (int yy = dy; yy <= dy + 2; yy++) {
                int fx = x + bestSeg.along().dx * j, fz = z + bestSeg.along().dz * j;
                if (j != 0 || yy == dy + 2) if (yy < floorY - 1) k.set(fx, yy, fz, frame);
            }
        BlockData lower = Blocks.facing(style.basementDoor, bestSeg.normal().opposite()), upper = lower.clone();
        if (lower instanceof Door d) d.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
        if (upper instanceof Door d) d.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
        k.set(x, dy, z, Blocks.state(lower));
        k.set(x, dy + 1, z, Blocks.state(upper));
        // the room behind: everything under the floor at the door's level that is foundation, not ground
        for (long c : fp.cells()) {
            int cx = PlotPolygon.kx(c), cz = PlotPolygon.kz(c);
            if (fp.onRing(cx, cz)) continue;
            for (int yy = dy; yy <= Math.min(dy + 1, floorY - 2); yy++) if (yy > t.get(c)) k.set(cx, yy, cz, Blocks.air());
        }
        k.info.add("a cellar door where the foundation stands " + bestExposed + " blocks high");
    }

    private static void roof(Kit k, Footprint fp, int yR, Defs.BuildingStyle st) throws WorldEditException {
        int o = Math.max(0, st.roofOverhang);
        Footprint.Rect main = fp.wings.get(0);
        for (int wi = 0; wi < fp.wings.size(); wi++) {
            Footprint.Rect w = fp.wings.get(wi);
            boolean alongX = w.width() >= w.depth();
            Footprint.Rect r = w;
            Dir inner = null;                       // the wing's end that runs into the main body
            if (wi > 0) {
                if (w.z1() < main.z0()) { inner = Dir.SOUTH; alongX = false; r = new Footprint.Rect(w.x0(), w.z0(), w.x1(), main.z0() + main.depth() / 2); }
                else if (w.z0() > main.z1()) { inner = Dir.NORTH; alongX = false; r = new Footprint.Rect(w.x0(), main.z1() - main.depth() / 2, w.x1(), w.z1()); }
                else if (w.x1() < main.x0()) { inner = Dir.EAST; alongX = true; r = new Footprint.Rect(w.x0(), w.z0(), main.x0() + main.width() / 2, w.z1()); }
                else if (w.x0() > main.x1()) { inner = Dir.WEST; alongX = true; r = new Footprint.Rect(main.x1() - main.width() / 2, w.z0(), w.x1(), w.z1()); }
            }
            int rx0 = r.x0() - o, rx1 = r.x1() + o, rz0 = r.z0() - o, rz1 = r.z1() + o;
            BlockState gable = Blocks.state(st.gableBlock), slab = Blocks.slab(st.roofSlab);
            for (int kk = 0; ; kk++) {
                int y = yR + kk - o;
                int lo = (alongX ? rz0 : rx0) + kk, hi = (alongX ? rz1 : rx1) - kk;
                if (lo > hi) break;
                int a0 = alongX ? rx0 : rz0, a1 = alongX ? rx1 : rz1;
                for (int a = a0; a <= a1; a++) {
                    if (lo == hi) {
                        roofSet(k, alongX ? a : lo, y, alongX ? lo : a, slab);
                    } else {
                        roofSet(k, alongX ? a : lo, y, alongX ? lo : a, Blocks.stairs(st.roofStairs, alongX ? Dir.SOUTH : Dir.EAST));
                        roofSet(k, alongX ? a : hi, y, alongX ? hi : a, Blocks.stairs(st.roofStairs, alongX ? Dir.NORTH : Dir.WEST));
                    }
                }
                if (kk >= o) {
                    // the gable triangles in the end walls (a wing only at its outer end)
                    int[] ends = alongX ? new int[]{w.x0(), w.x1()} : new int[]{w.z0(), w.z1()};
                    for (int e = 0; e < 2; e++) {
                        if (inner != null && ((e == 0 && (inner == Dir.WEST || inner == Dir.NORTH)) || (e == 1 && (inner == Dir.EAST || inner == Dir.SOUTH)))) continue;
                        for (int q = lo + 1; q <= hi - 1; q++) roofSet(k, alongX ? ends[e] : q, y, alongX ? q : ends[e], gable);
                    }
                }
            }
        }
    }

    private static void roofSet(Kit k, int x, int y, int z, BlockState st) throws WorldEditException {
        if (k.freeForRoof(x, y, z)) k.set(x, y, z, st);
    }

    // ------------------------------------------------------------------ objects, garden, fence
    private static void object(Kit k, Surface s, Footprint.Rect r, Set<String> cats, Map<Long, Integer> ground) throws WorldEditException {
        List<Defs.ObjectDef> fit = new ArrayList<>();
        List<Integer> turnsFor = new ArrayList<>();
        for (Defs.ObjectDef o : TagMatcher.all(k.store.objects, k.tags)) {
            if (!cats.contains(o.category)) continue;
            if (o.footprintX == r.width() && o.footprintZ == r.depth()) { fit.add(o); turnsFor.add(o.canRotate && k.rnd.nextBoolean() ? 2 : 0); }
            else if (o.canRotate && o.footprintZ == r.width() && o.footprintX == r.depth()) { fit.add(o); turnsFor.add(k.rnd.nextBoolean() ? 1 : 3); }
        }
        if (fit.isEmpty()) {
            k.warn.add("no " + cats + " object of " + r.width() + "x" + r.depth() + " matches the tags: left empty");
            return;
        }
        int i = fit.indexOf(TagMatcher.weighted(fit, q -> q.weight, k.rnd));
        Defs.ObjectDef o = fit.get(i);
        Clipboard cb = load(k, k.store.objectDir(), o.name);
        if (cb == null) return;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = r.x0(); x <= r.x1(); x++)
            for (int z = r.z0(); z <= r.z1(); z++) {
                Integer g = ground.get(PlotPolygon.key(x, z));
                int gy = g != null ? g : s.y(x, z);
                lo = Math.min(lo, gy); hi = Math.max(hi, gy);
            }
        if (hi - lo > o.maxSlope) k.warn.add(o.name + ": the ground under it rises " + (hi - lo) + " blocks (max_slope " + o.maxSlope + ")");
        Paster.paste(k.es(), cb, turnsFor.get(i), BlockVector3.at(r.x0(), lo + 1 + o.yOffset, r.z0()), true);
        k.info.add(o.category + " " + o.name);
    }

    private static void garden(Kit k, Surface s, Footprint.Rect r, Map<Long, Integer> ground, Defs.BuildingStyle st) throws WorldEditException {
        BlockState farm = Blocks.state(Material.FARMLAND.createBlockData()), water = Blocks.state(st.hiddenWaterBlock);
        boolean rowsAlongX = r.width() >= r.depth();
        Map<Integer, BlockState> crop = new HashMap<>();
        for (int x = r.x0(); x <= r.x1(); x++)
            for (int z = r.z0(); z <= r.z1(); z++) {
                Integer g = ground.get(PlotPolygon.key(x, z));
                int gy = g != null ? g : s.y(x, z);
                if (gy == Integer.MIN_VALUE) continue;
                boolean wet = (x - r.x0()) % 7 == 3 && (z - r.z0()) % 7 == 3
                        || (r.width() < 7 || r.depth() < 7) && x == (r.x0() + r.x1()) / 2 && z == (r.z0() + r.z1()) / 2;
                if (wet) { k.set(x, gy, z, water); continue; }
                k.set(x, gy, z, farm);
                int row = rowsAlongX ? z : x;
                BlockState c = crop.computeIfAbsent(row, q -> st.crops.isEmpty() ? null : Blocks.state(st.crops.get(k.rnd.nextInt(st.crops.size()))));
                if (c != null) k.set(x, gy + 1, z, c);
            }
        k.info.add("a garden " + r.width() + "x" + r.depth());
    }

    private static void fence(Kit k, Set<Long> cells, Set<Long> gates, Map<Long, Integer> ground) throws WorldEditException {
        Defs.FencePreset f = TagMatcher.best(k.store.fences, k.tags, k.rnd);
        if (f == null) { k.warn.add("no fence preset matches the tags"); return; }
        List<long[]> loop = order(cells);
        int n = loop.size();
        Set<Long> outside = outside(k, cells);
        int every = Math.max(1, f.blocksBetweenCol + 1);
        // a gate schematic wider than one block takes the fence cells beside its gold block (centred on it, along the run)
        Clipboard gateCb = null;
        if (f.gateSchematic != null && !f.gateSchematic.isBlank())
            gateCb = load(k, k.store.objectDir(), f.gateSchematic.replaceFirst("\\.schem(atic)?$", ""));
        Map<Long, int[]> gateAt = new HashMap<>();   // gold block -> {min x, min z, turns}
        Set<Long> gateCells = new HashSet<>(gates);
        for (int i = 0; i < n; i++) {
            long c = loop.get(i)[0];
            if (!gates.contains(c)) continue;
            int x = PlotPolygon.kx(c), z = PlotPolygon.kz(c);
            long p = loop.get((i - 1 + n) % n)[0], q = loop.get((i + 1) % n)[0];
            boolean runsX = PlotPolygon.kz(p) == PlotPolygon.kz(q);
            Dir out = runsX ? (outside.contains(PlotPolygon.key(x, z - 1)) ? Dir.NORTH : Dir.SOUTH)
                    : (outside.contains(PlotPolygon.key(x - 1, z)) ? Dir.WEST : Dir.EAST);
            if (gateCb == null) { gateAt.put(c, new int[]{x, z, -1, out.ordinal()}); continue; }
            int turns = Paster.turns(Dir.parse(f.gateFrontFacing), out);
            int[] size = Paster.size(gateCb, turns);
            int along = runsX ? size[0] : size[2], depth = runsX ? size[2] : size[0];
            int before = (along - 1) / 2;
            boolean fits = true;
            List<Long> span = new ArrayList<>();
            for (int j = -before; j < along - before; j++) {
                long cj = runsX ? PlotPolygon.key(x + j, z) : PlotPolygon.key(x, z + j);
                if (!cells.contains(cj)) fits = false;
                span.add(cj);
            }
            if (!fits) {
                k.warn.add("the gate schematic (" + along + " wide) does not fit the straight fence at " + x + "," + z + ": the gate block is used");
                gateAt.put(c, new int[]{x, z, -1, out.ordinal()});
                continue;
            }
            gateCells.addAll(span);
            int mx = runsX ? x - before : x, mz = runsX ? z : z - before;
            // like a wall module: its back on the fence line, anything deeper sticks out of the plot
            if (out == Dir.NORTH) mz -= depth - 1;
            if (out == Dir.WEST) mx -= depth - 1;
            gateAt.put(c, new int[]{mx, mz, turns, out.ordinal()});
        }
        // posts: on the outline's real corners and evenly between them (0.7.0, Arag 11-15: a slanted fence had a post on every
        // stair-step), and beside every gate; an even one right beside a gate's post is left out
        List<int[]> pts = new ArrayList<>();
        for (long[] c : loop) pts.add(new int[]{PlotPolygon.kx(c[0]), PlotPolygon.kz(c[0])});
        Set<Integer> even = cz.lollywood.townbuilder.geom.FencePosts.posts(pts, every - 1);
        Set<Integer> real = new HashSet<>(cz.lollywood.townbuilder.geom.FencePosts.corners(pts));
        Set<Integer> byGate = new HashSet<>();
        for (int i = 0; i < n; i++) {
            long c = loop.get(i)[0];
            long prev = loop.get((i - 1 + n) % n)[0], next = loop.get((i + 1) % n)[0];
            if (!gateCells.contains(c) && (gateCells.contains(prev) || gateCells.contains(next))) byGate.add(i);
        }
        Map<Long, Boolean> post = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long c = loop.get(i)[0];
            boolean p = byGate.contains(i);
            if (!p && even.contains(i) && !gateCells.contains(c)) {
                boolean besideGatePost = byGate.contains((i - 1 + n) % n) || byGate.contains((i + 1) % n);
                p = real.contains(i) || !besideGatePost;
            }
            post.put(c, p);
        }
        // the blocks, column by column; their arms set after, toward the neighbours along the loop at the same height
        Map<String, BlockData> placed = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long c = loop.get(i)[0];
            int x = PlotPolygon.kx(c), z = PlotPolygon.kz(c), g = ground.getOrDefault(c, k.y0);
            if (gateAt.containsKey(c)) {
                // the gate's face is across the fence's run here, toward the outside
                int[] at = gateAt.get(c);
                Dir out = Dir.values()[at[3]];
                if (at[2] >= 0) Paster.paste(k.es(), gateCb, at[2], BlockVector3.at(at[0], g + 1, at[1]), true);
                else placed.put(x + "," + (g + 1) + "," + z, Blocks.facing(f.gateBlock, out));
                continue;
            }
            if (gateCells.contains(c)) continue;
            boolean isPost = post.get(c);
            for (int h = 1; h <= f.height; h++) placed.put(x + "," + (g + h) + "," + z, Blocks.data(isPost ? f.postBlock : f.fillBlock));
            if (isPost && f.postCapBlock != null && !f.postCapBlock.isBlank()) placed.put(x + "," + (g + f.height + 1) + "," + z, Blocks.data(f.postCapBlock));
        }
        for (Map.Entry<String, BlockData> e : placed.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            BlockData d = e.getValue();
            if (Blocks.connects(d) && !org.bukkit.Tag.FENCE_GATES.isTagged(d.getMaterial())) {
                boolean nN = joins(placed, x, y, z - 1), nE = joins(placed, x + 1, y, z), nS = joins(placed, x, y, z + 1), nW = joins(placed, x - 1, y, z);
                d = Blocks.connect(d, nN, nE, nS, nW, post.getOrDefault(PlotPolygon.key(x, z), false));
            }
            k.set(x, y, z, Blocks.state(d));
        }
        k.info.add("a fence of " + n + " blocks, " + gates.size() + " gate(s)");
    }

    private static boolean joins(Map<String, BlockData> placed, int x, int y, int z) {
        BlockData d = placed.get(x + "," + y + "," + z);
        return d != null && Blocks.connects(d);
    }

    /** Which way is out of the plot from a fence cell. */
    private static Dir normal(int x, int z, Set<Long> outside) {
        for (Dir d : Dir.values()) if (outside.contains(PlotPolygon.key(x + d.dx, z + d.dz))) return d;
        return Dir.NORTH;
    }

    /** Cells outside the fence: a flood from the selection's edge that the fence stops. */
    private static Set<Long> outside(Kit k, Set<Long> fence) {
        Set<Long> out = new HashSet<>();
        ArrayDeque<long[]> q = new ArrayDeque<>();
        for (int x = k.x0 - 1; x <= k.x1 + 1; x++) for (int z : new int[]{k.z0 - 1, k.z1 + 1}) q.add(new long[]{x, z});
        for (int z = k.z0 - 1; z <= k.z1 + 1; z++) for (int x : new int[]{k.x0 - 1, k.x1 + 1}) q.add(new long[]{x, z});
        while (!q.isEmpty()) {
            long[] c = q.poll();
            int x = (int) c[0], z = (int) c[1];
            if (x < k.x0 - 1 || x > k.x1 + 1 || z < k.z0 - 1 || z > k.z1 + 1) continue;
            long key = PlotPolygon.key(x, z);
            if (fence.contains(key) || !out.add(key)) continue;
            for (Dir d : Dir.values()) q.add(new long[]{x + d.dx, z + d.dz});
        }
        return out;
    }

    /** The fence cells as a walk along the loop (4-neighbours), so posts can be counted along it. */
    private static List<long[]> order(Set<Long> cells) {
        List<long[]> out = new ArrayList<>();
        Set<Long> left = new LinkedHashSet<>(cells);
        while (!left.isEmpty()) {
            long cur = left.iterator().next();
            while (true) {
                left.remove(cur);
                out.add(new long[]{cur});
                long nxt = Long.MIN_VALUE;
                for (Dir d : Dir.values()) {
                    long nb = PlotPolygon.key(PlotPolygon.kx(cur) + d.dx, PlotPolygon.kz(cur) + d.dz);
                    if (left.contains(nb)) { nxt = nb; break; }
                }
                if (nxt == Long.MIN_VALUE) break;
                cur = nxt;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ outline → plan
    /** The ring plus everything it encloses. */
    static Set<Long> fillRing(Set<Long> ring) {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long c : ring) { x0 = Math.min(x0, PlotPolygon.kx(c)); z0 = Math.min(z0, PlotPolygon.kz(c)); x1 = Math.max(x1, PlotPolygon.kx(c)); z1 = Math.max(z1, PlotPolygon.kz(c)); }
        Set<Long> out = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{x0 - 1, z0 - 1});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] < x0 - 1 || c[0] > x1 + 1 || c[1] < z0 - 1 || c[1] > z1 + 1) continue;
            long key = PlotPolygon.key(c[0], c[1]);
            if (ring.contains(key) || !out.add(key)) continue;
            for (Dir d : Dir.values()) q.add(new int[]{c[0] + d.dx, c[1] + d.dz});
        }
        Set<Long> in = new HashSet<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (!out.contains(PlotPolygon.key(x, z))) in.add(PlotPolygon.key(x, z));
        return in;
    }

    /** The plan as rectangles, largest first (the main body), then the wings. */
    static List<Footprint.Rect> decompose(Set<Long> cells) {
        Set<Long> left = new HashSet<>(cells);
        List<Footprint.Rect> out = new ArrayList<>();
        while (!left.isEmpty()) {
            Footprint.Rect best = null;
            for (long c : left) {
                int x = PlotPolygon.kx(c), z = PlotPolygon.kz(c);
                int maxW = 0;
                while (left.contains(PlotPolygon.key(x + maxW, z))) maxW++;
                for (int dz = 0; ; dz++) {
                    int w = 0;
                    while (w < maxW && left.contains(PlotPolygon.key(x + w, z + dz))) w++;
                    if (w == 0) break;
                    maxW = w;
                    Footprint.Rect r = new Footprint.Rect(x, z, x + w - 1, z + dz);
                    if (best == null || r.area() > best.area()) best = r;
                }
            }
            out.add(best);
            for (int x = best.x0(); x <= best.x1(); x++) for (int z = best.z0(); z <= best.z1(); z++) left.remove(PlotPolygon.key(x, z));
        }
        return out;
    }

    static Set<Long> cellsOf(Footprint.Rect r) {
        Set<Long> s = new HashSet<>();
        for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) s.add(PlotPolygon.key(x, z));
        return s;
    }

    /** The bounding rectangles of the 4-connected pieces of one colour. */
    static List<Footprint.Rect> rects(Set<Long> cells) {
        List<Footprint.Rect> out = new ArrayList<>();
        Set<Long> left = new HashSet<>(cells);
        while (!left.isEmpty()) {
            long start = left.iterator().next();
            ArrayDeque<Long> q = new ArrayDeque<>(List.of(start));
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            while (!q.isEmpty()) {
                long c = q.poll();
                if (!left.remove(c)) continue;
                int x = PlotPolygon.kx(c), z = PlotPolygon.kz(c);
                x0 = Math.min(x0, x); z0 = Math.min(z0, z); x1 = Math.max(x1, x); z1 = Math.max(z1, z);
                for (Dir d : Dir.values()) q.add(PlotPolygon.key(x + d.dx, z + d.dz));
            }
            out.add(new Footprint.Rect(x0, z0, x1, z1));
        }
        return out;
    }
}
