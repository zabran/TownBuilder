"""Test of 0.7.0 (Arag's notes of 2026-11-15) on the LOCAL test server:
A. the outline phase clears the plot's trees, flowers and grass (not its water); a tree grown on the house's plan after the
   outline leaves no hole in the roof (the clearing no longer claims the cells the roof needs);
B. a slanted fence gets posts on its real corners and evenly between them, never two side by side;
C. overhang_depth: an arcade module (2 deep, overhang_depth 1) makes the ground floor's front row stand one block in, the side walls
   of that floor one shorter at the front, the floor above on the wall line.
python -I tools/test_arag070.py   (needs test_save.py's pieces in plugins/TownBuilder/test-saved/)"""
import json
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import run as rcon_run  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
SRV = "D:/LollyWood-work/mc-test-server/plugins/TownBuilder/"
DUMPS = SRV + "dumps/"
TAGS = "city,union,residential,savetest"
MTAGS = "city,union,residential,timber,savetest"
G = -60
PIECES = {"st_wall3": "wall_modules", "st_door3": "wall_modules"}
ok_all = True


def run(*cmds, quiet=False):
    out = []
    for c in cmds:
        r = rcon_run([c])[0]
        out.append(r)
        if not quiet:
            print(">", c)
            if r.strip():
                print("  " + r.strip().replace("\n", "\n  ")[:1500])
    return out


def dump(name):
    d = json.load(open(DUMPS + name + ".json", encoding="utf-8"))
    ox, oy, oz = d["origin"]
    sx, sy, sz = d["size"]
    blocks, i = {}, 0
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                blocks[(ox + x, oy + y, oz + z)] = d["palette"][d["data"][i]]
                i += 1
    return blocks


def inside_poly(pts, x, z):
    """Point in polygon (cell centre), for the plot's markers."""
    c, n = False, len(pts)
    for i in range(n):
        (x1, z1), (x2, z2) = pts[i], pts[(i + 1) % n]
        if (z1 > z) != (z2 > z) and x < (x2 - x1) * (z - z1) / (z2 - z1) + x1:
            c = not c
    return c


def check(name, cond, detail=""):
    global ok_all
    ok_all &= bool(cond)
    print(("  OK   " if cond else "  FAIL ") + name + (": " + detail if detail else ""))


VEG = ("_log", "_leaves", "short_grass", "tall_grass", "poppy", "dandelion", "fern", "wheat", "oxeye_daisy", "_wood")

for n, sub in PIECES.items():
    for ext in (".json", ".schem"):
        if os.path.exists(SRV + "test-saved/" + n + ext):
            shutil.copy(SRV + "test-saved/" + n + ext, SRV + "schematics/" + sub + "/" + n + ext)

# a one-wide outer corner of polished andesite for these tags (the placeholders' corners do not carry "savetest")
run("forceload add 320 -4 330 8", "fill 328 -60 0 328 -57 0 minecraft:polished_andesite",
    f"plot save outer_corner ar_corner1 {MTAGS} weight=1000 outside=north,east overwrite at tbtest 328 {G} 0 328 {G + 3} 0", "plot reload")

# ------------------------------------------------------------------ A + B: a slanted plot with trees, flowers, grass and a pond
X0, Z0 = 340, 0
MARK = [(X0, Z0), (X0 + 12, Z0), (X0 + 30, Z0 + 18), (X0 + 30, Z0 + 32), (X0, Z0 + 32)]
GATE = (X0 + 15, Z0 + 32)
SEL = f"at tbtest {X0 - 2} -64 {Z0 - 2} {X0 + 32} -20 {Z0 + 34}"
run("forceload add 336 -4 374 38", quiet=True)
for seed in (3, 7):
    print(f"--- A/B, seed {seed}")
    run(f"fill {X0 - 2} -60 {Z0 - 2} {X0 + 32} -40 {Z0 + 34} minecraft:air", f"fill {X0 - 2} -39 {Z0 - 2} {X0 + 32} -20 {Z0 + 34} minecraft:air",
        f"fill {X0 - 2} -61 {Z0 - 2} {X0 + 32} -61 {Z0 + 34} minecraft:grass_block", quiet=True)
    for x, z in MARK:
        run(f"setblock {x} -60 {z} minecraft:redstone_block", quiet=True)
    run(f"setblock {GATE[0]} -60 {GATE[1]} minecraft:gold_block", quiet=True)
    # vegetation: trees all over the plot, flowers, grass, farmland with wheat, and a small pond of water with seagrass
    trees = [(X0 + 5, Z0 + 6), (X0 + 15, Z0 + 14), (X0 + 8, Z0 + 24), (X0 + 22, Z0 + 26), (X0 + 18, Z0 + 6)]
    for i, (x, z) in enumerate(trees):
        run(f"place feature minecraft:{'fancy_oak' if i % 2 else 'oak'} {x} -60 {z}", quiet=True)
    run(f"fill {X0 + 3} -60 {Z0 + 18} {X0 + 6} -60 {Z0 + 20} minecraft:short_grass", f"setblock {X0 + 11} -60 {Z0 + 29} minecraft:poppy",
        f"setblock {X0 + 12} -60 {Z0 + 29} minecraft:dandelion", f"fill {X0 + 25} -61 {Z0 + 29} {X0 + 26} -61 {Z0 + 30} minecraft:water",
        f"setblock {X0 + 25} -61 {Z0 + 30} minecraft:seagrass", quiet=True)
    out = run(f"plot outline {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X0 + 32} -40 {Z0 + 34} a_outline", quiet=True)
    print("  outline:", re.sub(r"\s+", " ", out[0])[:400])
    ol = dump("a_outline")
    left = sorted({(x, z) for (x, y, z), b in ol.items() if y > -61 and any(v in b for v in VEG) and inside_poly(MARK, x + .5, z + .5)})
    check("A: no trees, flowers or grass left inside the plot after the outline", not left, f"{len(left)} columns {left[:6]}")
    water = sum(1 for (x, y, z), b in ol.items() if y == -61 and b.startswith("minecraft:water"))
    check("A: the pond's water stays", water >= 3, f"{water} water blocks")
    ring = {(x, z) for (x, y, z), b in ol.items() if b in ("minecraft:red_concrete", "minecraft:orange_concrete")}
    if not ring:
        check("A: a house was planned", False, out[0][:200]); continue
    xs = [c[0] for c in ring]; zs = [c[1] for c in ring]
    cx, cz = (min(xs) + max(xs)) // 2, (min(zs) + max(zs)) // 2
    # a tree grows on the house's plan after the outline (the builder skipped the clearing, or it grew back)
    run(f"place feature minecraft:fancy_oak {cx} -60 {cz}", f"place feature minecraft:oak {cx + 2} -60 {cz + 1}", quiet=True)
    out = run(f"plot build {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X0 + 32} -20 {Z0 + 34} a_build", quiet=True)
    print("  build:", re.sub(r"\s+", " ", out[0])[:500])
    bl = dump("a_build")
    m = re.search(r"floor at y (-?\d+), roof from y (-?\d+)", out[0])
    roof_from = int(m.group(2)) if m else G + 4
    holes = []
    for x in range(min(xs), max(xs) + 1):
        for z in range(min(zs), max(zs) + 1):
            col = [bl.get((x, y, z), "minecraft:air") for y in range(roof_from, -20)]
            if min(xs) <= x <= max(xs) and min(zs) <= z <= max(zs) and not any(("stairs" in b or "slab" in b or "planks" in b) for b in col):
                holes.append((x, z))
    check("A: the roof has no hole (a tree stood on the plan when the house was built)", not holes, f"{len(holes)} {holes[:6]}")
    veg = sorted({(x, z) for (x, y, z), b in bl.items() if any(v in b for v in ("_log", "_leaves")) and min(xs) <= x <= max(xs) and min(zs) <= z <= max(zs)})
    check("A: no tree left inside the house", not veg, str(veg[:6]))
    # B: the fence's posts
    fence = {(x, z) for (x, y, z), b in ol.items() if b in ("minecraft:yellow_concrete", "minecraft:gold_concrete", "minecraft:gold_block")}
    posts = sorted({(x, z) for (x, y, z), b in bl.items() if b.startswith("minecraft:stone_brick_wall") and (x, z) in fence})
    diag = [p for p in posts if X0 + 12 < p[0] < X0 + 30 and Z0 < p[1] < Z0 + 18]
    side = [(a, b) for a in posts for b in posts if a < b and abs(a[0] - b[0]) <= 1 and abs(a[1] - b[1]) <= 1]
    check("B: the slanted side has posts only every ~4 blocks", 2 <= len(diag) <= 6, f"{len(diag)} posts on 18 x 18 blocks: {diag}")
    check("B: no two posts side by side", not side, str(side[:4]))
    corners = [c for c in MARK if c in posts or any(abs(c[0] - p[0]) <= 1 and abs(c[1] - p[1]) <= 1 for p in posts)]
    check("B: a post on (or by) every corner", len(corners) == len(MARK), f"{len(corners)} of {len(MARK)}")

# ------------------------------------------------------------------ C: the arcade (overhang_depth 1)
print("--- C: an arcade module, 2 deep, overhang_depth 1, on the ground floor")
AX = 322
run("forceload add 320 -4 330 8", f"fill {AX - 1} {G} -1 {AX + 3} {G + 4} 4 minecraft:air",
    # back row (x = AX): the wall, a window; front row (x = AX + 1): pillars at both ends, a beam on top, the middle bay open
    f"fill {AX} {G} 0 {AX} {G + 3} 2 minecraft:stone_bricks", f"setblock {AX} {G + 1} 1 minecraft:glass",
    f"fill {AX + 1} {G} 0 {AX + 1} {G + 2} 0 minecraft:stripped_oak_log", f"fill {AX + 1} {G} 2 {AX + 1} {G + 2} 2 minecraft:stripped_oak_log",
    f"fill {AX + 1} {G + 3} 0 {AX + 1} {G + 3} 2 minecraft:dark_oak_planks",
    f"plot save wall ar_arcade3 {MTAGS} floors=ground weight=1000 facing=EAST overhang=1 overwrite at tbtest {AX} {G} 0 {AX + 1} {G + 3} 2",
    "plot reload")
print("ar_arcade3", json.load(open(SRV + "schematics/wall_modules/ar_arcade3.json", encoding="utf-8")))
CX0, CZ0, CX1, CZ1 = 380, 0, 412, 30
SEL2 = f"at tbtest {CX0 - 2} -64 {CZ0 - 2} {CX1 + 2} -20 {CZ1 + 2}"
run("forceload add 376 -4 416 34", quiet=True)
seen2 = False
for seed in (1, 2, 3, 4, 5, 6, 7, 8):
    run(f"fill {CX0 - 2} -60 {CZ0 - 2} {CX1 + 2} -40 {CZ1 + 2} minecraft:air", f"fill {CX0 - 2} -39 {CZ0 - 2} {CX1 + 2} -20 {CZ1 + 2} minecraft:air",
        f"fill {CX0 - 2} -61 {CZ0 - 2} {CX1 + 2} -61 {CZ1 + 2} minecraft:grass_block",
        f"setblock {CX0} -60 {CZ0} minecraft:redstone_block", f"setblock {CX1} -60 {CZ0} minecraft:redstone_block",
        f"setblock {CX1} -60 {CZ1} minecraft:redstone_block", f"setblock {CX0} -60 {CZ1} minecraft:redstone_block",
        f"setblock {(CX0 + CX1) // 2} -60 {CZ1} minecraft:gold_block", quiet=True)
    out = run(f"plot outline {TAGS} {SEL2} seed {seed}", f"plot dump tbtest {CX0 - 2} -61 {CZ0 - 2} {CX1 + 2} -60 {CZ1 + 2} c_outline",
              f"plot build {TAGS} {SEL2} seed {seed}", f"plot dump tbtest {CX0 - 2} -61 {CZ0 - 2} {CX1 + 2} -40 {CZ1 + 2} c_build", quiet=True)
    ol, bl = dump("c_outline"), dump("c_build")
    front = {(x, z) for (x, y, z), b in ol.items() if b == "minecraft:orange_concrete"}
    ring = {(x, z) for (x, y, z), b in ol.items() if b in ("minecraft:red_concrete", "minecraft:orange_concrete")}
    m = re.search(r"(\d) floor\(s\).*?floor at y (-?\d+)", out[2])
    if not front or not m:
        print(f"seed {seed}: no house / no front:", re.sub(r"\s+", " ", out[2])[:300]); continue
    floors, fy = int(m.group(1)), int(m.group(2))
    xs = [c[0] for c in ring]; zs = [c[1] for c in ring]
    fz = {z for x, z in front}; fx = {x for x, z in front}
    # the front's outward direction: toward the plot's edge the front faces (the gate is on the south: z grows outward)
    if len(fz) == 1:
        lz = next(iter(fz)); inward = (0, -1) if lz == max(zs) else (0, 1)
        line = sorted(front)
    else:
        lx = next(iter(fx)); inward = (-1, 0) if lx == max(xs) else (1, 0)
        line = sorted(front)
    y1 = fy + 1
    on_line = sum(1 for x, z in line if bl.get((x, y1, z), "minecraft:air").startswith("minecraft:stripped_oak_log"))
    behind = sum(1 for x, z in line if bl.get((x + inward[0], y1, z + inward[1]), "minecraft:air") in ("minecraft:stone_bricks", "minecraft:glass")
                 or "door" in bl.get((x + inward[0], y1, z + inward[1]), ""))
    upper_on_line = sum(1 for x, z in line if not bl.get((x, fy + 4 + 1, z), "minecraft:air").endswith(":air")) if floors > 1 else None
    print(f"seed {seed}: {floors} floor(s), front {len(line)} cells (inward {inward}); ground floor: pillars on the line {on_line}, "
          f"the wall one in {behind}; upper floor on the line {upper_on_line}; " + re.sub(r"\s+", " ", out[2])[:220])
    # the arcade's ends: the corner cells on the line are open on the ground floor (the corners moved one in), closed above
    if inward[0] == 0:
        ends = [(min(x for x, z in line) - 1, lz), (max(x for x, z in line) + 1, lz)]
    else:
        ends = [(lx, min(z for x, z in line) - 1), (lx, max(z for x, z in line) + 1)]
    open_ends = sum(1 for x, z in ends if bl.get((x, y1, z), "minecraft:air").endswith(":air"))
    moved = sum(1 for x, z in ends if not bl.get((x + inward[0], y1, z + inward[1]), "minecraft:air").endswith(":air"))
    upper_ends = sum(1 for x, z in ends if not bl.get((x, fy + 4 + 1, z), "minecraft:air").endswith(":air")) if floors > 1 else None
    print(f"   arcade ends open on the ground floor {open_ends}/2, the corners one in {moved}/2, the upper corners on the line {upper_ends}")
    good = (on_line >= 2 and behind >= len(line) - 2 and open_ends == 2 and moved == 2
            and (floors == 1 or (upper_on_line >= len(line) - 2 and upper_ends == 2)))
    check(f"C seed {seed}: the arcade's pillars on the wall line, its wall one block in" + (", the upper floor on the line" if floors > 1 else ""), good)
    seen2 |= floors > 1

check("C: a two-floor house was among the seeds (the overhang above the arcade)", seen2)

# clean up: the pieces aside again (their tags would serve plain city,union,residential)
for n, sub in list(PIECES.items()) + [("ar_arcade3", "wall_modules"), ("ar_corner1", "wall_modules")]:
    for ext in (".json", ".schem"):
        f = SRV + "schematics/" + sub + "/" + n + ext
        if os.path.exists(f):
            if n.startswith("ar_"):
                shutil.copy(f, SRV + "test-saved/" + n + ext)
            os.remove(f)
run("plot reload", "forceload remove all", quiet=True)
print("ALL OK" if ok_all else "SOME FAILED")
