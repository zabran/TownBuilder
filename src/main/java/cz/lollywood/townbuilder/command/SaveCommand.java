package cz.lollywood.townbuilder.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import cz.lollywood.townbuilder.config.ConfigStore;
import cz.lollywood.townbuilder.config.Defs;
import cz.lollywood.townbuilder.config.TagMatcher;
import cz.lollywood.townbuilder.geom.Dir;
import cz.lollywood.townbuilder.geom.Orient;
import cz.lollywood.townbuilder.geom.SegmentFiller;
import cz.lollywood.townbuilder.world.Paster;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * /plot save &lt;kind&gt; &lt;name&gt; [tags] [key=value ...] [overwrite]: the selected piece becomes a module or an object: its
 * .schem and its sidecar .json, measured (length, height, door, window, footprint) and turned the way the build expects (the
 * facing read from where the builder stands). /plot info &lt;tags&gt;: what those tags select and what is still missing.
 */
final class SaveCommand {
    static final List<String> KINDS = List.of("wall", "outer_corner", "inner_corner", "decoration", "shed", "gazebo", "pond", "foliage", "clutter", "gate");
    private static final List<String> FLOORS = List.of("basement", "ground", "upper");

    private SaveCommand() {}

    static void save(ConfigStore store, Paster paster, CommandSender sender, String[] args, Report r) {
        if (args.length < 3) {
            r.warn("/plot save <" + String.join("|", KINDS) + "> <name> [tags] [floors=ground,upper] [weight=10] "
                    + "[max=0] [facing=NORTH] [overhang=0] [outside=north,west] [offset=0] [decoration=true] [deco_width=0] [deco_flavours=a,b] "
                    + "[deco_chance=1] [filler=-1] [flavours=a,b] [depth_offset=0] [y_offset=0] [rotate=true] [slope=1] [fence=<id>] [overwrite]");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        String name = args[2];
        if (!KINDS.contains(kind)) { r.err("unknown kind " + kind + "; one of " + KINDS); return; }
        if (!name.matches("[A-Za-z0-9_\\-]+")) { r.err("a name of letters, digits, _ and - only"); return; }
        Map<String, String> opt = new HashMap<>();
        List<String> tagWords = new ArrayList<>();
        boolean overwrite = false;
        World world = null;
        int[] sel = null;
        for (int i = 3; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("at") && i + 7 < args.length) {
                world = Bukkit.getWorld(args[i + 1]);
                sel = new int[6];
                for (int j = 0; j < 6; j++) sel[j] = Integer.parseInt(args[i + 2 + j]);
                i += 7;
            } else if (a.equalsIgnoreCase("overwrite")) overwrite = true;
            else if (a.contains("=")) opt.put(a.substring(0, a.indexOf('=')).toLowerCase(Locale.ROOT), a.substring(a.indexOf('=') + 1));
            else tagWords.add(a);
        }
        Player player = sender instanceof Player p ? p : null;
        if (sel == null) {
            if (player == null) { r.err("From the console add: at <world> <x1> <y1> <z1> <x2> <y2> <z2>, and facing= or outside="); return; }
            Region region;
            try {
                region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(BukkitAdapter.adapt(player.getWorld()));
            } catch (IncompleteRegionException e) {
                r.err("Select exactly the piece first (//wand, then its two corners).");
                return;
            }
            if (!(region instanceof CuboidRegion)) { r.err("The selection must be a cuboid (//sel cuboid)."); return; }
            BlockVector3 a = region.getMinimumPoint(), b = region.getMaximumPoint();
            sel = new int[]{a.x(), a.y(), a.z(), b.x(), b.y(), b.z()};
            world = player.getWorld();
        }
        if (world == null) { r.err("No such world."); return; }
        BlockVector3 min = BlockVector3.at(Math.min(sel[0], sel[3]), Math.min(sel[1], sel[4]), Math.min(sel[2], sel[5]));
        BlockVector3 max = BlockVector3.at(Math.max(sel[0], sel[3]), Math.max(sel[1], sel[4]), Math.max(sel[2], sel[5]));
        int sx = max.x() - min.x() + 1, sy = max.y() - min.y() + 1, sz = max.z() - min.z() + 1;
        // the builder's place relative to the box's centre (x, z)
        double dx = 0, dz = 0;
        boolean placed = false;
        if (player != null && player.getWorld().equals(world)) {
            Location l = player.getLocation();
            dx = l.getX() - (min.x() + sx / 2.0);
            dz = l.getZ() - (min.z() + sz / 2.0);
            placed = true;
        }

        boolean module = kind.equals("wall") || kind.endsWith("_corner");
        Path dir = module ? store.wallDir() : kind.equals("decoration") ? store.decorationDir() : store.objectDir();
        Path jsonFile = dir.resolve(name + ".json");
        if (store.schematic(dir, name) != null && !overwrite) {
            r.err(name + ".schem exists already; add overwrite to replace it (its .json keeps every key you set by hand).");
            return;
        }

        // copy the box out of the world
        BlockArrayClipboard cb;
        com.sk89q.worldedit.world.World wew = BukkitAdapter.adapt(world);
        CuboidRegion region = new CuboidRegion(wew, min, max);
        try (EditSession es = WorldEdit.getInstance().newEditSession(wew)) {
            cb = new BlockArrayClipboard(region);
            cb.setOrigin(min);
            ForwardExtentCopy copy = new ForwardExtentCopy(es, region, cb, min);
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        } catch (Exception e) {
            r.err("could not copy the selection: " + e);
            return;
        }
        int solid = 0;
        boolean door = false, window = false;
        for (BlockVector3 p : region) {
            String id = cb.getBlock(p).getBlockType().id();
            if (id.endsWith(":air") || id.endsWith("_air")) continue;
            solid++;
            if (id.endsWith("_door")) door = true;
            if (id.contains("glass")) window = true;
        }
        if (solid == 0) { r.err("The selection holds only air."); return; }

        JsonObject j = new JsonObject();
        if (!kind.equals("gate") && Files.isRegularFile(jsonFile)) {
            try (Reader rd = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
                JsonElement e = JsonParser.parseReader(rd);
                if (e.isJsonObject()) j = e.getAsJsonObject();
            } catch (Exception e) {
                r.err(name + ".json is not valid JSON (" + e.getMessage() + "); fix or delete it first.");
                return;
            }
        }
        List<String> notes = new ArrayList<>();
        if (!tagWords.isEmpty()) j.add("tags", strings(new ArrayList<>(TagMatcher.parse(String.join(",", tagWords)))));
        else if (!kind.equals("gate") && !j.has("tags")) { r.err("Give the tags, e.g. /plot save " + kind + " " + name + " city,union,residential"); return; }

        String report;
        switch (kind) {
            case "wall" -> {
                Dir f = opt.containsKey("facing") ? parseDir(opt.get("facing")) : placed ? Orient.wallFacing(sx, sz, dx, dz) : null;
                if (f == null) { r.err(placed ? "Stand in front of the facade (not off the end of the wall), or add facing=<NORTH|EAST|SOUTH|WEST>."
                        : "Add facing=<NORTH|EAST|SOUTH|WEST>: where the facade looks."); return; }
                int len = Orient.length(sx, sz, f), depth = (f == Dir.NORTH || f == Dir.SOUTH) ? sz : sx;
                j.addProperty("module_type", "wall");
                j.addProperty("length", len);
                j.addProperty("height", sy);
                j.addProperty("has_door", door);
                j.addProperty("has_window", window);
                j.addProperty("front_facing", f.name());
                if (!floors(j, opt, r)) return;
                common(j, opt);
                if (!decorationKeys(j, opt, r, len)) return;
                if (opt.containsKey("overhang")) j.addProperty("overhang_depth", Integer.parseInt(opt.get("overhang")));
                int ov = j.has("overhang_depth") ? j.get("overhang_depth").getAsInt() : 0;
                if (ov > 0) notes.add("overhang_depth " + ov + ": its back row stands " + ov + " block(s) INSIDE the house (an overhang or an arcade), "
                        + "used only on the front wall; the side walls of that floor get " + ov + " shorter at the front");
                else if (depth > 1) notes.add("it is " + depth + " blocks deep: the back row stands on the wall line, the rest sticks out to the street "
                        + "(for an overhang or an arcade instead, save again with overhang=<blocks in>)");
                boolean deco = j.has("decoration") && j.get("decoration").getAsBoolean();
                report = "wall, length " + len + ", height " + sy + ", facade " + f + (door ? ", a door" : "") + (window ? ", a window" : "")
                        + ", floors " + j.get("allowed_floors") + (deco ? ", decorations up to " + (j.has("decoration_max_width") && j.get("decoration_max_width").getAsInt() > 0
                        ? j.get("decoration_max_width").getAsInt() : len) + " wide" + (j.has("decoration_flavours") && j.get("decoration_flavours").getAsJsonArray().size() > 0
                        ? " " + j.get("decoration_flavours") : "") : "");
            }
            case "outer_corner", "inner_corner" -> {
                boolean outer = kind.equals("outer_corner");
                Dir[] out = opt.containsKey("outside") ? parsePair(opt.get("outside")) : placed ? Orient.outside(dx, dz) : null;
                if (out == null) { r.err(placed ? "Stand diagonally outside the corner (where both its faces can be seen), or add outside=north,west."
                        : "Add outside=<north|south>,<east|west>: the two sides the corner's faces look to."); return; }
                Dir f;
                try { f = Orient.cornerFront(outer, out[0], out[1]); } catch (IllegalArgumentException e) { r.err(e.getMessage()); return; }
                if (sx != sz) { r.err("A corner must be square seen from above (this one is " + sx + " x " + sz + ")."); return; }
                int off = opt.containsKey("offset") ? Integer.parseInt(opt.get("offset")) : j.has("offset") ? j.get("offset").getAsInt() : 0;
                if (off < 0) { r.err("offset is 0 or more"); return; }
                if (!outer && off > 0) { notes.add("offset is for outer corners only: saved as 0 (an inner corner's box already reaches into the notch)"); off = 0; }
                int width = sx - off;
                if (width < 1) { r.err("offset " + off + " leaves nothing on the wall line: the box is only " + sx + " wide"); return; }
                j.addProperty("module_type", kind);
                j.addProperty("length", width);
                j.addProperty("offset", off);
                j.addProperty("height", sy);
                j.addProperty("has_door", false);
                j.addProperty("has_window", window);
                j.addProperty("front_facing", f.name());
                if (!floors(j, opt, r)) return;
                common(j, opt);
                if (off > 0) notes.add("offset " + off + ": the outer " + off + " row(s) on both faces stand OUTSIDE the walls (pillars that stick out); "
                        + width + " block(s) of it lie on the wall line");
                if (width > 1) notes.add("a corner " + width + " wide: the walls start " + width + " blocks from where they meet; every " + kind.replace('_', ' ')
                        + " of these tags should be " + width + " wide (the build uses the most common width)"
                        + (outer ? "" : "; its notch (the cells between the two arms, outside the house) is pasted as you built it, air is skipped"));
                report = kind.replace('_', ' ') + ", " + width + " x " + width + (off > 0 ? " on the wall line, sticking " + off + " out (a " + sx + " x " + sx + " box)" : "")
                        + ", height " + sy + ", its faces look " + out[0] + " and " + out[1] + " (saved as front_facing " + f + ")";
            }
            case "decoration" -> {
                Dir f = opt.containsKey("facing") ? parseDir(opt.get("facing")) : placed ? Orient.wallFacing(sx, sz, dx, dz) : null;
                if (f == null) { r.err(placed ? "Stand in front of the decoration (on its street side, not off its end), or add facing=<NORTH|EAST|SOUTH|WEST>."
                        : "Add facing=<NORTH|EAST|SOUTH|WEST>: where its street side looks."); return; }
                int wd = Orient.length(sx, sz, f), depth = (f == Dir.NORTH || f == Dir.SOUTH) ? sz : sx;
                j.addProperty("width", wd);
                j.addProperty("height", sy);
                j.addProperty("front_facing", f.name());
                String fl = opt.containsKey("flavours") ? opt.get("flavours") : opt.get("flavour");
                if (fl != null) j.add("flavours", strings(new ArrayList<>(TagMatcher.parse(fl))));
                else if (!j.has("flavours")) j.add("flavours", strings(List.of()));
                if (opt.containsKey("y_offset")) j.addProperty("y_offset", Integer.parseInt(opt.get("y_offset")));
                else if (!j.has("y_offset")) j.addProperty("y_offset", 0);
                if (opt.containsKey("depth_offset")) j.addProperty("depth_offset", Integer.parseInt(opt.get("depth_offset")));
                else if (!j.has("depth_offset")) j.addProperty("depth_offset", 0);
                if (opt.containsKey("weight")) j.addProperty("weight", Integer.parseInt(opt.get("weight")));
                else if (!j.has("weight")) j.addProperty("weight", 10);
                if (j.get("flavours").getAsJsonArray().isEmpty())
                    notes.add("no flavour: a wall that asks for flavours (decoration_flavours) skips it; add flavours=planter (or ledge, clothesline...)");
                notes.add("it goes in front of wall modules with \"decoration\": true whose decoration_max_width (or length) is at least " + wd
                        + "; its back row on the first row outside the wall" + (j.get("depth_offset").getAsInt() != 0 ? " moved " + j.get("depth_offset") + " out" : "")
                        + ", its foot " + j.get("y_offset") + " block(s) above the wall module's foot");
                report = "decoration, " + wd + " wide, " + sy + " high, " + depth + " deep, its street side looks " + f + ", flavours " + j.get("flavours")
                        + ", y_offset " + j.get("y_offset");
            }
            case "gate" -> {
                Dir f = opt.containsKey("facing") ? parseDir(opt.get("facing")) : placed ? Orient.wallFacing(sx, sz, dx, dz) : null;
                if (f == null) { r.err("Stand on the street side of the gate, or add facing=<NORTH|EAST|SOUTH|WEST>."); return; }
                report = "gate " + sx + "x" + sz + ", " + sy + " high, its street side looks " + f;
                if (opt.containsKey("fence")) {
                    String g = setGate(store, opt.get("fence"), name, f);
                    if (g != null) { r.err(g); return; }
                    report += "; fences.json preset " + opt.get("fence") + " now uses it";
                } else notes.add("put into the fence preset in fences.json: \"gate_schematic\": \"" + name + ".schem\", \"gate_front_facing\": \"" + f
                        + "\" (or save again with fence=<id>)");
            }
            default -> { // the objects
                j.addProperty("category", kind);
                j.addProperty("footprint_x", sx);
                j.addProperty("footprint_z", sz);
                if (opt.containsKey("y_offset")) j.addProperty("y_offset", Integer.parseInt(opt.get("y_offset")));
                else if (!j.has("y_offset")) j.addProperty("y_offset", 0);
                if (opt.containsKey("rotate")) j.addProperty("can_rotate", Boolean.parseBoolean(opt.get("rotate")));
                else if (!j.has("can_rotate")) j.addProperty("can_rotate", true);
                if (opt.containsKey("slope")) j.addProperty("max_slope", Integer.parseInt(opt.get("slope")));
                else if (!j.has("max_slope")) j.addProperty("max_slope", 1);
                if (opt.containsKey("weight")) j.addProperty("weight", Integer.parseInt(opt.get("weight")));
                else if (!j.has("weight")) j.addProperty("weight", 10);
                if (kind.equals("pond") && j.get("y_offset").getAsInt() == 0)
                    notes.add("a pond usually sinks: y_offset=-1 puts its top row level with the ground");
                report = kind + ", footprint " + sx + " x " + sz + " (x west-east, z north-south), " + sy + " high, y_offset " + j.get("y_offset");
            }
        }

        try {
            Files.createDirectories(dir);
            try (ClipboardWriter w = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(dir.resolve(name + ".schem").toFile()))) {
                w.write(cb);
            }
            if (!kind.equals("gate")) Files.writeString(jsonFile, ConfigStore.GSON.toJson(j) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            r.err("could not write the files: " + e);
            return;
        }
        store.load();
        paster.clear();
        r.ok("Saved " + name + ": " + report + ".");
        if (kind.equals("wall") || kind.endsWith("_corner")) heightCheck(store, name, notes);
        for (String n : notes) r.warn("  " + n);
        for (String p : store.problems) if (p.contains(name)) r.warn("  " + p);
    }

    /** /plot info &lt;tags&gt;: the layouts, style, fence, modules and objects those tags select, and what is still missing. */
    static void info(ConfigStore store, Set<String> tags, Report r) {
        List<String> out = new ArrayList<>();
        List<Defs.PlotLayout> ls = TagMatcher.all(store.layouts, tags);
        out.add("layouts: " + (ls.isEmpty() ? "NONE" : ls.stream().map(l -> l.id + " (min area " + l.minPlotArea + ", " + l.houseShapes + ", floors "
                + l.storiesRange[0] + "-" + l.storiesRange[1] + ")").toList()));
        List<Defs.BuildingStyle> ss = TagMatcher.all(store.styles, tags);
        out.add("styles: " + (ss.isEmpty() ? "NONE" : ss.stream().map(s -> s.id).toList()));
        List<Defs.FencePreset> fs = TagMatcher.all(store.fences, tags);
        out.add("fences: " + (fs.isEmpty() ? "NONE" : fs.stream().map(f -> f.id + (f.gateSchematic.isBlank() ? " (gate block)"
                : store.schematic(store.objectDir(), f.gateSchematic.replaceFirst("\\.schem(atic)?$", "")) != null ? " (gate " + f.gateSchematic + ")"
                : " (gate " + f.gateSchematic + " MISSING, the gate block instead)")).toList()));
        List<Defs.WallModule> ws = TagMatcher.all(store.walls, tags);
        for (String fl : FLOORS) {
            List<Defs.WallModule> on = ws.stream().filter(w -> w.allowedFloors.contains(fl)).toList();
            if (on.isEmpty()) { out.add(fl + ": no modules" + (fl.equals("ground") ? " (NO HOUSE CAN BE BUILT)" : "")); continue; }
            List<Integer> lens = on.stream().filter(w -> !w.isCorner()).map(w -> w.length).distinct().sorted().toList();
            Set<Integer> hs = new TreeSet<>();
            on.stream().filter(w -> !w.isCorner()).forEach(w -> hs.add(w.height));
            long doors = on.stream().filter(w -> !w.isCorner() && w.hasDoor).count();
            boolean oc = on.stream().anyMatch(w -> w.moduleType.equals("outer_corner")), ic = on.stream().anyMatch(w -> w.moduleType.equals("inner_corner"));
            List<Integer> ow = on.stream().filter(w -> w.moduleType.equals("outer_corner")).map(w -> Math.max(1, w.length)).distinct().sorted().toList();
            List<Integer> iw = on.stream().filter(w -> w.moduleType.equals("inner_corner")).map(w -> Math.max(1, w.length)).distinct().sorted().toList();
            out.add(fl + ": " + on.size() + " modules, lengths " + lens + " -> walls of " + fillable(lens) + " (other lengths, from an outline moved by hand, are closed with filler)" + ", heights " + hs
                    + (hs.size() > 1 ? " (only the most common is used)" : "") + ", " + doors + " with a door, outer corner " + (oc ? "yes" + (ow.equals(List.of(1)) ? "" : " (" + ow.stream().map(x -> x + " wide").toList() + ")") : "NO")
                    + ", inner corner " + (ic ? "yes" + (iw.equals(List.of(1)) ? "" : " (" + iw.stream().map(x -> x + " wide").toList() + ")") : "NO (L and T houses get none)"));
        }
        // 0.8.0: the decorations, by flavour, and the modules that take one
        List<Defs.Decoration> ds = TagMatcher.all(store.decorations, tags);
        List<String> takers = ws.stream().filter(w -> !w.isCorner() && w.decoration).map(w -> w.name + " (up to " + (w.decorationMaxWidth > 0
                ? Math.min(w.decorationMaxWidth, w.length) : w.length) + (w.decorationFlavours.isEmpty() ? "" : ", " + w.decorationFlavours)
                + (w.decorationChance < 1 ? ", " + Math.round(w.decorationChance * 100) + " %" : "") + ")").toList();
        Map<String, List<String>> byFl = new TreeMap<>();
        for (Defs.Decoration d : ds) {
            if (d.flavours.isEmpty()) byFl.computeIfAbsent("(no flavour)", x -> new ArrayList<>()).add(d.name + " " + d.width + " wide");
            for (String fl : d.flavours) byFl.computeIfAbsent(fl, x -> new ArrayList<>()).add(d.name + " " + d.width + " wide");
        }
        out.add("decorations: " + (ds.isEmpty() ? "none" : byFl.toString()) + "; modules that take one: " + (takers.isEmpty() ? "none" : takers));
        List<Defs.ObjectDef> os = TagMatcher.all(store.objects, tags);
        for (String cat : List.of("shed", "gazebo", "pond", "foliage", "clutter")) {
            List<Defs.ObjectDef> c = os.stream().filter(o -> o.category.equals(cat)).toList();
            out.add(cat + ": " + (c.isEmpty() ? "none" : c.stream().map(o -> o.name + " " + o.footprintX + "x" + o.footprintZ).toList()));
        }
        List<String> missing = new ArrayList<>();
        missing(store, store.wallDir(), Defs.WallModule.class, tags, missing);
        missing(store, store.objectDir(), Defs.ObjectDef.class, tags, missing);
        missing(store, store.decorationDir(), Defs.Decoration.class, tags, missing);
        if (!missing.isEmpty()) out.add("sidecars still without a .schem: " + missing);
        r.ok("TownBuilder for " + tags + ":");
        for (String s : out) {
            if (s.contains("NONE") || s.contains("NO ") || s.contains("MISSING")) r.warn("  " + s);
            else r.info("  " + s);
        }
    }

    /**
     * "any length from 2 up", "multiples of 3 only", or the lengths up to 24. A run of reachable lengths at least as long as the
     * longest module goes on for ever (add one more module); a lone reachable length at the cut-off proves nothing.
     */
    static String fillable(List<Integer> lens) {
        if (lens.isEmpty()) return "none";
        int maxL = 60, longest = Collections.max(lens);
        boolean[] r = SegmentFiller.reachable(lens, maxL);
        int from = maxL + 1;
        while (from - 1 >= 1 && r[from - 1]) from--;
        boolean forever = maxL - from + 1 >= longest;
        List<Integer> below = new ArrayList<>();
        for (int n = 1; n < (forever ? from : 25); n++) if (r[n]) below.add(n);
        if (forever) return (below.isEmpty() ? "" : below + " and ") + "any length from " + from + " up";
        int g = 0;
        for (int l : lens) g = gcd(g, l);
        return (g > 1 ? "multiples of " + g + " only: " : "only ") + below + "...";
    }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    private static <T> void missing(ConfigStore store, Path dir, Class<T> cls, Set<String> tags, List<String> out) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) {
                String name = p.getFileName().toString().replaceFirst("\\.json$", "");
                if (store.schematic(dir, name) != null) continue;
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    T t = ConfigStore.GSON.fromJson(r, cls);
                    if (t instanceof Defs.Tagged tg && TagMatcher.matches(tg, tags)) out.add(name);
                } catch (Exception ignored) { }
            }
        } catch (IOException ignored) { }
    }

    /** Warns when the new module's height differs from the rest of its floor (the build uses the most common height). */
    private static void heightCheck(ConfigStore store, String name, List<String> notes) {
        Defs.WallModule me = store.walls.stream().filter(w -> w.name.equals(name)).findFirst().orElse(null);
        if (me == null) return;
        Set<String> tags = new HashSet<>(me.tags);
        for (String fl : me.allowedFloors) {
            Map<Integer, Integer> hc = new TreeMap<>();
            for (Defs.WallModule w : TagMatcher.all(store.walls, tags))
                if (!w.isCorner() && w.allowedFloors.contains(fl)) hc.merge(w.height, 1, Integer::sum);
            if (hc.size() > 1) notes.add("the " + fl + " floor's modules of these tags have heights " + hc + " (height: count); only the most common is used");
        }
    }

    private static boolean floors(JsonObject j, Map<String, String> opt, Report r) {
        if (opt.containsKey("floors")) {
            List<String> fl = new ArrayList<>();
            for (String s : opt.get("floors").split(",")) {
                String f = s.trim().toLowerCase(Locale.ROOT);
                if (!FLOORS.contains(f)) { r.err("floors are any of " + FLOORS + ", not " + f); return false; }
                fl.add(f);
            }
            j.add("allowed_floors", strings(fl));
        } else if (!j.has("allowed_floors")) j.add("allowed_floors", strings(List.of("ground", "upper")));
        return true;
    }

    /** 0.8.0: decoration=true|false, deco_width=N, deco_flavours=a,b, deco_chance=0..1, filler=N on a wall module. */
    private static boolean decorationKeys(JsonObject j, Map<String, String> opt, Report r, int len) {
        try {
            if (opt.containsKey("decoration")) j.addProperty("decoration", Boolean.parseBoolean(opt.get("decoration")));
            if (opt.containsKey("deco_width")) j.addProperty("decoration_max_width", Integer.parseInt(opt.get("deco_width")));
            if (opt.containsKey("deco_flavours")) j.add("decoration_flavours", strings(new ArrayList<>(TagMatcher.parse(opt.get("deco_flavours")))));
            if (opt.containsKey("deco_chance")) {
                double c = Double.parseDouble(opt.get("deco_chance"));
                if (c < 0 || c > 1) { r.err("deco_chance is 0 to 1"); return false; }
                j.addProperty("decoration_chance", c);
            }
            if (opt.containsKey("filler")) {
                int f = Integer.parseInt(opt.get("filler"));
                if (f >= len) { r.err("filler=" + f + " is past the module's " + len + " columns (0 = the left one as you face it, -1 = the middle)"); return false; }
                j.addProperty("filler_column", f);
            }
        } catch (NumberFormatException e) {
            r.err("not a number: " + e.getMessage());
            return false;
        }
        return true;
    }

    private static void common(JsonObject j, Map<String, String> opt) {
        if (opt.containsKey("weight")) j.addProperty("weight", Integer.parseInt(opt.get("weight")));
        else if (!j.has("weight")) j.addProperty("weight", 10);
        if (opt.containsKey("max")) j.addProperty("max_per_building", Integer.parseInt(opt.get("max")));
        else if (!j.has("max_per_building")) j.addProperty("max_per_building", 0);
    }

    /** Sets a fence preset's gate in fences.json, keeping everything else; null on success, else the problem. */
    private static String setGate(ConfigStore store, String fenceId, String name, Dir f) {
        Path p = store.root().resolve("fences.json");
        try {
            JsonElement e = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            if (!e.isJsonArray()) return "fences.json is not a list";
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonObject() && x.getAsJsonObject().has("id") && x.getAsJsonObject().get("id").getAsString().equals(fenceId)) {
                    x.getAsJsonObject().addProperty("gate_schematic", name + ".schem");
                    x.getAsJsonObject().addProperty("gate_front_facing", f.name());
                    Files.writeString(p, ConfigStore.GSON.toJson(e) + "\n", StandardCharsets.UTF_8);
                    return null;
                }
            }
            return "no fence preset with id " + fenceId + " in fences.json";
        } catch (Exception ex) {
            return "could not update fences.json: " + ex.getMessage();
        }
    }

    private static Dir parseDir(String s) {
        try { return Dir.parse(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static Dir[] parsePair(String s) {
        String[] p = s.split(",");
        if (p.length != 2) return null;
        Dir a = parseDir(p[0]), b = parseDir(p[1]);
        return a == null || b == null ? null : new Dir[]{a, b};
    }

    private static JsonArray strings(Collection<String> xs) {
        JsonArray a = new JsonArray();
        for (String x : xs) a.add(new JsonPrimitive(x));
        return a;
    }

}
