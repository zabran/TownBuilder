package cz.lollywood.townbuilder.command;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import cz.lollywood.townbuilder.config.ConfigStore;
import cz.lollywood.townbuilder.config.Defs;
import cz.lollywood.townbuilder.world.Blocks;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * /plot testkit: plain PLACEHOLDER schematics for every sidecar that has none yet, so the plugin can be tried before the real
 * modules are built (planks walls, a glass window with a sill in front, a door, log corners, a box shed, a pond, an oak).
 * Saved facing NORTH (the facade at the low z side), as the README tells the builders to save theirs.
 */
final class TestKit {
    private TestKit() {}

    static List<String> run(ConfigStore store) throws IOException, WorldEditException {
        List<String> made = new ArrayList<>();
        for (Path dir : List.of(store.wallDir(), store.objectDir(), store.decorationDir())) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    String name = p.getFileName().toString().replaceFirst("\\.json$", "");
                    if (store.schematic(dir, name) != null) continue;
                    String json = Files.readString(p);
                    BlockArrayClipboard cb = dir.equals(store.wallDir())
                            ? wall(ConfigStore.GSON.fromJson(json, Defs.WallModule.class))
                            : dir.equals(store.decorationDir()) ? decoration(ConfigStore.GSON.fromJson(json, Defs.Decoration.class))
                            : object(ConfigStore.GSON.fromJson(json, Defs.ObjectDef.class));
                    try (ClipboardWriter w = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(dir.resolve(name + ".schem").toFile()))) {
                        w.write(cb);
                    }
                    made.add(name);
                }
            }
        }
        // the fence presets' gate schematics (they have no sidecar): a gate between two posts, saved facing NORTH
        for (Defs.FencePreset f : store.fences) {
            if (f.gateSchematic == null || f.gateSchematic.isBlank()) continue;
            String name = f.gateSchematic.replaceFirst("\\.schem(atic)?$", "");
            if (!Files.isDirectory(store.objectDir()) || store.schematic(store.objectDir(), name) != null) continue;
            BlockArrayClipboard cb = box(3, f.height + 1, 1);
            for (int y = 0; y <= f.height; y++) {
                put(cb, 0, y, 0, y < f.height ? "minecraft:stone_bricks" : "minecraft:lantern");
                put(cb, 2, y, 0, y < f.height ? "minecraft:stone_bricks" : "minecraft:lantern");
                put(cb, 1, y, 0, y == 0 ? "minecraft:spruce_fence_gate[facing=north]" : y < f.height ? "minecraft:air" : "minecraft:spruce_slab");
            }
            try (ClipboardWriter w = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(store.objectDir().resolve(name + ".schem").toFile()))) {
                w.write(cb);
            }
            made.add(name);
        }
        return made;
    }

    private static BlockArrayClipboard box(int w, int h, int d) {
        BlockArrayClipboard cb = new BlockArrayClipboard(new CuboidRegion(BlockVector3.at(0, 0, 0), BlockVector3.at(w - 1, h - 1, d - 1)));
        cb.setOrigin(BlockVector3.at(0, 0, 0));
        return cb;
    }

    private static void put(BlockArrayClipboard cb, int x, int y, int z, String block) throws WorldEditException {
        cb.setBlock(BlockVector3.at(x, y, z), Blocks.state(block));
    }

    /** 0.8.0: a decoration's stand-in, one row deep, saved facing NORTH: pots (planter), top slabs (ledge), else barrels. */
    private static BlockArrayClipboard decoration(Defs.Decoration d) throws WorldEditException {
        int w = Math.max(1, d.width), h = Math.max(1, d.height);
        BlockArrayClipboard cb = box(w, h, 1);
        String[] pots = {"minecraft:potted_red_tulip", "minecraft:potted_dandelion", "minecraft:potted_cornflower"};
        boolean planter = d.flavours.stream().anyMatch(f -> f.equalsIgnoreCase("planter")), ledge = d.flavours.stream().anyMatch(f -> f.equalsIgnoreCase("ledge"));
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                put(cb, x, y, 0, y > 0 ? "minecraft:air" : planter ? pots[x % 3] : ledge ? "minecraft:spruce_slab[type=top]" : "minecraft:barrel[facing=north]");
        return cb;
    }

    private static BlockArrayClipboard wall(Defs.WallModule m) throws WorldEditException {
        int L = m.length, H = m.height;
        boolean cellar = m.allowedFloors.contains("basement") && !m.allowedFloors.contains("ground");
        if (m.isCorner()) {
            // 0.8.0: an outer corner with an offset is (length + offset) square: a log on the wall line, stone pillars sticking out
            int o = "outer_corner".equals(m.moduleType) ? Math.max(0, m.offset) : 0;
            if (o > 0) {
                int S = Math.max(1, m.length) + o;
                BlockArrayClipboard cb = box(S, H, S);
                // saved facing NORTH: its faces look north and west (front_facing NORTH = the corner's faces NORTH and WEST, as /plot save makes it)
                for (int x = 0; x < S; x++)
                    for (int z = 0; z < S; z++)
                        for (int y = 0; y < H; y++) {
                            String b = "minecraft:air";
                            if ((x == o && z >= o) || (z == o && x >= o)) b = "minecraft:stripped_spruce_log";
                            else if ((x < o && z == o) || (z < o && x == o)) b = cellar ? "minecraft:chiseled_stone_bricks" : "minecraft:cobblestone";
                            put(cb, x, y, z, b);
                        }
                return cb;
            }
            BlockArrayClipboard cb = box(1, H, 1);
            for (int y = 0; y < H; y++) put(cb, 0, y, 0, cellar ? "minecraft:chiseled_stone_bricks" : "minecraft:stripped_spruce_log");
            return cb;
        }
        if (cellar) {   // stone, a barred window or a door; the facade row (z = 0) empty
            BlockArrayClipboard cb = box(L, H, 2);
            for (int x = 0; x < L; x++)
                for (int y = 0; y < H; y++) {
                    put(cb, x, y, 0, "minecraft:air");
                    put(cb, x, y, 1, y == H - 1 ? "minecraft:polished_andesite" : "minecraft:stone_bricks");
                }
            int mid = (L - 1) / 2;
            if (m.hasDoor) {
                put(cb, mid, 0, 1, "minecraft:spruce_door[facing=south,half=lower]");
                put(cb, mid, 1, 1, "minecraft:spruce_door[facing=south,half=upper]");
            } else if (m.hasWindow) put(cb, mid, H - 2, 1, "minecraft:iron_bars");
            return cb;
        }
        // two rows deep: z = 1 is the wall itself (its back), z = 0 the facade side, empty but for a window sill
        BlockArrayClipboard cb = box(L, H, 2);
        for (int x = 0; x < L; x++)
            for (int y = 0; y < H; y++) {
                put(cb, x, y, 0, "minecraft:air");
                put(cb, x, y, 1, y == H - 1 ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks");
            }
        int mid = (L - 1) / 2;
        if (m.hasDoor) {
            put(cb, mid, 0, 1, "minecraft:spruce_door[facing=south,half=lower]");
            put(cb, mid, 1, 1, "minecraft:spruce_door[facing=south,half=upper]");
        } else if (m.hasWindow) {
            for (int x = L >= 3 ? 1 : 0; x <= (L >= 3 ? L - 2 : L - 1); x++) {
                put(cb, x, 1, 1, "minecraft:glass_pane");
                if (H > 3) put(cb, x, 2, 1, "minecraft:glass_pane");
                put(cb, x, 0, 0, "minecraft:spruce_trapdoor[facing=south,half=top]");
            }
        }
        return cb;
    }

    private static BlockArrayClipboard object(Defs.ObjectDef o) throws WorldEditException {
        int w = Math.max(1, o.footprintX), d = Math.max(1, o.footprintZ);
        switch (o.category) {
            case "pond" -> {
                BlockArrayClipboard cb = box(w, 1, d);
                for (int x = 0; x < w; x++)
                    for (int z = 0; z < d; z++)
                        put(cb, x, 0, z, (x == 0 || z == 0 || x == w - 1 || z == d - 1) ? "minecraft:mossy_cobblestone" : "minecraft:water");
                return cb;
            }
            case "foliage" -> {
                if (w == 1 && d == 1) {
                    BlockArrayClipboard cb = box(1, 1, 1);
                    put(cb, 0, 0, 0, "minecraft:oak_leaves[persistent=true]");
                    return cb;
                }
                BlockArrayClipboard cb = box(w, 5, d);
                int cx = w / 2, cz = d / 2;
                for (int x = 0; x < w; x++)
                    for (int z = 0; z < d; z++)
                        for (int y = 2; y <= 4; y++)
                            if (!(x == cx && z == cz && y < 4)) put(cb, x, y, z, "minecraft:oak_leaves[persistent=true]");
                for (int y = 0; y < 4; y++) put(cb, cx, y, cz, "minecraft:oak_log");
                return cb;
            }
            case "clutter" -> {
                BlockArrayClipboard cb = box(w, 1, d);
                for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) put(cb, x, 0, z, "minecraft:barrel[facing=up]");
                return cb;
            }
            default -> {   // shed, gazebo: a small box, its door on the north side
                boolean gazebo = o.category.equals("gazebo");
                BlockArrayClipboard cb = box(w, 4, d);
                for (int x = 0; x < w; x++)
                    for (int z = 0; z < d; z++)
                        for (int y = 0; y < 4; y++) {
                            boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                            boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                            String b = "minecraft:air";
                            if (y == 3) b = gazebo ? "minecraft:spruce_slab" : "minecraft:spruce_planks";
                            else if (gazebo) b = corner ? "minecraft:spruce_fence" : "minecraft:air";
                            else if (edge) b = "minecraft:oak_planks";
                            put(cb, x, y, z, b);
                        }
                if (!gazebo) {
                    put(cb, w / 2, 0, 0, "minecraft:oak_door[facing=south,half=lower]");
                    put(cb, w / 2, 1, 0, "minecraft:oak_door[facing=south,half=upper]");
                }
                return cb;
            }
        }
    }
}
