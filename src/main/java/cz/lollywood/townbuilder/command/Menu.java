package cz.lollywood.townbuilder.command;

import com.google.gson.reflect.TypeToken;
import cz.lollywood.townbuilder.TownBuilderPlugin;
import cz.lollywood.townbuilder.config.ConfigStore;
import cz.lollywood.townbuilder.config.Defs;
import cz.lollywood.townbuilder.config.TagMatcher;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 0.8.0: the menu (Marek 11-17: *"please consider adding some user-friendly menu to the Town plugin like we added to
 * Fallouťák, since Arag is going to use that a lot and just clicking is faster than writing instruction into console."*).
 * /plot (or the TownBuilder book, right click) opens it: the tags remembered per player, the three phases on the WorldEdit
 * selection with their results in a dialog (next phase, again with a new roll, undo), what the tags select, saving a piece
 * (a form per kind: sliders and ticks instead of key=value), pasting a schematic, reload, the placeholder kit.
 */
public final class Menu implements Listener {
    private final TownBuilderPlugin plugin;
    private final ConfigStore store;
    private final PlotCommand cmd;
    private final NamespacedKey bookKey;
    private final Path stateFile;
    /** each player's tags (uuid -> "city,union,residential"), kept in menu.json */
    private final Map<String, String> tags = new HashMap<>();

    Menu(TownBuilderPlugin plugin, ConfigStore store, PlotCommand cmd) {
        this.plugin = plugin;
        this.store = store;
        this.cmd = cmd;
        this.bookKey = new NamespacedKey(plugin, "menu_book");
        this.stateFile = plugin.getDataFolder().toPath().resolve("menu.json");
        try {
            if (Files.isRegularFile(stateFile)) {
                Map<String, String> m = ConfigStore.GSON.fromJson(Files.readString(stateFile, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType());
                if (m != null) tags.putAll(m);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("menu.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ state
    Set<String> tagsOf(Player p) {
        String t = tags.get(p.getUniqueId().toString());
        return t == null ? new LinkedHashSet<>() : TagMatcher.parse(t);
    }

    void remember(Player p, Set<String> t) {
        tags.put(p.getUniqueId().toString(), String.join(",", t));
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, ConfigStore.GSON.toJson(tags) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("menu.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ the book
    void giveItem(Player p) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("TownBuilder", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Right click: the TownBuilder menu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        p.getInventory().addItem(it);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        if (!e.getPlayer().hasPermission("townbuilder.use")) { e.getPlayer().sendMessage(Component.text("TownBuilder: no permission.", NamedTextColor.RED)); return; }
        main(e.getPlayer());
    }

    // ------------------------------------------------------------------ the main page
    void main(Player p) {
        Set<String> t = tagsOf(p);
        List<Component> lines = new ArrayList<>();
        lines.add(Ui.line(t.isEmpty() ? "Tags: none yet. Pick them first (Tags...)." : "Tags: " + String.join(", ", t),
                t.isEmpty() ? NamedTextColor.RED : NamedTextColor.GOLD));
        Report r = new Report();
        int[] sel = PlotCommand.selection(p, r);
        lines.add(sel == null ? Ui.line(r.lines.get(0).text(), NamedTextColor.RED)
                : Ui.line(String.format("Selection: %d x %d x %d blocks, from %d %d %d to %d %d %d", sel[3] - sel[0] + 1, sel[4] - sel[1] + 1, sel[5] - sel[2] + 1,
                sel[0], sel[1], sel[2], sel[3], sel[4], sel[5]), NamedTextColor.GRAY));
        lines.add(Ui.line("Mark the plot with redstone blocks (a gold block for each gate), select it with the WorldEdit wand, then "
                + "1 Outline, fix the coloured lines by hand if you like, 2 Build, 3 Foliage. Hover a button for what it does.", NamedTextColor.GRAY));
        List<ActionButton> b = new ArrayList<>();
        b.add(Ui.btn("1 Outline", "Clears plants on the plot and draws the coloured outlines on the ground: the house red with its front orange, "
                + "shed blue, pond cyan, garden green, fence yellow. You may move them by hand before building.", x -> phase(x, "outline")));
        b.add(Ui.btn("2 Build", "Builds what the coloured outlines show: the house from the wall modules (corners, decorations, roof), the shed, "
                + "pond, garden, the fence with its gate.", x -> phase(x, "build")));
        b.add(Ui.btn("3 Foliage", "Trees, bushes and clutter inside the fence.", x -> phase(x, "foliage")));
        b.add(Ui.btn("Undo", "WorldEdit's //undo: takes back your last change (a phase, a paste).", x -> { x.performCommand("/undo"); main(x); }));
        b.add(Ui.btn("Tags...", "Pick the tags: they choose the layout, the style, the fence, the wall modules, the decorations and the objects.", this::tagsPage));
        b.add(Ui.btn("What the tags select", "The layouts, style, fence, the modules of each floor (lengths, corners), the decorations and objects "
                + "these tags choose, and what is still missing.", this::info));
        b.add(Ui.btn("Save a piece...", "Your selection becomes a wall module, a corner, a decoration, an object or a gate (its .schem and .json).", this::saveKinds));
        if (p.isOp()) b.add(Ui.btn("Paste a schematic...", "Pastes any schematic under plugins/TownBuilder/ at your feet (old worlds, pieces to cut).", x -> pasteList(x, "")));
        b.add(Ui.btn("Reload files", "Reads the JSON files and the schematics again (after you edited them by hand).", x -> result(x, "Reload", cmd.reload(), List.of())));
        if (p.isOp()) b.add(Ui.btn("Placeholder kit", "/plot testkit: plain stand-in schematics for every .json that has no .schem yet (to try things before "
                + "the real modules exist).", x -> result(x, "Placeholder kit", cmd.testkit(x), List.of())));
        Ui.menu(p, "TownBuilder", lines, List.of(), b, null, 2);
    }

    // ------------------------------------------------------------------ the phases
    private void phase(Player p, String sub) {
        Set<String> t = tagsOf(p);
        Report r = new Report();
        if (t.isEmpty()) { result(p, "No tags", r.err("Pick the tags first (Tags... on the main page)."), List.of(Ui.btn("Tags...", "pick the tags", this::tagsPage))); return; }
        int[] sel = PlotCommand.selection(p, r);
        if (sel == null) { result(p, "No selection", r, List.of()); return; }
        cmd.runPhase(p, sub, t, sel, p.getWorld(), System.nanoTime(), r);
        List<ActionButton> next = new ArrayList<>();
        if (sub.equals("outline") && r.ok) next.add(Ui.btn("2 Build", "build what the outlines show", x -> phase(x, "build")));
        if (sub.equals("build") && r.ok) next.add(Ui.btn("3 Foliage", "trees, bushes and clutter inside the fence", x -> phase(x, "foliage")));
        next.add(Ui.btn("Again (new roll)", "undo this " + sub + " and run it again with a new random choice", x -> { x.performCommand("/undo"); phase(x, sub); }));
        next.add(Ui.btn("Undo", "WorldEdit's //undo: takes back this " + sub, x -> { x.performCommand("/undo"); main(x); }));
        String title = (sub.equals("outline") ? "1 Outline" : sub.equals("build") ? "2 Build" : "3 Foliage") + (r.ok ? ": done" : ": did not finish");
        result(p, title, r, next);
    }

    /** A command's result in a dialog: green what was done, yellow what to check, red what failed; then the buttons and Menu. */
    private void result(Player p, String title, Report r, List<ActionButton> buttons) {
        List<Component> lines = new ArrayList<>();
        int n = 0;
        for (Report.Line l : r.lines) {
            if (++n > 40) { lines.add(Ui.line("... " + (r.lines.size() - 40) + " more lines in the chat", NamedTextColor.GRAY)); break; }
            lines.add(Ui.line(l.text(), switch (l.kind()) {
                case OK -> NamedTextColor.GREEN;
                case INFO -> NamedTextColor.GRAY;
                case WARN -> NamedTextColor.YELLOW;
                case ERR -> NamedTextColor.RED;
            }));
        }
        if (r.lines.size() > 40) r.send(p);
        List<ActionButton> b = new ArrayList<>(buttons);
        b.add(Ui.btn("Menu", "the TownBuilder menu", this::main));
        Ui.menu(p, title, lines, List.of(), b, null, 2);
    }

    // ------------------------------------------------------------------ the tags
    /** Every tag the files use, with how many entries carry it. */
    private Map<String, Integer> knownTags() {
        Map<String, Integer> n = new TreeMap<>();
        List<Defs.Tagged> all = new ArrayList<>();
        all.addAll(store.layouts); all.addAll(store.styles); all.addAll(store.fences); all.addAll(store.walls); all.addAll(store.objects); all.addAll(store.decorations);
        for (Defs.Tagged e : all) for (String s : e.tags()) n.merge(s.trim().toLowerCase(Locale.ROOT), 1, Integer::sum);
        return n;
    }

    private void tagsPage(Player p) {
        Set<String> cur = tagsOf(p);
        Map<String, Integer> known = knownTags();
        List<String> keys = new ArrayList<>(known.keySet());
        for (String c : cur) if (!known.containsKey(c)) keys.add(c);
        List<DialogInput> in = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++)
            in.add(Ui.tick("t" + i, keys.get(i) + "  (" + known.getOrDefault(keys.get(i), 0) + ")", cur.contains(keys.get(i))));
        in.add(Ui.text("more", "Other tags (comma separated)", "", 200));
        List<ActionButton> b = new ArrayList<>();
        b.add(Ui.form("Use these", "use the ticked tags and the ones typed below them", (v, x) -> {
            Set<String> t = new LinkedHashSet<>();
            for (int i = 0; i < keys.size(); i++) if (Ui.on(v, "t" + i)) t.add(keys.get(i));
            t.addAll(TagMatcher.parse(Ui.str(v, "more")));
            remember(x, t);
            main(x);
        }));
        // the layouts' and styles' own tag sets, one click each
        Set<String> sets = new LinkedHashSet<>();
        for (Defs.PlotLayout l : store.layouts) if (!l.tags.isEmpty()) sets.add(String.join(",", TagMatcher.parse(String.join(",", l.tags))));
        for (Defs.BuildingStyle s : store.styles) if (!s.tags.isEmpty()) sets.add(String.join(",", TagMatcher.parse(String.join(",", s.tags))));
        for (String s : sets)
            b.add(Ui.btn(s.replace(",", ", "), "use exactly these tags (a layout's or a style's own set)", x -> { remember(x, TagMatcher.parse(s)); main(x); }));
        List<Component> lines = List.of(Ui.line("Tick the tags (the number = how many layouts, styles, fences, modules, decorations and objects carry "
                + "it), then Use these. An entry fits when its tags are all among yours, or yours all among its.", NamedTextColor.GRAY));
        Ui.menu(p, "Tags", lines, in, b, this::main, 2);
    }

    private void info(Player p) {
        Set<String> t = tagsOf(p);
        Report r = new Report();
        if (t.isEmpty()) r.err("Pick the tags first.");
        else SaveCommand.info(store, t, r);
        result(p, "What the tags select", r, List.of(Ui.btn("Tags...", "change the tags", this::tagsPage)));
    }

    // ------------------------------------------------------------------ saving a piece
    private static final Map<String, String> HOW = new LinkedHashMap<>();

    static {
        HOW.put("wall", "A wall module: select exactly the piece and stand IN FRONT of its facade (the street side). Its back row goes on the wall "
                + "line, anything deeper sticks out to the street.");
        HOW.put("outer_corner", "An outer corner: square seen from above; stand diagonally OUTSIDE it, where both its faces show. Offset: how many "
                + "blocks it sticks out of the walls (pillars).");
        HOW.put("inner_corner", "An inner corner: square seen from above; stand diagonally outside it, in the notch.");
        HOW.put("decoration", "A decoration (a planter, a ledge, a clothesline, stairs...): stand on its street side. It goes in front of wall "
                + "modules that allow decorations.");
        HOW.put("gate", "A fence gate: stand on its street side.");
        HOW.put("shed", "A shed for the plot (an object).");
        HOW.put("gazebo", "A gazebo for the plot (an object).");
        HOW.put("pond", "A pond (an object): y offset -1 sinks it level with the ground.");
        HOW.put("foliage", "A tree or a bush (an object for the foliage phase).");
        HOW.put("clutter", "Clutter: barrels, carts, woodpiles (an object for the foliage phase).");
    }

    private void saveKinds(Player p) {
        Ui.pick(p, "Save a piece", "Select the piece with the WorldEdit wand first, then stand where its kind says (hover) and pick the kind.",
                new ArrayList<>(HOW.keySet()), k -> k.replace('_', ' '), HOW::get, this::saveForm, this::main, "");
    }

    private String freeName(String kind, Set<String> t) {
        String base = (t.isEmpty() ? "" : t.iterator().next() + "_") + kind;
        Path dir = kind.equals("wall") || kind.endsWith("_corner") ? store.wallDir() : kind.equals("decoration") ? store.decorationDir() : store.objectDir();
        for (int i = 1; ; i++) if (!Files.exists(dir.resolve(base + "_" + i + ".json")) && store.schematic(dir, base + "_" + i) == null) return base + "_" + i;
    }

    private void saveForm(Player p, String kind) {
        Set<String> t = tagsOf(p);
        List<DialogInput> in = new ArrayList<>();
        in.add(Ui.text("name", "Name (letters, digits, _ and -)", freeName(kind, t), 64));
        in.add(Ui.text("tags", "Tags (comma separated)", String.join(",", t), 200));
        boolean module = kind.equals("wall") || kind.endsWith("_corner");
        if (module) {
            in.add(Ui.tick("f_basement", "Floor: basement", false));
            in.add(Ui.tick("f_ground", "Floor: ground", true));
            in.add(Ui.tick("f_upper", "Floor: upper", true));
        }
        switch (kind) {
            case "wall" -> {
                in.add(Ui.slider("max", "At most this many a building (0 = no limit)", 0, 20, 1, 0));
                in.add(Ui.slider("overhang", "Overhang or arcade: blocks into the house (0 = none)", 0, 4, 1, 0));
                in.add(Ui.tick("deco", "A decoration may go in front of it", false));
                in.add(Ui.slider("deco_width", "Widest decoration (0 = the module's length)", 0, 16, 1, 0));
                in.add(Ui.text("deco_flavours", "Only these decoration flavours (empty = any)", "", 100));
                in.add(Ui.slider("deco_chance", "How often a decoration, %", 0, 100, 5, 100));
                in.add(Ui.slider("filler", "Filler column from the left (-1 = the middle)", -1, 15, 1, -1));
            }
            case "outer_corner" -> in.add(Ui.slider("offset", "Offset: blocks it sticks out of the walls", 0, 4, 1, 0));
            case "decoration" -> {
                in.add(Ui.text("flavours", "Flavours (planter, ledge, clothesline, stairs...)", "", 100));
                in.add(Ui.slider("y_offset", "Y offset: blocks above the wall module's foot", -10, 10, 1, 0));
                in.add(Ui.slider("depth_offset", "Depth offset: 0 = just outside the wall, -1 = on the wall line", -1, 4, 1, 0));
            }
            case "gate" -> {
                List<Defs.FencePreset> fs = TagMatcher.all(store.fences, t);
                in.add(Ui.text("fence", "The fence preset (id in fences.json) that gets it", fs.isEmpty() ? "" : fs.get(0).id, 64));
            }
            case "shed", "gazebo", "pond", "foliage", "clutter" -> {
                in.add(Ui.slider("y_offset", "Y offset (a pond: -1)", -5, 5, 1, kind.equals("pond") ? -1 : 0));
                in.add(Ui.tick("rotate", "May be turned", true));
                in.add(Ui.slider("slope", "Steepest ground it stands on (blocks)", 0, 6, 1, 1));
            }
            default -> {}
        }
        if (!kind.equals("gate")) in.add(Ui.slider("weight", "Weight (how often it is chosen)", 1, 100, 1, 10));
        in.add(Ui.tick("overwrite", "Overwrite a piece of the same name", false));
        List<ActionButton> b = List.of(Ui.form("Save", "save the selection as this " + kind.replace('_', ' '), (v, x) -> doSave(x, kind, v)));
        List<Component> lines = List.of(Ui.line(HOW.get(kind), NamedTextColor.GRAY),
                Ui.line("The facing is read from where you stand now.", NamedTextColor.GRAY));
        Ui.menu(p, "Save: " + kind.replace('_', ' '), lines, in, b, this::saveKinds, 1);
    }

    private void doSave(Player p, String kind, DialogResponseView v) {
        List<String> a = new ArrayList<>(List.of("save", kind, Ui.str(v, "name")));
        String tg = Ui.str(v, "tags").replace(" ", "");
        if (!tg.isEmpty()) a.add(tg);
        if (kind.equals("wall") || kind.endsWith("_corner")) {
            List<String> fl = new ArrayList<>();
            for (String f : List.of("basement", "ground", "upper")) if (Ui.on(v, "f_" + f)) fl.add(f);
            if (fl.isEmpty()) { result(p, "Not saved", new Report().err("Tick at least one floor."), List.of(Ui.btn("Again", "back to the form", x -> saveForm(x, kind)))); return; }
            a.add("floors=" + String.join(",", fl));
        }
        switch (kind) {
            case "wall" -> {
                a.add("max=" + Ui.num(v, "max", 0));
                a.add("overhang=" + Ui.num(v, "overhang", 0));
                a.add("decoration=" + Ui.on(v, "deco"));
                a.add("deco_width=" + Ui.num(v, "deco_width", 0));
                String df = Ui.str(v, "deco_flavours").replace(" ", "");
                a.add("deco_flavours=" + df);
                a.add("deco_chance=" + Ui.num(v, "deco_chance", 100) / 100.0);
                a.add("filler=" + Ui.num(v, "filler", -1));
            }
            case "outer_corner" -> a.add("offset=" + Ui.num(v, "offset", 0));
            case "decoration" -> {
                String fl = Ui.str(v, "flavours").replace(" ", "");
                if (!fl.isEmpty()) a.add("flavours=" + fl);
                a.add("y_offset=" + Ui.num(v, "y_offset", 0));
                a.add("depth_offset=" + Ui.num(v, "depth_offset", 0));
            }
            case "gate" -> { if (!Ui.str(v, "fence").isEmpty()) a.add("fence=" + Ui.str(v, "fence")); }
            case "shed", "gazebo", "pond", "foliage", "clutter" -> {
                a.add("y_offset=" + Ui.num(v, "y_offset", 0));
                a.add("rotate=" + Ui.on(v, "rotate"));
                a.add("slope=" + Ui.num(v, "slope", 1));
            }
            default -> {}
        }
        if (!kind.equals("gate")) a.add("weight=" + Ui.num(v, "weight", 10));
        if (Ui.on(v, "overwrite")) a.add("overwrite");
        Report r = new Report();
        SaveCommand.save(store, cmd.paster, p, a.toArray(new String[0]), r);
        result(p, r.ok ? "Saved" : "Not saved", r, List.of(Ui.btn(r.ok ? "Save another" : "Back to the form", r.ok ? "save another piece" : "the form again",
                x -> { if (r.ok) saveKinds(x); else saveForm(x, kind); })));
    }

    // ------------------------------------------------------------------ pasting a schematic (operators)
    private void pasteList(Player p, String filter) {
        Path root = plugin.getDataFolder().toPath();
        List<String> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root, 6)) {
            s.filter(f -> Files.isRegularFile(f) && (f.toString().endsWith(".schem") || f.toString().endsWith(".schematic")))
                    .forEach(f -> files.add(root.relativize(f).toString().replace('\\', '/')));
        } catch (IOException e) {
            result(p, "Paste", new Report().err("cannot list the files: " + e.getMessage()), List.of());
            return;
        }
        Collections.sort(files);
        Ui.pick(p, "Paste a schematic", "It is pasted with its box's corner at your feet, air included (//undo reverts it).", files, f -> f,
                f -> "paste " + f + " at your feet", (x, f) -> {
                    org.bukkit.Location l = x.getLocation();
                    result(x, "Paste", cmd.pasteFile(x, root.resolve(f), x.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ()),
                            List.of(Ui.btn("Undo", "take the paste back", y -> { y.performCommand("/undo"); main(y); })));
                }, this::main, filter);
    }
}
