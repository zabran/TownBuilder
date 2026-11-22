# TownBuilder

A Paper 1.21.11 plugin for filling town plots on the LollyWood server with WorldEdit 7.3 (plain WorldEdit or FAWE): the builder
marks a plot, the plugin draws coloured outlines, the builder corrects them, the plugin builds the house, the fence, the shed,
pond and garden, and finally scatters trees and clutter. Every step goes into the builder's WorldEdit history, so `//undo` reverts
it. Specification: Arag's *Příprava zadání pro Minecraft plugin* (2026-11-13).

**Start here (for the builders):**
0. **0.8.0: `/plot` alone opens the MENU** (or `/plot item` gives a book that opens it on a right click): everything below by clicking.
1. Install (below): the jar beside WorldEdit; the first start writes the example files for `city, union, residential` (Vindgard).
2. Try it at once with `/plot testkit` (placeholder schematics for every example), on a test plot: `/plot outline`, `/plot build`,
   `/plot foliage`; `//undo` reverts each step.
3. Build your own pieces in the world and save each with `/plot save` (the table under *Saving your modules*); save under an
   example's name with `overwrite` to replace its placeholder, or delete the placeholder `.schem`.
4. `/plot info city,union,residential` shows what is there and what is still missing.
The pictures in `docs/` show the test plots of each version.

**Version 0.8.1 (2026-11-22): the outline's seed works** (Arag 11-19: every new outline put the house, garden and pond in the same
place and size). The planner had always taken the biggest house nearest the gate and the extras farthest from it, so the seed only
picked the shape. Now the layout's `variety` (0-1, default 0.3) lets each outline pick the house's size down to (1 - variety) of the
biggest that fits, and the house's and the extras' places within variety x the plot's span of their best place (the house still
faces the gate); `variety: 0` = 0.8.0's single plan; the same seed still repeats a plan. Tested: 8 seeds, 8 different plans
(`tools/test_arag081.py`, `docs/test-plot-0.8.1-seeds.png`). For Arag: `ZPRAVA-PRO-ARAGA-0.8.1.md`.

**Version 0.8.0 (2026-11-17): Arag's three wishes of 11-17 and the menu.**
- **For Arag:** `ZPRAVA-PRO-ARAGA-0.8.0.md` (Czech; the zip's top folder, `docs/` in the repo).
- **The menu** (Marek: clicking is faster than typing): `/plot` (or the book from `/plot item`, right click). The page shows your
  tags (remembered per player in `menu.json`) and your WorldEdit selection; buttons **1 Outline**, **2 Build**, **3 Foliage**, **Undo**,
  **Tags...** (tick the tags every file uses, or take a layout's or style's own set), **What the tags select** (`/plot info`), **Save a
  piece...** (pick the kind, then a form: name, tags, floors as ticks, the numbers as sliders, decoration and offset fields), **Paste a
  schematic...** and **Placeholder kit** (operators), **Reload files**. A phase's result opens in a dialog (green done, yellow to check,
  red failed) with **the next phase**, **Again (new roll)** (undo it and run it with a new random choice) and **Undo**. Every button
  says on hover what it does.
- **An outer corner may stick out of the walls: `offset`** (Arag: the pillars of his corner were pushed into the house). A corner
  with `"offset": 1` is saved as a (length + 1) square: `length` cells on the wall line and one row outside the house on each of its
  two faces. `/plot save outer_corner <name> <tags> offset=1` (stand diagonally outside it as before) measures the box and writes
  `length` = its width minus the offset. Inner corners need none (their box already reaches into the notch).
- **Decorations** (Arag's design): wall modules and decorations are separate schematics that mix. A wall module with
  `"decoration": true` gets one of the decorations of the same tags in front of it, at most `decoration_max_width` wide (default its
  length), centred on it. A decoration (`schematics/decorations/<name>.schem` + `.json`) has `flavours` (planter, ledge,
  clothesline, stairs...) and `y_offset` (0 = its foot on the wall module's foot; a ledge between floors: the module's height minus
  1). Two optional keys of mine on the wall: `decoration_flavours` (only those flavours) and `decoration_chance` (0.5 = about every
  other one); one on the decoration: `depth_offset` (0 = its back row just outside the wall line, 1 = a block further out, -1 = on
  the wall line). `/plot save decoration <name> <tags> flavours=planter y_offset=1`, stand on its street side.
- **Posts in the middle of a module** (Arag's own solution: a post at least one block from both ends of a module of 3 or more can
  never stand beside another): fine, with one catch, the filler. A wall the modules' lengths cannot make exactly is closed with a
  one-block slice cut from the MIDDLE of the shortest plain module, and if that middle is the post, two posts can meet. New key
  `filler_column` on that module: 0 = cut from its first column from the left as you face it (-1 = the middle, the default);
  `/plot save wall ... filler=0`.
- Tested on the local server (`tools/test_arag080.py`, 5 seeds, ALL OK): a corner 1 on the wall line with offset 1 (cobblestone
  pillars on both faces, none inside the house), a pot in front of every window, a ledge of top slabs at the top of the plain walls,
  the walls closed, a door. Render `docs/test-plot-0.8.0-house.png` (the renderer draws a flower pot as a block).

**Version 0.7.0 (2026-11-15): Arag's first test.**
- **For Arag:** `ZPRAVA-PRO-ARAGA-0.7.0.md` (Czech; the zip's top folder, `docs/` in the repo): what changed after his test, what was done with the 2017 Vindgard and a
  plan of what could come next, with questions. The 2017 Vindgard's west bank is in `examples/vindgard-west-2017.schematic` (put
  it into `plugins/TownBuilder/imports/` and `/plot paste imports/vindgard-west-2017.schematic`), its style in
  `examples/vindgard_2017-style.json` (one entry of the array in `building_styles.json`), its pictures `docs/vindgard-2017-*.png`.
- **The outline phase clears the plot** of trees, leaves, flowers, crops and grass (the vanilla block tags `logs`, `leaves`,
  `flowers`, `crops` and `replaceable_by_trees`, from the ground up to the treetops, inside the plot and on its fence line). Water
  and the plants in it are kept, although `replaceable_by_trees` lists them, so a pond or a stream in a plot stays.
- **No more holes in the roof where a tree stood.** The house's own clearing claimed the cells it emptied, so the roof skipped them;
  now it clears without claiming, and a tree taller than the roof loses its crown too.
- **Fence posts on the real corners and evenly between them.** A slanted fence is drawn in blocks as a staircase, and every step
  used to count as a corner, so every block got a post. Now the outline's real corners are found (a slanted run wobbles less than a
  block off its line) and the posts between them are spaced evenly by straight-line distance, about every `blocks_between_col + 1`
  blocks; a post that would stand right beside another is left out.
- **Deep wall modules and `overhang_depth`.** A module deeper than one block stands with its back row on the wall line and the
  rest sticks out to the street (ornaments, flower boxes, shutters): as before. **New key `overhang_depth`** (Arag's proposal):
  how many blocks INTO the house the module's back row reaches; 1 or more makes an **overhang or an arcade (podloubí)**. Such
  modules are used only on the front wall; on a floor whose modules include them, the whole front row stands that many blocks in,
  that floor's side walls are that much shorter at the front and the front corners move in with them, so the open ends of an
  arcade stay open and the floor above (modules with `overhang_depth` 0) overhangs the street. Give the arcade modules
  `allowed_floors: ["ground"]`; a door module on that floor stands in the inner wall. `/plot save wall ... overhang=1` writes it.
- **`/plot paste <file> [at <world> <x> <y> <z>]`** (operators): pastes any schematic WorldEdit reads (also an old MCEdit
  `.schematic` from a pre-1.13 world: WorldEdit converts its block ids), its box's corner at the point, into your `//undo`
  history. The file is a path under `plugins/TownBuilder/`. Made to bring pieces of the old LollyWood server's worlds onto a test
  server and cut modules from them with `/plot save` (the 2017 Vindgard: `LollyWood-Lore/_tools/mc_to_schematic.py`).
- **Vindgard's own materials** (read from the 2017 save of `RPG-Vindgard`): roofs of oak logs edged with spruce stairs and slabs,
  walls of spruce planks in oak and spruce log frames, oak doors, oak trapdoor shutters, a cobblestone plinth. A style with them,
  tried on the local server (`docs/test-plot-0.7.0-vindgard-style.png`, tags `city,union,residential,timber,vindgard`):
  `{"id": "vindgard_2017", "tags": ["city", "union", "residential", "vindgard"], "foundation_block": "minecraft:cobblestone",
  "floor_block": "minecraft:spruce_planks", "gable_block": "minecraft:spruce_planks", "roof_stairs": "minecraft:spruce_stairs",
  "roof_slab": "minecraft:spruce_slab", "basement_door": "minecraft:oak_door", "basement_frame_block": "minecraft:cobblestone",
  "entrance_stairs": "minecraft:cobblestone_stairs"}`.
- Tested on the local server (`tools/test_arag070.py`, renders `docs/test-plot-0.7.0-fence.png` and
  `docs/test-plot-0.7.0-arcade.png`): a slanted plot full of trees, flowers and grass with a pond, cleared, its water kept; a tree
  grown on the house's plan after the outline, no hole in the roof; the 18 x 18 slanted side with 5 evenly spaced posts and none
  side by side; an arcade module 2 deep with `overhang_depth` 1 on eight houses: its pillars on the wall line, its wall one block
  in, the arcade's ends open, the corners one in, the upper floor and its corners on the wall line.

**Version 0.6.0 (2026-11-14): inner corners of any width too.** An inner corner module may also be a square (2 x 2, 3 x 3): its
two arms run along the walls meeting at the notch of an L or T, the cells between the arms (the notch, outside the house) are
pasted as built, air skipped; the walls there start that many blocks from the elbow. With outer and inner corners of the same
width every side of an L needs the same lengths, so L and T houses come out even with modules of one length. Tested on the local
server with 2 x 2 corners of both kinds and 3-long walls: rectangles and L houses, closed walls, every arm on the wall line and
every notch outside the house (`tools/test_widecorner.py`).

**Version 0.5.0 (2026-11-14): outer corners of any width.** An outer corner module may be a square of any size (2 x 2, 3 x 3: a
stone pillar, a quoin, a corner tower's foot); the walls then start that many blocks from the house's corner, the planner draws
only sides of two corners plus what the wall modules make, and the corner's box stands inside the house. `/plot save
outer_corner` measures it (it must be square seen from above) and `/plot info` shows the width. All outer corners of one set of
tags should share a width (the build uses the most common one). Inner corners stay one block. Tested on the local server with a
2 x 2 corner and 3-long walls: four houses, closed walls, every corner box inside the house and looking out, a door each
(`tools/test_widecorner.py`, render `docs/test-plot-0.5.0.png`).

**Version 0.4.1 (2026-11-14): outlines moved by hand always build.** When a wall's length cannot be made exactly from the
modules' lengths (the builder moved or stretched the red line), the modules fill the longest length they can, centred, and the
rest is closed with **filler columns**: a one-block slice cut from the middle of the shortest plain module (one without a door or
window when there is such a one); the build says how many. A front wall where no exact arrangement leaves room for the door gets the
door in its middle with filler beside it. The planner falls back to a rectangle when no L or T can be made from the modules'
lengths (with modules of one length it used to give up). Tested on the local server with only 3-long walls and doors: four plots,
each stretched by hand one block, closed walls and a door every time (`tools/test_filler.py`, render `docs/test-plot-0.4.1.png`).

**Version 0.4.0 (2026-11-14): `/plot save`** turns a piece built in the world into a module or an object (its `.schem` and its
sidecar `.json`, measured and turned the way the build expects, the facing read from where you stand), and **`/plot info <tags>`**
shows what a set of tags selects and what is still missing. Tested on the local server: walls, a door wall, an outer and an inner
corner saved in four different turns, a pond and a gate; a plot built from them (an L house, two floors) had all 70 marker blocks
turned outward (`tools/test_save.py`, render `docs/test-plot-0.4.0.png`).

**Version 0.3.0 (2026-11-14):** all three phases, plus the **basement floor** (basement wall modules on the foundation where it
stands out of the ground, a cellar behind them, a basement door module), **steps** from any door that stands above the ground,
**gate schematics wider than one block** with their saved facing, and a house no longer than twice its width. Tried on a local
Paper 1.21.11 + WorldEdit 7.3.18 server with the placeholder modules (`/plot testkit`): a 5-marker plot over a 4-block terrace
step gave a 20x21 house facing the gate with a 3-high stone basement band on its two downhill sides, the cellar door with one
step, four steps down from the front door, a 3-wide gate with lanterns centred on the gold block, and the rest as before; `//undo`
reverts each phase. Renders: `docs/test-plot-0.3.0.png` (and 0.2.2's flat-step test, `docs/test-plot-0.2.2.png`).

## Install
Put `TownBuilder-<version>.jar` into `plugins/` beside WorldEdit 7.3.18 (or newer, or FAWE) and start the server. The first start
writes the example files below into `plugins/TownBuilder/`; it never overwrites files that are already there. The commands need
the permission `townbuilder.use` (operators have it; give it to other builders, e.g. with LuckPerms
`/lp group <group> permission set townbuilder.use true`).

## Workflow
1. Select the plot area with a WorldEdit cuboid (`//wand`). Inside it, put **REDSTONE_BLOCKs** on the ground at the corners of the
   plot (at least 3; their order does not matter: they are sorted round their centre, and a plot with a deep notch gets a warning).
   Put a **GOLD_BLOCK** where each gate should be (a plot may have several); the house faces the nearest one.
2. `/plot outline <tags>` (e.g. `/plot outline city,union,residential`) draws 1-block-high concrete outlines on the ground:
   | colour | meaning |
   |---|---|
   | red | the house (rectangle, L or T) |
   | orange | the house's front wall (the entrance side) |
   | blue | a shed or gazebo |
   | cyan | a pond |
   | green | a garden or field |
   | yellow | the fence along the plot edge (the gold blocks stay as the gates) |
3. Move or redraw the outlines by hand if you like.
4. `/plot build <tags>` (same selection) builds what the outlines say.
5. `/plot foliage <tags>` scatters trees, bushes and clutter on what is left free.
6. `/plot reload` re-reads every JSON and schematic and lists any problems.

## Saving your modules: `/plot save`
Build the piece anywhere, select exactly it (`//wand`, its two corners), stand where the command says, and run:
| piece | command (the tags once; `key=value` options in any order) | where to stand |
|---|---|---|
| a wall module | `/plot save wall <name> <tags> [floors=ground,upper] [weight=10] [max=0] [overhang=0]` | **in front of its facade** |
| an arcade or overhang module | `/plot save wall <name> <tags> floors=ground overhang=1` (its back row 1 block inside the house) | in front of its facade |
| a door module | `/plot save wall <name> <tags> floors=ground max=1` (a door in it is found by itself) | in front of its facade |
| a basement module | `/plot save wall <name> <tags> floors=basement` | in front of its facade |
| an outer corner | `/plot save outer_corner <name> <tags> [floors=...] [offset=0]`; select it whole: 1 x 1, or a square 2 x 2, 3 x 3...; `offset=1` = its outer row on both faces stands outside the walls (pillars) | **diagonally outside it**, where both its faces are seen |
| an inner corner | `/plot save inner_corner <name> <tags> [floors=...]`; 1 x 1, or a square whose two arms are the walls and whose rest is the notch | diagonally outside it, in the notch of the L |
| a wall that takes decorations | `/plot save wall <name> <tags> decoration=true [deco_width=1] [deco_flavours=planter] [deco_chance=0.5] [filler=0]` | in front of its facade |
| a decoration | `/plot save decoration <name> <tags> flavours=planter [y_offset=1] [depth_offset=0]` | **on its street side** |
| a shed, gazebo, pond, tree or clutter | `/plot save <kind> <name> <tags> [y_offset=-1] [rotate=true] [slope=1]`, kind = `shed`, `gazebo`, `pond`, `foliage` or `clutter` | anywhere |
| a gate | `/plot save gate <name> [fence=<id>]` | on its street side; `fence=<id>` writes it into that fence preset |
It measures `length`, `height`, `has_door` (any door), `has_window` (any glass), `front_facing` (walls) or `footprint_x/z` (objects),
writes `<name>.schem` and `<name>.json` into the right folder and reloads. **A corner may be saved in any turn:** the plugin works out
the `front_facing` that turns it right (the convention below is only for writing a corner's JSON by hand). If the `.json` exists
already (the shipped examples), it keeps your other keys and fills in the measured ones; an existing `.schem` is replaced only with
`overwrite`. From the console add `at <world> <x1> <y1> <z1> <x2> <y2> <z2>` and `facing=<NORTH|EAST|SOUTH|WEST>` (walls, gates) or
`outside=north,east` (corners). It warns when a floor's modules differ in height (only the most common height is used) and when a
piece is deeper than one block (it sticks out to the street, unless you give it `overhang=<blocks in>`).

**`/plot info <tags>`** lists the layouts, style and fence those tags select, the wall modules on each floor (their lengths and the
wall lengths they can fill, heights, door, corners), the objects by kind, and the sidecars still waiting for their `.schem`.
Tags match one way or the other (below), so a module tagged with MORE tags than you ask for still serves.

## More commands
- `/plot` or `/plot menu`: the menu (0.8.0, above); `/plot item`: the book that opens it; `/plot undo`: WorldEdit's `//undo`.
- `/plot testkit` (operators): writes plain PLACEHOLDER schematics for every sidecar that has no `.schem` yet (planks walls, a
  window with a sill on the facade side, a door, log corners, a box shed, a gazebo, a pond, an oak, a bush, barrels), so the plugin
  can be tried before the real modules exist. It also writes a 3-wide gate (two stone posts with lanterns, a fence gate) for a fence
preset's `gate_schematic`. **A placeholder is used in real builds until it is replaced**: delete its `.schem`, or save your own
  piece under the same name with `/plot save ... overwrite` (the example's JSON keys stay).
- From the console (or a command block): add `at <world> <x1> <y1> <z1> <x2> <y2> <z2>` instead of a selection, e.g.
  `/plot build city,union,residential at world 100 60 100 160 120 160`. `seed <n>` repeats a result (every run prints its seed).
- `/plot dump <world> <x1> <y1> <z1> <x2> <y2> <z2> <name>`: the blocks of a box to `dumps/<name>.json` (for checking a result
  outside the game; `tools/render_dump.py` draws it).

## How the plan is made
The house goes near the main gate (the gold block nearest the plot's middle; how near and how big by the layout's `variety`, so
every new outline differs: 0.8.1), up to `max_house_share` of the plot
and `house_margin` blocks from the fence, at most `max_house_aspect` times longer than wide, its front toward that gate; every wall
length must be a sum of the modules' lengths. The
shed, pond and garden go behind it, away from the gate, with a free row around each. The floor stands one block above the highest
ground under the house; the foundation fills down to the ground. **The basement:** wherever the foundation stands out of the
ground at least as high as the `basement` wall modules, the band right under the ground floor is built from them (the longest
run their lengths can fill, centred), the outer corners too where both sides are that high, and the foundation behind becomes a
cellar room; one basement module with `has_door` goes on the most exposed run. Without basement modules (or without a door
module) the cellar door is carved into the foundation where it stands 3+ blocks high, as the spec says. **Steps:** a door above
the ground outside gets stairs (`entrance_stairs`) down to it, straight out from the door. The roof's ridge runs along the longer
side; a wing's roof runs into the main one.

## Limits of this version
- Corners may be any square (outer 0.5.0, inner 0.6.0), all of one kind of one width for a set of tags (the build takes the most common).
- A floor's height is the most common height among its modules; modules of another height are skipped for that floor.
- A floor of basement modules is one band right under the ground floor (no deeper cellar storeys).
- The gate schematic's back stands on the fence line and it is centred on its gold block; if it does not fit the straight fence
  there, the `gate_block` is placed instead.
- Steps run straight out from a door; if something stands in their way they stop (the build reports it).
- An overhang (`overhang_depth`) is only on the front wall, and its side walls are shortened only where the front meets an outer
  corner (a front wall ending at an inner corner keeps that corner as it is, and the build says so).
- A filler column is a plain slice of a module: a wall with a timber frame may show a narrower bay where filler closes it
  (`filler_column` picks which slice, 0.8.0).
- A decoration is centred on its module (no other placement), and only wall modules take them (not corners).
- The menu cannot read where you will stand: `/plot save` from the menu reads the facing from where you stand when you click Save.
- Phase 3 plants only inside a closed fence; with no fence it plants the whole selection (and says so).

## Tags
Every preset and every module has `tags`. An entry fits when one set of tags holds the other: your command's tags are all among
the entry's (a module tagged `city, union, residential, timber` serves `/plot build city,union,residential`), or the entry's are
all among yours (a fence tagged `city, union` serves the same command). Among several fitting entries the one sharing the most tags
wins, then the one with the fewest extra tags; modules of one kind are drawn by `weight`.

## The files (`plugins/TownBuilder/`)
### `plot_layouts.json` (a list)
| key | meaning | default |
|---|---|---|
| `id` | a name | |
| `tags` | | |
| `min_plot_area` | the smallest plot (in blocks) this layout fits | 0 |
| `house_shapes` | any of `rectangle`, `L_shape`, `T_shape` | `["rectangle"]` |
| `stories_range` | `[min, max]` floors | `[1, 1]` |
| `required_elements` | always built: `house`, `fence` | |
| `optional_elements` | built if there is room: `shed`, `gazebo`, `garden`, `pond` | |
| `foliage_density` | 0-1, how thickly phase 3 plants | 0.1 |
| `max_house_share` | the largest part of the plot the house may cover (0-1) | 0.4 |
| `house_margin` | free blocks between the fence and the house | 2 |
| `max_house_aspect` | the house's main body at most this many times longer than wide | 2.0 |
| `variety` | how much each new outline differs (0-1): the house's size picked at random down to (1 - variety) of the biggest that fits, the house and the extras placed at random up to variety x the plot's span from their best place; 0 = the same plan every time (as 0.8.0) | 0.3 |

### `fences.json` (a list)
| key | meaning |
|---|---|
| `post_block`, `post_cap_block` | the post and the block on top of it (cap may be empty) |
| `fill_block` | between the posts |
| `blocks_between_col` | fill blocks between two posts (0.7.0: measured in straight lines, so slanted runs get evenly spaced posts too; posts always stand on the outline's corners) |
| `height` | the fence's height in blocks |
| `gate_schematic` | a schematic pasted at each gold block (in `schematics/objects/`), its street side facing out of the plot; it may be wider than one block (centred on the gold block, the fence cells it covers are left free) |
| `gate_front_facing` | where that schematic's street side looks as you saved it: `NORTH`, `EAST`, `SOUTH`, `WEST` (default `NORTH`) |
| `gate_block` | used when there is no gate schematic (default `minecraft:spruce_fence_gate`) |
Fences and walls are connected by the plugin itself (WorldEdit does not update their shapes).

### `building_styles.json` (a list)
`foundation_block`, `floor_block`, `gable_block` (the triangle under the roof's ends), `roof_stairs`, `roof_slab` (the ridge when the
roof is an odd number of blocks wide), `roof_overhang` (blocks), `basement_door` and `basement_frame_block` (when the foundation
stands 3 or more blocks out of the ground somewhere and no basement door module exists), `entrance_stairs` (the steps down
from a door above the ground, default `minecraft:stone_brick_stairs`; the foundation block goes under them), `crops` (block states such as `minecraft:wheat[age=7]`), `hidden_water_block`
(the water that keeps the fields wet, e.g. a waterlogged top trapdoor).

### `schematics/wall_modules/<name>.schem` + `<name>.json`
| key | meaning | default |
|---|---|---|
| `module_type` | `wall`, `outer_corner` or `inner_corner` | `wall` |
| `length` | blocks along the wall | 1 |
| `height` | blocks; all modules of one floor should share one height | 4 |
| `has_door`, `has_window` | | false |
| `allowed_floors` | any of `basement`, `ground`, `upper` | `["ground", "upper"]` |
| `max_per_building` | 0 = no limit | 0 |
| `weight` | how often it is drawn among the fitting ones | 10 |
| `front_facing` | where the facade looks in the schematic as you saved it: `NORTH`, `EAST`, `SOUTH`, `WEST` | `NORTH` |
| `overhang_depth` | 0.7.0: blocks the module's back row reaches INTO the house: 0 = on the wall line (deeper parts stick out to the street), 1+ = an overhang or an arcade, front wall only (above) | 0 |
| `offset` | 0.8.0, outer corners: blocks it sticks out of both walls; the schematic is (`length` + `offset`) square | 0 |
| `decoration` | 0.8.0: a decoration may go in front of it | false |
| `decoration_max_width` | 0.8.0: the widest decoration that fits (0 = its `length`) | 0 |
| `decoration_flavours` | 0.8.0 [mine]: only decorations of these flavours (empty = any) | `[]` |
| `decoration_chance` | 0.8.0 [mine]: 0-1, how often it gets one | 1 |
| `filler_column` | 0.8.0 [mine]: the column a filler is cut from, 0 = the first from the left as you face it, -1 = the middle | -1 |
**How to make one:** easiest with `/plot save` (above). By hand: build the piece so its facade looks the way you will write in `front_facing`. Select exactly the piece
(`//pos1`, `//pos2`: `length` blocks along the wall, `height` blocks tall), `//copy`, `//schem save <name>` (WorldEdit writes it to
`plugins/WorldEdit/schematics/`), then move `<name>.schem` beside `<name>.json` here. Where you stand when copying does not matter:
the plugin aligns the selected box. The **back** of the box (the side away from the facade) stands on the wall line; anything
deeper than one block (sills, beams) sticks out of the house.
**A corner is a square of any size** (its `length` = its width; all corners of one kind and one set of tags should share it; an
inner corner's notch cells, between its arms, lie outside the house). By hand (`/plot save` needs none of this), save the **outer corner** as the north-west corner of a house (its outside faces north and west)
with `front_facing: NORTH`; save the **inner corner** as the inner corner of an L whose outside lies to the south-east, with
`front_facing: EAST`. The ground floor of the front wall gets one module with `has_door: true`, as near the middle as the lengths
allow. A wall's length between two corners must be a sum of the available modules' lengths (with lengths 2 and 3, any length from
2 up works).
**Basement modules** (`allowed_floors: ["basement"]`) are saved the same way; all of one height (the most common height wins).
They face the foundation's exposed band under the ground floor, so their height is how far a slope must drop before they appear.
One of them may have `has_door: true` (give it `max_per_building: 1`): the cellar door, with steps down to the ground if needed.
A basement outer corner is optional (without one the corner stays foundation).

### `schematics/decorations/<name>.schem` + `<name>.json` (0.8.0)
| key | meaning | default |
|---|---|---|
| `tags` | chosen by tags like everything else | |
| `flavours` | Arag's flavour tags: `planter`, `ledge`, `clothesline`, `stairs`... (a wall may ask for some only) | `[]` |
| `width`, `height` | measured by `/plot save`: blocks along the wall, tall | 1, 1 |
| `y_offset` | 0 = its foot on the wall module's foot; up or down from there | 0 |
| `depth_offset` | [mine] 0 = its back row on the first row outside the wall line, 1 = one further out, -1 = on the wall line | 0 |
| `weight` | | 10 |
| `front_facing` | where its street side looks as saved | `NORTH` |
It is centred on the module, air in it is skipped (so it never erases the wall). The examples: `union_deco_pot_1` (a flower pot on
the window sill, `y_offset` 1, flavour `planter`) and `union_deco_ledge_3` (a ledge at the top of a 4-high wall, `y_offset` 3,
flavour `ledge`); the example window modules take a planter 1 wide, the plain 3-long one a ledge on about every other one.

### `schematics/objects/<name>.schem` + `<name>.json`
| key | meaning | default |
|---|---|---|
| `category` | `shed`, `gazebo`, `pond`, `foliage` or `clutter` | `clutter` |
| `footprint_x`, `footprint_z` | the size on the ground as saved (x = west-east, z = north-south) | 1, 1 |
| `y_offset` | how far to sink it (-1 sinks a pond one block) | 0 |
| `can_rotate` | may be turned by 90° to fit | true |
| `max_slope` | the largest height difference of the ground under it | 1 |
| `weight` | | 10 |

## The examples
The first start writes a complete set for the first style, `city, union, residential` (Vindgard): two layouts, a fence, a style,
twelve wall modules (plain 2 and 3, window 2 and 3, a door 3, the two corners; for the basement plain 2 and 3, a barred window 3,
a door 3 and an outer corner, all 3 high) and six objects (a shed 5x4, a gazebo 4x4, a pond 5x4,
an oak 3x3, a bush, two barrels). **Only the `.json` files are shipped**: each is skipped, with a note in `/plot reload`, until its
`.schem` is saved beside it.

## Building the plugin
JDK 21 and the Gradle wrapper: `./gradlew build` → `build/libs/TownBuilder-<version>.jar` (the unit tests run with it).
