package cz.lollywood.townbuilder.world;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import cz.lollywood.townbuilder.geom.Dir;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Pastes a schematic turned by quarter turns so that its BOX lands at a given minimum corner (where the builder stood when
 * copying does not matter). Clipboards are cached by file until /plot reload.
 */
public final class Paster {
    private final Map<Path, Clipboard> cache = new HashMap<>();

    public void clear() { cache.clear(); }

    public Clipboard load(Path file) throws IOException {
        Clipboard c = cache.get(file);
        if (c != null) return c;
        ClipboardFormat f = ClipboardFormats.findByFile(file.toFile());
        if (f == null) throw new IOException("not a schematic WorldEdit knows: " + file.getFileName());
        try (InputStream in = new FileInputStream(file.toFile()); ClipboardReader r = f.getReader(in)) {
            c = r.read();
        }
        cache.put(file, c);
        return c;
    }

    /** The clipboard's size after {@code turns} clockwise quarter turns: {x, y, z}. */
    public static int[] size(Clipboard c, int turns) {
        BlockVector3 d = c.getDimensions();
        return turns % 2 == 0 ? new int[]{d.x(), d.y(), d.z()} : new int[]{d.z(), d.y(), d.x()};
    }

    /** Quarter turns that bring a schematic's {@code front} onto {@code target}. */
    public static int turns(Dir front, Dir target) {
        return front.quarterTurnsTo(target);
    }

    /** Pastes so the turned box's minimum corner is at {@code min}; air in the schematic is pasted too unless skipAir. */
    public static void paste(EditSession es, Clipboard c, int turns, BlockVector3 min, boolean skipAir) throws WorldEditException {
        // WorldEdit's own //rotate N passes rotateY(-N): a negative angle turns clockwise seen from above
        AffineTransform t = new AffineTransform().rotateY(-90.0 * turns);
        BlockVector3 o = c.getOrigin();
        BlockVector3 a = c.getRegion().getMinimumPoint().subtract(o), b = c.getRegion().getMaximumPoint().subtract(o);
        double mx = Double.MAX_VALUE, my = Double.MAX_VALUE, mz = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3 p = t.apply(Vector3.at((i & 1) == 0 ? a.x() : b.x(), (i & 2) == 0 ? a.y() : b.y(), (i & 4) == 0 ? a.z() : b.z()));
            mx = Math.min(mx, p.x()); my = Math.min(my, p.y()); mz = Math.min(mz, p.z());
        }
        BlockVector3 to = min.subtract((int) Math.round(mx), (int) Math.round(my), (int) Math.round(mz));
        ClipboardHolder h = new ClipboardHolder(c);
        h.setTransform(t);
        Operations.complete(h.createPaste(es).to(to).ignoreAirBlocks(skipAir).build());
    }

    /**
     * The middle one-block slice of a wall module across its length (its facade looks {@code front} as saved), as a clipboard of
     * its own with the same origin, so it pastes like a module one block long.
     */
    public static Clipboard sliceMiddle(Clipboard src, Dir front) throws WorldEditException {
        return slice(src, front, -1);
    }

    /**
     * 0.8.0: the one-block slice at column {@code col} counted from the LEFT as you face the facade (-1 = the middle); a column past
     * the end is the last one. Seen from the street a facade looking NORTH has its left at the east end, EAST at the south end,
     * SOUTH at the west end, WEST at the north end.
     */
    public static Clipboard slice(Clipboard src, Dir front, int col) throws WorldEditException {
        BlockVector3 a = src.getRegion().getMinimumPoint(), b = src.getRegion().getMaximumPoint();
        boolean alongX = front == Dir.NORTH || front == Dir.SOUTH;
        int len = alongX ? b.x() - a.x() + 1 : b.z() - a.z() + 1;
        int mid = col < 0 ? len / 2 : leftToMin(front, Math.min(col, len - 1), len);
        CuboidRegion r = alongX
                ? new CuboidRegion(BlockVector3.at(a.x() + mid, a.y(), a.z()), BlockVector3.at(a.x() + mid, b.y(), b.z()))
                : new CuboidRegion(BlockVector3.at(a.x(), a.y(), a.z() + mid), BlockVector3.at(b.x(), b.y(), a.z() + mid));
        BlockArrayClipboard out = new BlockArrayClipboard(r);
        out.setOrigin(src.getOrigin());
        for (BlockVector3 q : r) out.setBlock(q, src.getFullBlock(q));
        return out;
    }

    /** A column counted from the left as you face the facade, as an index from the box's low x (or z) end. */
    public static int leftToMin(Dir front, int fromLeft, int len) {
        // the viewer stands on the facade's side looking at it: facing NORTH he looks south, his left is east (+x)
        return switch (front) {
            case NORTH, EAST -> len - 1 - fromLeft;
            case SOUTH, WEST -> fromLeft;
        };
    }

    /**
     * Where each non-air block of the same paste lands (world position -> the block's id), computed from the clipboard: the
     * EditSession does not show a phase its own pastes when read back, so a phase that needs them (the steps to a door) asks here.
     */
    public static Map<BlockVector3, String> landing(Clipboard c, int turns, BlockVector3 min) {
        AffineTransform t = new AffineTransform().rotateY(-90.0 * turns);
        BlockVector3 o = c.getOrigin();
        BlockVector3 a = c.getRegion().getMinimumPoint().subtract(o), b = c.getRegion().getMaximumPoint().subtract(o);
        double mx = Double.MAX_VALUE, my = Double.MAX_VALUE, mz = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3 p = t.apply(Vector3.at((i & 1) == 0 ? a.x() : b.x(), (i & 2) == 0 ? a.y() : b.y(), (i & 4) == 0 ? a.z() : b.z()));
            mx = Math.min(mx, p.x()); my = Math.min(my, p.y()); mz = Math.min(mz, p.z());
        }
        BlockVector3 to = min.subtract((int) Math.round(mx), (int) Math.round(my), (int) Math.round(mz));
        Map<BlockVector3, String> out = new HashMap<>();
        for (BlockVector3 p : c.getRegion()) {
            String id = c.getBlock(p).getBlockType().id();
            if (id.endsWith(":air") || id.endsWith("_air")) continue;
            BlockVector3 r = p.subtract(o);
            Vector3 q = t.apply(Vector3.at(r.x(), r.y(), r.z()));
            out.put(to.add((int) Math.round(q.x()), (int) Math.round(q.y()), (int) Math.round(q.z())), id);
        }
        return out;
    }
}
