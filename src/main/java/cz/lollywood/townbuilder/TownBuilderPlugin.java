package cz.lollywood.townbuilder;

import cz.lollywood.townbuilder.command.PlotCommand;
import cz.lollywood.townbuilder.config.ConfigStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class TownBuilderPlugin extends JavaPlugin {
    /** The example files written on the first start (never over the builders' own). */
    public static final List<String> DEFAULTS = List.of(
            "plot_layouts.json", "fences.json", "building_styles.json",
            "schematics/wall_modules/union_wall_plain_3.json",
            "schematics/wall_modules/union_wall_plain_2.json",
            "schematics/wall_modules/union_wall_window_3.json",
            "schematics/wall_modules/union_wall_window_2.json",
            "schematics/wall_modules/union_wall_door_3.json",
            "schematics/wall_modules/union_corner_outer.json",
            "schematics/wall_modules/union_corner_inner.json",
            "schematics/wall_modules/union_basement_plain_2.json",
            "schematics/wall_modules/union_basement_plain_3.json",
            "schematics/wall_modules/union_basement_window_3.json",
            "schematics/wall_modules/union_basement_door_3.json",
            "schematics/wall_modules/union_basement_corner_outer.json",
            "schematics/objects/union_shed_5x4.json",
            "schematics/objects/union_gazebo_4x4.json",
            "schematics/objects/union_pond_5x4.json",
            "schematics/objects/union_tree_oak_3x3.json",
            "schematics/objects/union_bush_1x1.json",
            "schematics/objects/union_barrels_2x1.json",
            "schematics/decorations/union_deco_pot_1.json",
            "schematics/decorations/union_deco_ledge_3.json");

    private ConfigStore store;

    @Override
    public void onEnable() {
        for (String r : DEFAULTS) {
            if (!new File(getDataFolder(), r).exists()) saveResource(r, false);
        }
        store = new ConfigStore(getDataFolder().toPath(), getLogger());
        store.load();
        PluginCommand cmd = getCommand("plot");
        if (cmd != null) {
            PlotCommand pc = new PlotCommand(this, store);
            cmd.setExecutor(pc);
            cmd.setTabCompleter(pc);
            getServer().getPluginManager().registerEvents(pc.menu(), this);
        }
    }

    public ConfigStore store() { return store; }
}
