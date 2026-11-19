package cz.lollywood.townbuilder.phase;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import cz.lollywood.townbuilder.config.ConfigStore;
import cz.lollywood.townbuilder.world.Paster;
import cz.lollywood.townbuilder.world.Surface;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** What every phase gets: the world, the selection, the tags, the presets, one EditSession for //undo, the messages. */
public final class Kit {
    public final World world;
    public final int x0, y0, z0, x1, y1, z1;
    public final Set<String> tags;
    /** Clipboards made during the run (the filler columns), by name. */
    public final java.util.Map<String, com.sk89q.worldedit.extent.clipboard.Clipboard> clips = new java.util.HashMap<>();
    /** Wall columns closed by filler this run (outlines the modules could not make exactly). */
    public int fillerColumns;
    /** 0.8.0: decorations put in front of walls this run, and the modules that allowed one but found none to fit. */
    public int decorations;
    public final java.util.Set<String> decorationMissing = new java.util.TreeSet<>();
    public final ConfigStore store;
    public final Paster paster;
    public final Random rnd;
    public final Actor actor;
    public final LocalSession session;
    public final List<String> info = new ArrayList<>();
    public final List<String> warn = new ArrayList<>();
    private EditSession es;

    public Kit(World world, int[] sel, Set<String> tags, ConfigStore store, Paster paster, Actor actor, long seed) {
        this.world = world;
        this.x0 = Math.min(sel[0], sel[3]); this.y0 = Math.min(sel[1], sel[4]); this.z0 = Math.min(sel[2], sel[5]);
        this.x1 = Math.max(sel[0], sel[3]); this.y1 = Math.max(sel[1], sel[4]); this.z1 = Math.max(sel[2], sel[5]);
        this.tags = tags;
        this.store = store;
        this.paster = paster;
        this.actor = actor;
        this.session = WorldEdit.getInstance().getSessionManager().get(actor);
        this.rnd = new Random(seed);
    }

    public Surface surface() {
        return new Surface(world, x0, y0, z0, x1, y1, z1);
    }

    public EditSession es() {
        if (es == null) {
            es = WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(world)).actor(actor).build();
            // write straight through, so later steps read what earlier steps placed
            es.setReorderMode(EditSession.ReorderMode.NONE);
            es.setBatchingChunks(false);
        }
        return es;
    }

    private final java.util.Set<String> placed = new java.util.HashSet<>();
    private final java.util.Set<String> cleared = new java.util.HashSet<>();

    public void set(int x, int y, int z, BlockState s) throws WorldEditException {
        es().setBlock(BlockVector3.at(x, y, z), s);
        placed.add(x + "," + y + "," + z);
    }

    /**
     * Clears a cell (trees, grass, flowers) WITHOUT claiming it, so the roof may still be laid there (0.7.0: the house's clearing
     * used {@link #set}, which claimed the cells, so the roof skipped every cell a tree had stood in: Arag's hole in the roof).
     */
    public void clear(int x, int y, int z) throws WorldEditException {
        es().setBlock(BlockVector3.at(x, y, z), cz.lollywood.townbuilder.world.Blocks.air());
        cleared.add(x + "," + y + "," + z);
    }

    /** Free for the roof: nothing placed there by this phase, and air in the world or cleared by this phase. */
    public boolean freeForRoof(int x, int y, int z) {
        String key = x + "," + y + "," + z;
        return !placed.contains(key) && (cleared.contains(key) || world.getBlockAt(x, y, z).getType().isAir());
    }

    public BlockState get(int x, int y, int z) {
        return es().getBlock(BlockVector3.at(x, y, z));
    }

    public boolean isAir(int x, int y, int z) {
        return get(x, y, z).getBlockType().getMaterial().isAir();
    }

    /** Closes the session and puts it into the actor's WorldEdit history, so //undo reverts the whole phase. */
    public int finish() {
        if (es == null) return 0;
        es.close();
        session.remember(es);
        return es.getBlockChangeCount();
    }
}
