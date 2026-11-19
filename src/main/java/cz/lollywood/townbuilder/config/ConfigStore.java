package cz.lollywood.townbuilder.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Everything under plugins/TownBuilder/: the three preset files and the two schematic folders, each .schem with a sidecar
 * .json of the same name. Pure Java (no Bukkit), so the tests can load the shipped examples.
 */
public final class ConfigStore {
    public static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final Path root;
    private final Logger log;
    public final List<Defs.PlotLayout> layouts = new ArrayList<>();
    public final List<Defs.FencePreset> fences = new ArrayList<>();
    public final List<Defs.BuildingStyle> styles = new ArrayList<>();
    public final List<Defs.WallModule> walls = new ArrayList<>();
    public final List<Defs.ObjectDef> objects = new ArrayList<>();
    public final List<Defs.Decoration> decorations = new ArrayList<>();
    /** Problems found while loading, shown by /plot reload. */
    public final List<String> problems = new ArrayList<>();

    public ConfigStore(Path root, Logger log) {
        this.root = root;
        this.log = log;
    }

    public Path root() { return root; }
    public Path wallDir() { return root.resolve("schematics").resolve("wall_modules"); }
    public Path objectDir() { return root.resolve("schematics").resolve("objects"); }
    public Path decorationDir() { return root.resolve("schematics").resolve("decorations"); }

    public void load() {
        layouts.clear(); fences.clear(); styles.clear(); walls.clear(); objects.clear(); decorations.clear(); problems.clear();
        layouts.addAll(readList("plot_layouts.json", new TypeToken<List<Defs.PlotLayout>>() {}.getType()));
        fences.addAll(readList("fences.json", new TypeToken<List<Defs.FencePreset>>() {}.getType()));
        styles.addAll(readList("building_styles.json", new TypeToken<List<Defs.BuildingStyle>>() {}.getType()));
        readSidecars(wallDir(), Defs.WallModule.class, walls);
        readSidecars(objectDir(), Defs.ObjectDef.class, objects);
        if (Files.isDirectory(decorationDir())) readSidecars(decorationDir(), Defs.Decoration.class, decorations);
        for (Defs.WallModule w : walls) {
            if (w.length < 1 || w.height < 1) problems.add("wall module " + w.name + ": length and height must be at least 1");
            if (!List.of("wall", "outer_corner", "inner_corner").contains(w.moduleType))
                problems.add("wall module " + w.name + ": unknown module_type " + w.moduleType);
            if (w.offset < 0) problems.add("wall module " + w.name + ": offset must be 0 or more");
            if (w.offset > 0 && !"outer_corner".equals(w.moduleType)) problems.add("wall module " + w.name + ": offset is for outer corners only (ignored)");
            if (w.decorationChance < 0 || w.decorationChance > 1) problems.add("wall module " + w.name + ": decoration_chance is 0 to 1");
        }
        for (Defs.Decoration d : decorations)
            if (d.width < 1 || d.height < 1) problems.add("decoration " + d.name + ": width and height must be at least 1");
        for (Defs.ObjectDef o : objects)
            if (!List.of("shed", "gazebo", "pond", "foliage", "clutter").contains(o.category))
                problems.add("object " + o.name + ": unknown category " + o.category);
        for (String p : problems) log.warning(p);
        log.info(String.format("TownBuilder: %d layouts, %d fences, %d styles, %d wall modules, %d objects, %d decorations",
                layouts.size(), fences.size(), styles.size(), walls.size(), objects.size(), decorations.size()));
    }

    /** The .schem of a sidecar, or null if the builder has not saved it yet. */
    public Path schematic(Path dir, String name) {
        for (String ext : List.of(".schem", ".schematic")) {
            Path p = dir.resolve(name + ext);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private <T> List<T> readList(String file, Type type) {
        Path p = root.resolve(file);
        if (!Files.isRegularFile(p)) {
            problems.add(file + " is missing");
            return List.of();
        }
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            List<T> out = GSON.fromJson(r, type);
            return out == null ? List.of() : out;
        } catch (IOException | JsonParseException e) {
            problems.add(file + ": " + e.getMessage());
            return List.of();
        }
    }

    private <T> void readSidecars(Path dir, Class<T> cls, List<T> out) {
        if (!Files.isDirectory(dir)) {
            problems.add(root.relativize(dir) + " is missing");
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) {
                String name = p.getFileName().toString().replaceFirst("\\.json$", "");
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    T t = GSON.fromJson(r, cls);
                    if (t == null) continue;
                    if (t instanceof Defs.WallModule w) w.name = name;
                    if (t instanceof Defs.ObjectDef o) o.name = name;
                    if (t instanceof Defs.Decoration d) d.name = name;
                    if (schematic(dir, name) == null) problems.add(root.relativize(p) + ": no " + name + ".schem beside it (skipped until it is saved)");
                    else out.add(t);
                } catch (IOException | JsonParseException e) {
                    problems.add(root.relativize(p) + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            problems.add(dir + ": " + e.getMessage());
        }
    }
}
