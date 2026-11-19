"""Test of the wide corners on the LOCAL test server (0.5.0 outer, 0.6.0 inner): a 2 x 2 outer corner (polished andesite, a
furnace marking its front and an observer its other side, both on its outer cell) and a 2 x 2 inner corner (an L of polished
diorite, its notch left air, the markers on its elbow) are built and saved by command beside test_save.py's 3-long walls and
door; then houses are planned and built (rectangles and L's) and every one is checked: the walls closed, each outer corner's box
inside the house and its furnace looking out, each inner corner's two arms on the wall line and its notch outside, a door.
python -I tools/test_widecorner.py   (needs test_save.py's pieces in plugins/TownBuilder/test-saved/)"""
import json
import os
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
DIRS = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


def run(*cmds, quiet=False):
    out = []
    for c in cmds:
        r = rcon_run([c])[0]
        out.append(r)
        if not quiet:
            print(">", c)
            if r.strip():
                print("  " + r.strip().replace("\n", "\n  "))
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


def house_cells(ring):
    """The ring and everything it encloses (a flood fill from outside the bounding box)."""
    xs = [x for x, z in ring]; zs = [z for x, z in ring]
    x0, x1, z0, z1 = min(xs) - 1, max(xs) + 1, min(zs) - 1, max(zs) + 1
    out, todo = set(), [(x0, z0)]
    while todo:
        c = todo.pop()
        if c in out or c in ring or not (x0 <= c[0] <= x1 and z0 <= c[1] <= z1):
            continue
        out.add(c)
        todo += [(c[0] + 1, c[1]), (c[0] - 1, c[1]), (c[0], c[1] + 1), (c[0], c[1] - 1)]
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)} - out


# 1. the test pieces back in (3-long walls, the door, the inner corner) and the 2 x 2 outer corner built and saved
for n, sub in PIECES.items():
    for ext in (".json", ".schem"):
        if os.path.exists(SRV + "test-saved/" + n + ext):
            shutil.copy(SRV + "test-saved/" + n + ext, SRV + "schematics/" + sub + "/" + n + ext)
run("forceload add 296 16 310 24", quiet=True)
run(f"fill 299 {G} 19 302 {G + 4} 22 minecraft:air", f"fill 300 {G} 20 301 {G + 3} 21 minecraft:polished_andesite",
    f"setblock 301 {G + 1} 20 minecraft:furnace[facing=east]", f"setblock 301 {G + 2} 20 minecraft:observer[facing=north]",
    f"plot save outer_corner wc_outer2 {MTAGS} weight=1000 outside=north,east overwrite at tbtest 300 {G} 20 301 {G + 3} 21",
    f"fill 304 {G} 19 307 {G + 4} 22 minecraft:air", f"fill 305 {G} 20 306 {G + 3} 20 minecraft:polished_diorite",
    f"fill 306 {G} 21 306 {G + 3} 21 minecraft:polished_diorite",
    f"setblock 306 {G + 1} 20 minecraft:furnace[facing=south]", f"setblock 306 {G + 2} 20 minecraft:observer[facing=west]",
    f"plot save inner_corner wc_inner2 {MTAGS} weight=1000 outside=south,west overwrite at tbtest 305 {G} 20 306 {G + 3} 21",
    "plot reload", f"plot info {TAGS}")
print("wc_outer2", json.load(open(SRV + "schematics/wall_modules/wc_outer2.json", encoding="utf-8")))
print("wc_inner2", json.load(open(SRV + "schematics/wall_modules/wc_inner2.json", encoding="utf-8")))

X0, Z0, X1, Z1 = 245, 0, 280, 34
SEL = f"at tbtest {X0 - 2} -64 {Z0 - 2} {X1 + 2} -30 {Z1 + 2}"
run("forceload add 241 -4 284 38", quiet=True)
ok_all = True
shapes = []
for seed in (1, 2, 3, 4, 5, 7, 11, 42):
    run(f"fill {X0 - 2} -60 {Z0 - 2} {X1 + 2} -46 {Z1 + 2} minecraft:air", f"fill {X0 - 2} -45 {Z0 - 2} {X1 + 2} -30 {Z1 + 2} minecraft:air",
        f"fill {X0 - 2} -61 {Z0 - 2} {X1 + 2} -61 {Z1 + 2} minecraft:grass_block",
        f"setblock {X0} -60 {Z0} minecraft:redstone_block", f"setblock {X1} -60 {Z0} minecraft:redstone_block",
        f"setblock {X1} -60 {Z1} minecraft:redstone_block", f"setblock {X0} -60 {Z1} minecraft:redstone_block",
        f"setblock {(X0 + X1) // 2} -60 {Z1} minecraft:gold_block", quiet=True)
    out = run(f"plot outline {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X1 + 2} -60 {Z1 + 2} wc_outline",
              f"plot build {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X1 + 2} -45 {Z1 + 2} wc_build", quiet=True)
    ol, bl = dump("wc_outline"), dump("wc_build")
    ring = {(x, z) for (x, y, z), b in ol.items() if b in ("minecraft:red_concrete", "minecraft:orange_concrete")}
    if not ring:
        print("seed", seed, "no house:", out[0].strip()[:300]); ok_all = False; continue
    house = house_cells(ring)
    holes = sorted(c for c in ring if bl.get((c[0], G + 2, c[1]), "minecraft:air").endswith(":air"))
    andesite = [(x, z) for (x, y, z), b in bl.items() if y == G + 2 and b == "minecraft:polished_andesite"]
    outside_box = [c for c in andesite if c not in house]
    furn = [(x, z, b) for (x, y, z), b in bl.items() if y == G + 1 and b.startswith("minecraft:furnace") and (x, z) in ring]
    wide = [(x, z, b) for x, z, b in furn if any((x + dx, G + 2, z + dz) in bl and bl[(x + dx, G + 2, z + dz)] == "minecraft:polished_andesite"
                                                   for dx in (-1, 0, 1) for dz in (-1, 0, 1))]
    bad_front = []
    for x, z, b in wide:
        f = next(k for k in DIRS if "facing=" + k in b)
        if (x + DIRS[f][0], z + DIRS[f][1]) in house:
            bad_front.append((x, z, f))
    doors = [(x, y, z) for (x, y, z), b in bl.items() if "_door[" in b and "half=lower" in b and (x, z) in ring]
    xs = [p[0] for p in ring]; zs = [p[1] for p in ring]
    # the inner corners: the elbow's observer with diorite arms on the ring, the notch between them outside the house and empty
    diorite = {(x, z) for (x, y, z), b in bl.items() if y == G + 2 and b == "minecraft:polished_diorite"}
    off_ring = sorted(c for c in diorite if c not in ring)
    elbows, bad_notch = 0, []
    for (x, y, z), b in bl.items():
        if y != G + 2 or not b.startswith("minecraft:observer") or (x, z) not in ring:
            continue
        arms = [(dx, dz) for dx, dz in DIRS.values() if (x + dx, z + dz) in diorite]
        if len(arms) != 2:
            continue
        elbows += 1
        notch = (x + arms[0][0] + arms[1][0], z + arms[0][1] + arms[1][1])
        if notch in house or not bl.get((notch[0], G + 2, notch[1]), "minecraft:air").endswith(":air"):
            bad_notch.append(notch)
    shape = "L" if elbows else "rectangle"
    shapes.append(shape)
    good = (not holes and not outside_box and not bad_front and doors and len(andesite) == 3 * len(wide) and wide
            and not off_ring and not bad_notch and len(diorite) == 2 * elbows)
    ok_all &= bool(good)
    print(f"seed {seed}: house x {min(xs)}..{max(xs)} z {min(zs)}..{max(zs)} ({len(ring)} ring cells); holes at y {G + 2}: {len(holes)} {holes[:5]}; "
          f"wide corners {len(wide)} (andesite {len(andesite)}, outside the house {len(outside_box)}); fronts looking in: {bad_front}; "
          f"inner corners {elbows} (diorite {len(diorite)}, off the wall line {off_ring[:4]}, bad notches {bad_notch}); {shape}; "
          f"doors {len(doors)} -> {'OK' if good else 'FAIL'}")

# 2. clean up: the pieces aside again (their tags would serve plain city,union,residential)
for n, sub in list(PIECES.items()) + [("wc_outer2", "wall_modules"), ("wc_inner2", "wall_modules")]:
    for ext in (".json", ".schem"):
        f = SRV + "schematics/" + sub + "/" + n + ext
        if os.path.exists(f):
            if n.startswith("wc_"):
                shutil.copy(f, SRV + "test-saved/" + n + ext)
            os.remove(f)
run("plot reload", "forceload remove all", quiet=True)
print("shapes:", shapes)
if "L" not in shapes:
    print("no L house among the seeds: the inner corners were not exercised"); ok_all = False
print("ALL OK" if ok_all else "SOME FAILED")
