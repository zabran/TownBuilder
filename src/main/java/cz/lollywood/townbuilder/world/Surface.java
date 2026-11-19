package cz.lollywood.townbuilder.world;

import cz.lollywood.townbuilder.geom.PlotPolygon;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * The top solid, non-foliage block of every column of the selection (looking down from the selection's top), so the scan sees
 * the terrain and the builder's markers through grass, flowers and trees.
 */
public final class Surface {
    public final int minX, minZ, maxX, maxZ, minY, maxY;
    private final Map<Long, Integer> top = new HashMap<>();
    private final Map<Long, Material> type = new HashMap<>();

    public Surface(World w, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ; this.minY = minY; this.maxY = maxY;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = maxY; y >= minY; y--) {
                    Material m = w.getBlockAt(x, y, z).getType();
                    if (m.isAir() || Blocks.isFoliage(m) || m == Material.WATER || m == Material.LAVA) {
                        if (m == Material.WATER) { put(x, z, y, m); break; }
                        continue;
                    }
                    put(x, z, y, m);
                    break;
                }
            }
    }

    private void put(int x, int z, int y, Material m) {
        long k = PlotPolygon.key(x, z);
        top.put(k, y);
        type.put(k, m);
    }

    public boolean has(int x, int z) { return top.containsKey(PlotPolygon.key(x, z)); }
    /** The top block's y, or Integer.MIN_VALUE if the column is empty inside the selection. */
    public int y(int x, int z) { return top.getOrDefault(PlotPolygon.key(x, z), Integer.MIN_VALUE); }
    public Material type(int x, int z) { return type.get(PlotPolygon.key(x, z)); }
    public void set(int x, int z, int y, Material m) { put(x, z, y, m); }
    public boolean inside(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
}
