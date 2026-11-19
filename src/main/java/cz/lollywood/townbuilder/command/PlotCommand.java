package cz.lollywood.townbuilder.command;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import cz.lollywood.townbuilder.TownBuilderPlugin;
import cz.lollywood.townbuilder.config.ConfigStore;
import cz.lollywood.townbuilder.config.TagMatcher;
import cz.lollywood.townbuilder.phase.BuildPhase;
import cz.lollywood.townbuilder.phase.FoliagePhase;
import cz.lollywood.townbuilder.phase.Kit;
import cz.lollywood.townbuilder.phase.OutlinePhase;
import cz.lollywood.townbuilder.world.Paster;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 0.8.0: /plot alone (or /plot menu) opens the MENU (Marek 11-17: *"please consider adding some user-friendly menu to the Town
 * plugin like we added to Fallouťák, since Arag is going to use that a lot"*), /plot item gives the book that opens it.
 * /plot outline|build|foliage &lt;tags&gt; on the player's WorldEdit cuboid; from the console (or for tests) add
 * {@code at <world> <x1> <y1> <z1> <x2> <y2> <z2>}; {@code seed <n>} repeats a result. /plot reload, /plot testkit,
 * /plot dump &lt;world&gt; &lt;x1 y1 z1 x2 y2 z2&gt; &lt;name&gt;; /plot save (a module or object from the selection), /plot info &lt;tags&gt;.
 */
public final class PlotCommand implements CommandExecutor, TabCompleter {
    private final TownBuilderPlugin plugin;
    private final ConfigStore store;
    final Paster paster = new Paster();
    final Menu menu;

    public PlotCommand(TownBuilderPlugin plugin, ConfigStore store) {
        this.plugin = plugin;
        this.store = store;
        this.menu = new Menu(plugin, store, this);
    }

    public Menu menu() { return menu; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) { menu.main(p); return true; }
            if (args.length == 0) return false;
            sender.sendMessage(ChatColor.RED + "The menu is for players in the game.");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "item" -> {
                if (sender instanceof Player p) { menu.giveItem(p); sender.sendMessage(ChatColor.GREEN + "Right click the TownBuilder book to open the menu."); }
                else sender.sendMessage(ChatColor.RED + "For players in the game.");
                return true;
            }
            case "reload" -> {
                reload().send(sender);
                return true;
            }
            case "testkit" -> {
                testkit(sender).send(sender);
                return true;
            }
            case "dump" -> {
                return dump(sender, args);
            }
            case "save" -> {
                Report r = new Report();
                SaveCommand.save(store, paster, sender, args, r);
                r.send(sender);
                return true;
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "/plot info <tags>"); return true; }
                Report r = new Report();
                Set<String> tags = TagMatcher.parse(String.join(",", Arrays.asList(args).subList(1, args.length)));
                if (sender instanceof Player p) menu.remember(p, tags);
                SaveCommand.info(store, tags, r);
                r.send(sender);
                return true;
            }
            case "outline", "build", "foliage" -> {
                return phase(sender, sub, args);
            }
            case "paste" -> {
                return paste(sender, args);
            }
            case "undo" -> {
                if (sender instanceof Player p) p.performCommand("/undo");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean phase(CommandSender sender, String sub, String[] args) {
        List<String> tagWords = new ArrayList<>();
        int[] sel = null;
        World world = null;
        long seed = System.nanoTime();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("at") && i + 7 < args.length) {
                world = Bukkit.getWorld(args[i + 1]);
                sel = new int[6];
                for (int j = 0; j < 6; j++) sel[j] = Integer.parseInt(args[i + 2 + j]);
                i += 7;
            } else if (args[i].equalsIgnoreCase("seed") && i + 1 < args.length) {
                seed = Long.parseLong(args[++i]);
            } else tagWords.add(args[i]);
        }
        if (tagWords.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Give the tags, e.g. /plot " + sub + " city,union,residential");
            return true;
        }
        Set<String> tags = TagMatcher.parse(String.join(",", tagWords));
        Report r = new Report();
        if (sel == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "From the console add: at <world> <x1> <y1> <z1> <x2> <y2> <z2>");
                return true;
            }
            sel = selection(player, r);
            if (sel == null) { r.send(sender); return true; }
            world = player.getWorld();
            menu.remember(player, tags);
        }
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "No such world.");
            return true;
        }
        runPhase(sender, sub, tags, sel, world, seed, r).send(sender);
        return true;
    }

    /** The player's WorldEdit cuboid as {x1 y1 z1 x2 y2 z2}, or null with the reason in {@code r}. */
    static int[] selection(Player player, Report r) {
        Region region;
        try {
            region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException e) {
            r.err("Make a WorldEdit selection first (//wand, then two corners).");
            return null;
        }
        if (!(region instanceof CuboidRegion)) {
            r.err("The selection must be a cuboid (//sel cuboid).");
            return null;
        }
        BlockVector3 a = region.getMinimumPoint(), b = region.getMaximumPoint();
        return new int[]{a.x(), a.y(), a.z(), b.x(), b.y(), b.z()};
    }

    /** Runs one phase; what it says goes into {@code r} (the chat or the menu shows it). */
    Report runPhase(CommandSender sender, String sub, Set<String> tags, int[] sel, World world, long seed, Report r) {
        Actor actor = BukkitAdapter.adapt(sender);
        Kit k = new Kit(world, sel, tags, store, paster, actor, seed);
        boolean ok;
        try {
            ok = switch (sub) {
                case "outline" -> OutlinePhase.run(k);
                case "build" -> BuildPhase.run(k);
                default -> FoliagePhase.run(k);
            };
        } catch (Exception e) {
            ok = false;
            k.warn.add("stopped by an error: " + e);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "/plot " + sub, e);
        }
        int changed = k.finish();
        if (k.fillerColumns > 0) k.info.add(k.fillerColumns + " wall column(s) closed with filler cut from a plain module (an outline the modules' lengths could not make exactly)");
        if (k.decorations > 0) k.info.add(k.decorations + " decoration(s) in front of the walls");
        if (!k.decorationMissing.isEmpty()) k.warn.add("no decoration of these tags fits in front of " + k.decorationMissing);
        for (String s : k.info) r.ok(s);
        for (String s : k.warn) r.warn(s);
        String last = "/plot " + sub + (ok ? " done" : " did not finish") + ": " + changed + " blocks changed (//undo reverts it). seed " + seed;
        if (ok) r.ok(last); else r.err(last);
        r.seed = seed;
        return r;
    }

    Report reload() {
        Report r = new Report();
        store.load();
        paster.clear();
        r.ok(String.format("TownBuilder reloaded: %d layouts, %d fences, %d styles, %d wall modules, %d objects, %d decorations.",
                store.layouts.size(), store.fences.size(), store.styles.size(), store.walls.size(), store.objects.size(), store.decorations.size()));
        for (String p : store.problems) r.warn("  " + p);
        return r;
    }

    Report testkit(CommandSender sender) {
        Report r = new Report();
        if (sender instanceof Player p && !p.isOp()) return r.err("Operators only.");
        try {
            List<String> made = TestKit.run(store);
            store.load();
            paster.clear();
            r.ok("Placeholder schematics written: " + made.size() + " files: " + made);
        } catch (Exception e) {
            r.err("testkit failed: " + e);
        }
        return r;
    }

    /**
     * 0.7.0: pastes any schematic WorldEdit can read (.schem, or an old MCEdit .schematic, whose legacy block ids WorldEdit converts)
     * with its box's minimum corner at the point given, air included, into the sender's WorldEdit history (//undo reverts it). The
     * file is a path under plugins/TownBuilder/. Operators only. Used to bring pieces of old LollyWood worlds onto a test server, to
     * cut modules from them with /plot save. /plot paste &lt;file&gt; at &lt;world&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; (a player may leave out the
     * "at ...": his own position).
     */
    private boolean paste(CommandSender sender, String[] args) {
        if (sender instanceof Player p && !p.isOp()) { sender.sendMessage(ChatColor.RED + "Operators only."); return true; }
        if (args.length < 2) { sender.sendMessage("/plot paste <file under plugins/TownBuilder/> [at <world> <x> <y> <z>]"); return true; }
        Path file = plugin.getDataFolder().toPath().resolve(args[1]).normalize();
        if (!file.startsWith(plugin.getDataFolder().toPath().normalize()) || !Files.isRegularFile(file)) {
            sender.sendMessage(ChatColor.RED + "No such file under plugins/TownBuilder/: " + args[1]);
            return true;
        }
        World w;
        int x, y, z;
        if (args.length >= 7 && args[2].equalsIgnoreCase("at")) {
            w = Bukkit.getWorld(args[3]);
            if (w == null) { sender.sendMessage(ChatColor.RED + "No world " + args[3]); return true; }
            x = Integer.parseInt(args[4]); y = Integer.parseInt(args[5]); z = Integer.parseInt(args[6]);
        } else if (sender instanceof Player p) {
            w = p.getWorld();
            x = p.getLocation().getBlockX(); y = p.getLocation().getBlockY(); z = p.getLocation().getBlockZ();
        } else {
            sender.sendMessage(ChatColor.RED + "From the console add: at <world> <x> <y> <z>");
            return true;
        }
        pasteFile(sender, file, w, x, y, z).send(sender);
        return true;
    }

    Report pasteFile(CommandSender sender, Path file, World w, int x, int y, int z) {
        Report r = new Report();
        Actor actor = BukkitAdapter.adapt(sender);
        try {
            com.sk89q.worldedit.extent.clipboard.Clipboard c = paster.load(file);
            com.sk89q.worldedit.EditSession es = WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(w)).actor(actor).build();
            try (es) {
                Paster.paste(es, c, 0, com.sk89q.worldedit.math.BlockVector3.at(x, y, z), false);
            }
            WorldEdit.getInstance().getSessionManager().get(actor).remember(es);
            com.sk89q.worldedit.math.BlockVector3 d = c.getDimensions();
            r.ok("Pasted " + file.getFileName() + " (" + d.x() + " x " + d.y() + " x " + d.z() + ") at " + x + " " + y + " " + z
                    + ": " + es.getBlockChangeCount() + " blocks (//undo reverts it).");
        } catch (Exception e) {
            r.err("paste failed: " + e);
        }
        return r;
    }

    /** A debug dump of blocks to plugins/TownBuilder/dumps/&lt;name&gt;.json (palette + indices, x fastest, then z, then y). */
    private boolean dump(CommandSender sender, String[] args) {
        if (args.length < 9) { sender.sendMessage("/plot dump <world> <x1> <y1> <z1> <x2> <y2> <z2> <name>"); return true; }
        World w = Bukkit.getWorld(args[1]);
        if (w == null) { sender.sendMessage("no world " + args[1]); return true; }
        int[] c = new int[6];
        for (int i = 0; i < 6; i++) c[i] = Integer.parseInt(args[2 + i]);
        int x0 = Math.min(c[0], c[3]), y0 = Math.min(c[1], c[4]), z0 = Math.min(c[2], c[5]);
        int x1 = Math.max(c[0], c[3]), y1 = Math.max(c[1], c[4]), z1 = Math.max(c[2], c[5]);
        Map<String, Integer> pal = new LinkedHashMap<>();
        StringBuilder data = new StringBuilder();
        for (int y = y0; y <= y1; y++)
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) {
                    String b = w.getBlockAt(x, y, z).getBlockData().getAsString();
                    int id = pal.computeIfAbsent(b, q -> pal.size());
                    if (data.length() > 0) data.append(',');
                    data.append(id);
                }
        Path dir = plugin.getDataFolder().toPath().resolve("dumps");
        try {
            Files.createDirectories(dir);
            try (Writer out = Files.newBufferedWriter(dir.resolve(args[8] + ".json"), StandardCharsets.UTF_8)) {
                out.write("{\"origin\":[" + x0 + "," + y0 + "," + z0 + "],\"size\":[" + (x1 - x0 + 1) + "," + (y1 - y0 + 1) + "," + (z1 - z0 + 1)
                        + "],\"palette\":" + ConfigStore.GSON.toJson(new ArrayList<>(pal.keySet())).replace("\n", "") + ",\"data\":[" + data + "]}");
            }
            sender.sendMessage("dumped " + pal.size() + " block kinds to dumps/" + args[8] + ".json");
        } catch (IOException e) {
            sender.sendMessage("dump failed: " + e);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("menu", "item", "outline", "build", "foliage", "undo", "save", "info", "reload", "testkit", "dump", "paste");
        if (args.length == 2 && args[0].equalsIgnoreCase("save")) return SaveCommand.KINDS;
        if (args.length >= 4 && args[0].equalsIgnoreCase("save"))
            return List.of("floors=", "weight=", "max=", "facing=", "outside=", "offset=", "overhang=", "decoration=", "deco_width=", "deco_flavours=",
                    "deco_chance=", "filler=", "flavours=", "depth_offset=", "y_offset=", "rotate=", "slope=", "fence=", "overwrite");
        return List.of();
    }
}
