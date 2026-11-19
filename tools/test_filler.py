"""Test of 0.4.1's filler on the LOCAL test server: with only 3-long wall modules (and a 3-long door), most wall lengths cannot
be made exactly; every wall must still close (filler columns cut from a plain module) and the front wall must get its door.
Needs the pieces test_save.py saved (moved aside to plugins/TownBuilder/test-saved/ after it). python -I tools/test_filler.py"""
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
PIECES = {"st_wall3": "wall_modules", "st_door3": "wall_modules", "st_outer": "wall_modules", "st_inner": "wall_modules"}


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


# 1. only the 3-long pieces (no st_wall2): every wall length that is not a multiple of 3 needs filler
for n, sub in PIECES.items():
    for ext in (".json", ".schem"):
        if os.path.exists(SRV + "test-saved/" + n + ext):
            shutil.copy(SRV + "test-saved/" + n + ext, SRV + "schematics/" + sub + "/" + n + ext)
run("plot reload", f"plot info {TAGS}")

X0, Z0, X1, Z1 = 200, 0, 235, 34
SEL = f"at tbtest {X0 - 2} -64 {Z0 - 2} {X1 + 2} -30 {Z1 + 2}"
run("forceload add 196 -4 239 38", quiet=True)
ok_all = True
for seed in (7, 11, 23, 42):
    run(f"fill {X0 - 2} -60 {Z0 - 2} {X1 + 2} -46 {Z1 + 2} minecraft:air", f"fill {X0 - 2} -45 {Z0 - 2} {X1 + 2} -30 {Z1 + 2} minecraft:air",
        f"fill {X0 - 2} -61 {Z0 - 2} {X1 + 2} -61 {Z1 + 2} minecraft:grass_block",
        f"setblock {X0} -60 {Z0} minecraft:redstone_block", f"setblock {X1} -60 {Z0} minecraft:redstone_block",
        f"setblock {X1} -60 {Z1} minecraft:redstone_block", f"setblock {X0} -60 {Z1} minecraft:redstone_block",
        f"setblock {(X0 + X1) // 2} -60 {Z1} minecraft:gold_block", quiet=True)
    o = run(f"plot outline {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X1 + 2} -60 {Z1 + 2} tf_outline0", quiet=True)
    ol = dump("tf_outline0")
    cells = {(x, z): (y, b) for (x, y, z), b in ol.items() if b in ("minecraft:red_concrete", "minecraft:orange_concrete")}
    if not cells:
        print("seed", seed, "no house outline:", o[0].strip()[:300]); ok_all = False; continue
    # 2. the builder's hand edit: stretch the house one block east at its middle (every wall crossing the cut gets 1 longer)
    cut = (min(x for x, z in cells) + max(x for x, z in cells)) // 2
    new = {}
    for (x, z), v in cells.items():
        new[(x + 1, z) if x > cut else (x, z)] = v
    for (x, z), v in cells.items():
        if x == cut and (cut + 1, z) in cells:
            new[(cut + 1, z)] = v
    cmds = [f"setblock {x} {y} {z} minecraft:air" for (x, z), (y, b) in cells.items() if (x, z) not in new]
    cmds += [f"setblock {x} {y} {z} {b}" for (x, z), (y, b) in new.items()]
    run(*cmds, quiet=True)
    out = run(f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X1 + 2} -60 {Z1 + 2} tf_outline",
              f"plot build {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 2} -61 {Z0 - 2} {X1 + 2} -45 {Z1 + 2} tf_build", quiet=True)
    out = [o[0]] + out
    ol, bl = dump("tf_outline"), dump("tf_build")
    ring = {(x, z) for (x, y, z), b in ol.items() if b in ("minecraft:red_concrete", "minecraft:orange_concrete")}
    front = {(x, z) for (x, y, z), b in ol.items() if b == "minecraft:orange_concrete"}
    holes = sorted((x, z) for (x, z) in ring if bl.get((x, -58, z), "minecraft:air").endswith(":air"))
    doors = [(x, y, z) for (x, y, z), b in bl.items() if "_door[" in b and "half=lower" in b and (x, z) in ring]
    filler = [ln for ln in out[2].split("\n") if "filler" in ln]
    xs = [p[0] for p in ring]; zs = [p[1] for p in ring]
    good = not holes and doors
    ok_all &= bool(good)
    print(f"seed {seed}: house x {min(xs)}..{max(xs)} z {min(zs)}..{max(zs)}, front {len(front)} cells; "
          f"holes in the wall line at y -58: {len(holes)} {holes[:6]}; doors on the wall line: {len(doors)}; "
          f"{filler[0].strip() if filler else 'no filler needed'} -> {'OK' if good else 'FAIL'}")

# 2. clean up: the pieces back aside (their tags would serve plain city,union,residential)
for n, sub in PIECES.items():
    for ext in (".json", ".schem"):
        f = SRV + "schematics/" + sub + "/" + n + ext
        if os.path.exists(f):
            os.remove(f)
run("plot reload", "forceload remove all", quiet=True)
print("ALL OK" if ok_all else "SOME FAILED")
