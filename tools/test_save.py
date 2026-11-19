"""Test of /plot save on the LOCAL test server (0.4.0): builds a wall, two corners, a pond and a gate by command in odd
orientations, saves them from the console, prints /plot info, builds a plot with them and checks in the dumps that every
saved corner and wall landed turned outward (a furnace marks each piece's front, an observer a corner's other side).
python -I tools/test_save.py   (needs the server running with world tbtest; RCON password from ~/.secrets, never printed)"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import run as rcon_run  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
DUMPS = "D:/LollyWood-work/mc-test-server/plugins/TownBuilder/dumps/"
TAGS = "city,union,residential,savetest"
MTAGS = "city,union,residential,timber,savetest"  # the saved pieces: a superset of TAGS, so only they serve TAGS
G = -60  # the first air row of the flat world


def run(*cmds):
    for c in cmds:
        out = rcon_run([c])[0]
        print(">", c)
        if out.strip():
            print("  " + out.strip().replace("\n", "\n  "))


def dump(name):
    d = json.load(open(DUMPS + name + ".json", encoding="utf-8"))
    ox, oy, oz = d["origin"]
    sx, sy, sz = d["size"]
    pal, data = d["palette"], d["data"]
    blocks = {}
    i = 0
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                blocks[(ox + x, oy + y, oz + z)] = pal[data[i]]
                i += 1
    return blocks


DIRS = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


def facing(b):
    for k in DIRS:
        if "facing=" + k in b:
            return k
    return None


# 1. the pieces, built by command (the chunks force-loaded: nobody stands there)
run("forceload add 296 -4 320 16", "forceload add 136 -4 179 38", "fill 296 -60 -4 320 -55 16 minecraft:air",
    f"fill 300 {G} 0 300 {G + 3} 2 minecraft:stone_bricks", f"setblock 300 {G + 1} 1 minecraft:glass",
    f"setblock 300 {G + 3} 1 minecraft:furnace[facing=east]",
    f"fill 304 {G} 0 304 {G + 3} 0 minecraft:oak_log", f"setblock 304 {G + 1} 0 minecraft:furnace[facing=east]",
    f"setblock 304 {G + 2} 0 minecraft:observer[facing=north]",
    f"fill 306 {G} 0 306 {G + 3} 0 minecraft:spruce_log", f"setblock 306 {G + 1} 0 minecraft:furnace[facing=south]",
    f"setblock 306 {G + 2} 0 minecraft:observer[facing=west]",
    f"fill 310 {G} 0 312 {G} 1 minecraft:mossy_cobblestone",
    f"fill 316 {G} 0 316 {G + 2} 0 minecraft:stone_bricks", f"fill 318 {G} 0 318 {G + 2} 0 minecraft:stone_bricks",
    f"setblock 317 {G} 0 minecraft:oak_fence_gate[facing=south]",
    f"fill 300 {G} 6 300 {G + 3} 8 minecraft:stone_bricks", f"setblock 300 {G} 7 minecraft:oak_door[half=lower,facing=east]",
    f"setblock 300 {G + 1} 7 minecraft:oak_door[half=upper,facing=east]", f"setblock 300 {G + 3} 7 minecraft:furnace[facing=east]",
    f"fill 300 {G} 12 300 {G + 3} 13 minecraft:stone_bricks", f"setblock 300 {G + 3} 12 minecraft:furnace[facing=east]")
# 2. the saves (the console needs facing= / outside=); a second save without overwrite must be refused
run(f"plot save wall st_door3 {MTAGS} floors=ground max=1 facing=EAST overwrite at tbtest 300 {G} 6 300 {G + 3} 8",
    f"plot save wall st_wall2 {MTAGS} facing=EAST overwrite at tbtest 300 {G} 12 300 {G + 3} 13",
    f"plot save wall st_wall3 {MTAGS} weight=1000 facing=EAST overwrite at tbtest 300 {G} 0 300 {G + 3} 2",
    f"plot save wall st_wall3 {TAGS} facing=EAST at tbtest 300 {G} 0 300 {G + 3} 2",
    f"plot save wall st_nofacing {TAGS} at tbtest 300 {G} 0 300 {G + 3} 2",
    f"plot save outer_corner st_outer {MTAGS} weight=1000 outside=north,east overwrite at tbtest 304 {G} 0 304 {G + 3} 0",
    f"plot save inner_corner st_inner {MTAGS} weight=1000 outside=south,west overwrite at tbtest 306 {G} 0 306 {G + 3} 0",
    f"plot save pond st_pond {TAGS} overwrite at tbtest 310 {G} 0 312 {G} 1",
    f"plot save gate st_gate facing=SOUTH overwrite at tbtest 316 {G} 0 318 {G + 2} 0",
    f"plot info {TAGS}")
for n in ("st_wall3", "st_door3", "st_wall2", "st_outer", "st_inner", "st_pond"):
    sub = "objects" if n == "st_pond" else "wall_modules"
    print(n, json.load(open(f"D:/LollyWood-work/mc-test-server/plugins/TownBuilder/schematics/{sub}/{n}.json", encoding="utf-8")))

# 3. a plot: an L-capable layout on a 36 x 35 field, the gate on the south side
X0, Z0, X1, Z1 = 140, 0, 175, 34
SEL = f"at tbtest {X0 - 2} -64 {Z0 - 2} {X1 + 2} -30 {Z1 + 2}"
run(f"fill {X0 - 2} -60 {Z0 - 2} {X1 + 2} -46 {Z1 + 2} minecraft:air", f"fill {X0 - 2} -45 {Z0 - 2} {X1 + 2} -30 {Z1 + 2} minecraft:air",
    f"fill {X0 - 2} -61 {Z0 - 2} {X1 + 2} -61 {Z1 + 2} minecraft:grass_block",
    f"setblock {X0} -60 {Z0} minecraft:redstone_block", f"setblock {X1} -60 {Z0} minecraft:redstone_block",
    f"setblock {X1} -60 {Z1} minecraft:redstone_block", f"setblock {X0} -60 {Z1} minecraft:redstone_block",
    f"setblock {(X0 + X1) // 2} -60 {Z1} minecraft:gold_block")
seed = sys.argv[1] if len(sys.argv) > 1 else "7"
run(f"plot outline {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X1 + 2} -60 {Z1 + 2} st_outline")
run(f"plot build {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X1 + 2} -45 {Z1 + 2} st_build")

# 4. the checks: the house's wall line from the outline (red/orange), its inside by flood fill from outside
ol = dump("st_outline")
ring = {(x, z) for (x, y, z), b in ol.items() if b in ("minecraft:red_concrete", "minecraft:orange_concrete")}
xs = [p[0] for p in ring]; zs = [p[1] for p in ring]
bx0, bx1, bz0, bz1 = min(xs) - 1, max(xs) + 1, min(zs) - 1, max(zs) + 1
outside = set()
stack = [(bx0, bz0)]
while stack:
    p = stack.pop()
    if p in outside or p in ring or not (bx0 <= p[0] <= bx1 and bz0 <= p[1] <= bz1):
        continue
    outside.add(p)
    stack += [(p[0] + 1, p[1]), (p[0] - 1, p[1]), (p[0], p[1] + 1), (p[0], p[1] - 1)]
print(f"house wall line: {len(ring)} cells, box x {min(xs)}..{max(xs)} z {min(zs)}..{max(zs)}")
bl = dump("st_build")
inner_cols = {(x, z) for (x, y, z), b in bl.items() if b.startswith("minecraft:spruce_log")}
ok = bad = 0
for (x, y, z), b in sorted(bl.items()):
    if not (b.startswith("minecraft:furnace") or b.startswith("minecraft:observer")):
        continue
    f = facing(b)
    dx, dz = DIRS[f]
    if (x, z) in inner_cols:
        # an inner corner's two faces touch the ends of the two walls meeting there; between them, diagonally, is outside
        other = [d for d in DIRS.values() if d != (dx, dz) and d != (-dx, -dz) and (x + d[0], z + d[1]) in ring
                 and (x + dx + d[0], z + dz + d[1]) in outside]
        good = (x + dx, z + dz) in ring and len(other) == 1
    else:
        good = (x + dx, z + dz) in outside and (x - dx, z - dz) not in outside
    ok += good
    bad += not good
    if not good:
        print("  WRONG", b.split("[")[0], "at", (x, y, z), "faces", f)
corners = {}
for (x, y, z), b in bl.items():
    if b.startswith("minecraft:oak_log") or b.startswith("minecraft:spruce_log"):
        corners.setdefault(b.split("[")[0], set()).add((x, z))
print(f"markers turned outward: {ok}, wrong: {bad}; corner columns: { {k: len(v) for k, v in corners.items()} }")

# 5. clean up: the saved pieces would also serve plain city,union,residential (their tags are a superset), so move them aside
import shutil  # noqa: E402
SRV = "D:/LollyWood-work/mc-test-server/plugins/TownBuilder/"
os.makedirs(SRV + "test-saved", exist_ok=True)
for sub in ("schematics/wall_modules/", "schematics/objects/"):
    for f in os.listdir(SRV + sub):
        if f.startswith("st_"):
            shutil.move(SRV + sub + f, SRV + "test-saved/" + f)
run("plot reload", "forceload remove all")
