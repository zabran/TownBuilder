package cz.lollywood.townbuilder.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The JSON shapes, one static class per file kind. Field names map to snake_case keys
 * (Gson LOWER_CASE_WITH_UNDERSCORES): {@code minPlotArea} is {@code "min_plot_area"} in the file.
 * Every field has a default, so a sidecar may leave out what it does not need.
 */
public final class Defs {
    private Defs() {}

    /** Something chosen by tags. */
    public interface Tagged {
        List<String> tags();
    }

    /** plot_layouts.json: what a plot of these tags holds. */
    public static final class PlotLayout implements Tagged {
        public String id = "";
        public List<String> tags = new ArrayList<>();
        public int minPlotArea = 0;
        public List<String> houseShapes = new ArrayList<>(List.of("rectangle"));
        public int[] storiesRange = {1, 1};
        public List<String> requiredElements = new ArrayList<>(List.of("house", "fence"));
        public List<String> optionalElements = new ArrayList<>();
        public double foliageDensity = 0.1;
        /** Extension: the largest share of the plot the house may cover (0-1). */
        public double maxHouseShare = 0.4;
        /** Extension: free blocks kept between the fence and the house. */
        public int houseMargin = 2;
        /** Extension: the house's main body at most this many times longer than wide. */
        public double maxHouseAspect = 2.0;

        @Override public List<String> tags() { return tags; }
    }

    /** fences.json */
    public static final class FencePreset implements Tagged {
        public String id = "";
        public List<String> tags = new ArrayList<>();
        public String postBlock = "minecraft:stone_brick_wall";
        public String postCapBlock = "";
        public String fillBlock = "minecraft:spruce_fence";
        public int blocksBetweenCol = 3;
        public int height = 2;
        public String gateSchematic = "";
        /** Extension: where the gate schematic's street side looks as saved: NORTH, EAST, SOUTH or WEST. */
        public String gateFrontFacing = "NORTH";
        /** Extension: used when gate_schematic is empty or missing. */
        public String gateBlock = "minecraft:spruce_fence_gate";

        @Override public List<String> tags() { return tags; }
    }

    /** building_styles.json: the blocks the house is finished with. */
    public static final class BuildingStyle implements Tagged {
        public String id = "";
        public List<String> tags = new ArrayList<>();
        public String foundationBlock = "minecraft:cobblestone";
        public String floorBlock = "minecraft:spruce_planks";
        public String gableBlock = "minecraft:spruce_planks";
        public String roofStairs = "minecraft:dark_oak_stairs";
        public String roofSlab = "minecraft:dark_oak_slab";
        public int roofOverhang = 1;
        public String basementDoor = "minecraft:oak_door";
        public String basementFrameBlock = "minecraft:stone_bricks";
        /** Extension: the steps laid from a door down to the ground when the door stands above it. */
        public String entranceStairs = "minecraft:stone_brick_stairs";
        public List<String> crops = new ArrayList<>(List.of("minecraft:wheat[age=7]"));
        public String hiddenWaterBlock = "minecraft:oak_trapdoor[half=top,waterlogged=true]";

        @Override public List<String> tags() { return tags; }
    }

    /** schematics/wall_modules/&lt;name&gt;.json, beside &lt;name&gt;.schem */
    public static final class WallModule implements Tagged {
        public transient String name = "";
        public List<String> tags = new ArrayList<>();
        /** "wall", "outer_corner" or "inner_corner" */
        public String moduleType = "wall";
        public int length = 1;
        public int height = 4;
        public boolean hasDoor = false;
        public boolean hasWindow = false;
        /** any of "basement", "ground", "upper" */
        public List<String> allowedFloors = new ArrayList<>(List.of("ground", "upper"));
        /** 0 = no limit */
        public int maxPerBuilding = 0;
        public int weight = 10;
        /** where the facade looks in the schematic as saved: NORTH, EAST, SOUTH or WEST */
        public String frontFacing = "NORTH";
        /**
         * 0.7.0 (Arag 11-15, his own proposal): how many blocks INTO the house the module's back row reaches. 0 = the back row on the
         * wall line, anything deeper sticks out to the street (a wall with ornaments, flower boxes, shutters). 1 or more = an
         * overhang or an arcade (podloubí): used only on the front wall; on a floor whose front has such modules, the whole front
         * row stands that many blocks in, the side walls are that much shorter at the front and the front corners move in with
         * them, so the floor above overhangs the street.
         */
        public int overhangDepth = 0;
        /**
         * 0.8.0 (Arag 11-17: an outer corner's pillars were pushed into the house): how many blocks an OUTER corner sticks out of
         * both walls. Its schematic is then (length + offset) square: {@code length} cells on the wall line, {@code offset} rows
         * outside the house on each face. 0 = the whole box inside the house (as before).
         */
        public int offset = 0;
        /** 0.8.0 (Arag 11-17): a decoration may be put in front of this module (decorations are separate schematics). */
        public boolean decoration = false;
        /** 0.8.0 (Arag 11-17): the widest decoration that fits, along the wall; 0 = the module's length. */
        public int decorationMaxWidth = 0;
        /** 0.8.0 [extension]: only decorations of these flavours (empty = any flavour). */
        public List<String> decorationFlavours = new ArrayList<>();
        /** 0.8.0 [extension]: how often a decoration is put there, 0-1 (1 = always, 0.5 = on every other one or so). */
        public double decorationChance = 1.0;
        /**
         * 0.8.0 [extension]: which column the FILLER is cut from (a wall the modules' lengths cannot make exactly is closed with
         * one-block slices of the shortest plain module): -1 = its middle, 0 = its first column from the LEFT as you face the
         * facade, 1 the next... (a module with a post in its middle should give a plain column, or two posts can stand side by side).
         */
        public int fillerColumn = -1;

        @Override public List<String> tags() { return tags; }
        public boolean isCorner() { return !"wall".equals(moduleType); }
    }

    /**
     * 0.8.0 (Arag 11-17): schematics/decorations/&lt;name&gt;.json, beside &lt;name&gt;.schem: a piece put in front of a wall module
     * that allows it ({@code decoration: true}), so walls and decorations mix (a planter, a ledge, a clothesline, stairs...).
     */
    public static final class Decoration implements Tagged {
        public transient String name = "";
        public List<String> tags = new ArrayList<>();
        /** Arag's flavour tags: planter, ledge, clothesline, stairs... A wall may ask for some flavours only. */
        public List<String> flavours = new ArrayList<>();
        /** Along the wall (measured by /plot save). */
        public int width = 1;
        public int height = 1;
        /** Arag: 0 = it starts on the same block (height) as the wall module, up or down from there (between floors: a ledge). */
        public int yOffset = 0;
        /**
         * [extension] 0 = its back row stands on the first row OUTSIDE the wall line (just in front of a flat wall); 1 = one
         * block further out (in front of a module that is itself 2 deep); -1 = its back row ON the wall line (it replaces wall blocks).
         */
        public int depthOffset = 0;
        public int weight = 10;
        /** where its street side looks in the schematic as saved: NORTH, EAST, SOUTH or WEST */
        public String frontFacing = "NORTH";

        @Override public List<String> tags() { return tags; }
    }

    /** schematics/objects/&lt;name&gt;.json, beside &lt;name&gt;.schem */
    public static final class ObjectDef implements Tagged {
        public transient String name = "";
        public List<String> tags = new ArrayList<>();
        /** "shed", "gazebo", "pond", "foliage" or "clutter" */
        public String category = "clutter";
        public int footprintX = 1;
        public int footprintZ = 1;
        public int yOffset = 0;
        public boolean canRotate = true;
        public int maxSlope = 1;
        public int weight = 10;

        @Override public List<String> tags() { return tags; }
    }
}
