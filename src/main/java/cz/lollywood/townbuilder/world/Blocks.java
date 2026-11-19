package cz.lollywood.townbuilder.world;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockState;
import cz.lollywood.townbuilder.geom.Dir;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;

import java.util.Map;

/** Block strings from the JSON ("minecraft:oak_stairs[facing=north]") to WorldEdit states, and the ground tests. */
public final class Blocks {
    private Blocks() {}

    public static final Material MARKER = Material.REDSTONE_BLOCK;
    public static final Material GATE = Material.GOLD_BLOCK;
    public static final Material HOUSE = Material.RED_CONCRETE;
    public static final Material FRONT = Material.ORANGE_CONCRETE;
    public static final Material SHED = Material.BLUE_CONCRETE;
    public static final Material POND = Material.CYAN_CONCRETE;
    public static final Material GARDEN = Material.GREEN_CONCRETE;
    public static final Material FENCE = Material.YELLOW_CONCRETE;
    public static final Map<Material, String> OUTLINE = Map.of(HOUSE, "house", FRONT, "front", SHED, "shed", POND, "pond",
            GARDEN, "garden", FENCE, "fence", GATE, "gate");

    public static BlockData data(String s) {
        return Bukkit.createBlockData(s.trim());
    }

    public static BlockState state(String s) {
        return BukkitAdapter.adapt(data(s));
    }

    public static BlockState state(BlockData d) {
        return BukkitAdapter.adapt(d);
    }

    public static BlockState air() {
        return BukkitAdapter.adapt(Material.AIR.createBlockData());
    }

    public static BlockFace face(Dir d) {
        return switch (d) {
            case NORTH -> BlockFace.NORTH;
            case EAST -> BlockFace.EAST;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
        };
    }

    public static BlockState stairs(String type, Dir facing) {
        BlockData d = data(type);
        if (d instanceof Stairs s) {
            s.setFacing(face(facing));
            s.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
        }
        return state(d);
    }

    public static BlockState slab(String type) {
        BlockData d = data(type);
        if (d instanceof Slab s) s.setType(Slab.Type.BOTTOM);
        return state(d);
    }

    public static BlockData facing(String type, Dir facing) {
        BlockData d = data(type);
        if (d instanceof Directional dd && dd.getFaces().contains(face(facing))) dd.setFacing(face(facing));
        return d;
    }

    /** Leaves, logs and plants are not ground: the scan looks through them for the terrain. */
    public static boolean isFoliage(Material m) {
        return Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m)
                || Tag.REPLACEABLE.isTagged(m) || m == Material.VINE || m == Material.SNOW || m == Material.BAMBOO
                || m == Material.SUGAR_CANE || m == Material.CACTUS || m == Material.SWEET_BERRY_BUSH;
    }

    /** Natural ground the foliage pass may plant on. */
    public static boolean isGround(Material m) {
        return Tag.DIRT.isTagged(m) || m == Material.SAND || m == Material.RED_SAND || m == Material.GRAVEL
                || m == Material.SNOW_BLOCK || m == Material.CLAY;
    }

    public static boolean connects(BlockData d) {
        return d instanceof Wall || d instanceof MultipleFacing && Tag.FENCES.isTagged(d.getMaterial())
                || Tag.FENCE_GATES.isTagged(d.getMaterial());
    }

    /** Set a fence's or a wall's arms toward the given neighbours (WorldEdit does not update shapes). */
    public static BlockData connect(BlockData d, boolean n, boolean e, boolean s, boolean w, boolean post) {
        if (d instanceof Wall wall) {
            wall.setHeight(BlockFace.NORTH, n ? Wall.Height.LOW : Wall.Height.NONE);
            wall.setHeight(BlockFace.EAST, e ? Wall.Height.LOW : Wall.Height.NONE);
            wall.setHeight(BlockFace.SOUTH, s ? Wall.Height.LOW : Wall.Height.NONE);
            wall.setHeight(BlockFace.WEST, w ? Wall.Height.LOW : Wall.Height.NONE);
            int arms = (n ? 1 : 0) + (e ? 1 : 0) + (s ? 1 : 0) + (w ? 1 : 0);
            boolean straight = arms == 2 && ((n && s) || (e && w));
            wall.setUp(post || !straight);
        } else if (d instanceof MultipleFacing mf) {
            if (mf.getAllowedFaces().contains(BlockFace.NORTH)) mf.setFace(BlockFace.NORTH, n);
            if (mf.getAllowedFaces().contains(BlockFace.EAST)) mf.setFace(BlockFace.EAST, e);
            if (mf.getAllowedFaces().contains(BlockFace.SOUTH)) mf.setFace(BlockFace.SOUTH, s);
            if (mf.getAllowedFaces().contains(BlockFace.WEST)) mf.setFace(BlockFace.WEST, w);
        }
        return d;
    }
}
