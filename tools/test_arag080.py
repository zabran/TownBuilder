"""Test of 0.8.0 on the LOCAL test server (Arag 11-17): an OUTER CORNER WITH AN OFFSET (a log on the wall line, cobblestone
pillars sticking one block out of both walls) and DECORATIONS (a flower pot in front of every window, a ledge of top slabs at the
top of every plain wall). Sidecars with the tag `offtest` are written into the server's folders, /plot testkit makes their
placeholder schematics, then houses are planned and built and every one is checked: the walls closed, a log on every outer corner
with cobblestone just outside it on both faces and nowhere inside the house, a pot right in front of each window's glass, a slab
ledge along the top of the plain walls, a door.
python -I tools/test_arag080.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import run as rcon_run  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
SRV = "D:/LollyWood-work/mc-test-server/plugins/TownBuilder/"


def dump(name):
    d = json.load(open(SRV + "dumps/" + name + ".json", encoding="utf-8"))
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

TAGS = "city,union,residential,offtest"
OWN = ["city", "union", "residential", "offtest"]
G = -60
SIDECARS = {
    "wall_modules/ot_wall_plain_3": {"module_type": "wall", "length": 3, "height": 4, "has_window": False, "decoration": True,
                                     "decoration_flavours": ["ledge"]},
    "wall_modules/ot_wall_window_3": {"module_type": "wall", "length": 3, "height": 4, "has_window": True, "decoration": True,
                                      "decoration_max_width": 1, "decoration_flavours": ["planter"]},
    "wall_modules/ot_wall_door_3": {"module_type": "wall", "length": 3, "height": 4, "has_door": True},
    "wall_modules/ot_corner_outer": {"module_type": "outer_corner", "length": 1, "height": 4, "offset": 1},
    "decorations/ot_deco_pot_1": {"flavours": ["planter"], "width": 1, "height": 1, "y_offset": 1},
    "decorations/ot_deco_ledge_3": {"flavours": ["ledge"], "width": 3, "height": 1, "y_offset": 3},
}


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


def write_sidecars():
    for k, v in SIDECARS.items():
        os.makedirs(SRV + "schematics/" + k.split("/")[0], exist_ok=True)
        j = {"tags": OWN, "allowed_floors": ["ground", "upper"], "weight": 10, "front_facing": "NORTH"}
        j.update(v)
        for ext in (".schem",):
            if os.path.exists(SRV + "schematics/" + k + ext):
                os.remove(SRV + "schematics/" + k + ext)
        json.dump(j, open(SRV + "schematics/" + k + ".json", "w", encoding="utf-8"), indent=2)


def remove_sidecars():
    for k in SIDECARS:
        for ext in (".json", ".schem"):
            f = SRV + "schematics/" + k + ext
            if os.path.exists(f):
                os.remove(f)


write_sidecars()
run("plot testkit", "plot reload", f"plot info {TAGS}")
X0, Z0, X1, Z1 = 600, 0, 635, 34
SEL = f"at tbtest {X0 - 2} -64 {Z0 - 2} {X1 + 2} -30 {Z1 + 2}"
run("forceload add 596 -4 640 38", quiet=True)
ok_all = True
N4 = [(1, 0), (-1, 0), (0, 1), (0, -1)]
for seed in (1, 2, 3, 5, 8):
    run(f"fill {X0 - 3} -60 {Z0 - 3} {X1 + 3} -46 {Z1 + 3} minecraft:air", f"fill {X0 - 3} -45 {Z0 - 3} {X1 + 3} -30 {Z1 + 3} minecraft:air",
        f"fill {X0 - 3} -61 {Z0 - 3} {X1 + 3} -61 {Z1 + 3} minecraft:grass_block",
        f"setblock {X0} -60 {Z0} minecraft:redstone_block", f"setblock {X1} -60 {Z0} minecraft:redstone_block",
        f"setblock {X1} -60 {Z1} minecraft:redstone_block", f"setblock {X0} -60 {Z1} minecraft:redstone_block",
        f"setblock {(X0 + X1) // 2} -60 {Z1} minecraft:gold_block", quiet=True)
    out = run(f"plot outline {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 3} -61 {Z0 - 3} {X1 + 3} -60 {Z1 + 3} t8_outline",
              f"plot build {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 3} -61 {Z0 - 3} {X1 + 3} -45 {Z1 + 3} t8_build", quiet=True)
    ol, bl = dump("t8_outline"), dump("t8_build")
    ring = {(x, z) for (x, y, z), b in ol.items() if b in ("minecraft:red_concrete", "minecraft:orange_concrete")}
    if not ring:
        print("seed", seed, "no house:", out[0].strip()[:300]); ok_all = False; continue
    house = house_cells(ring)
    wy = min(y for (x, y, z), b in bl.items() if (x, z) in ring and "spruce_planks" in b)   # the ground floor's foot
    holes = sorted(c for c in ring if bl.get((c[0], wy + 1, c[1]), "minecraft:air").endswith(":air"))
    # the corners: the outer ones of the ring (a ring cell with exactly two ring neighbours at a right angle, convex)
    logs = {(x, z) for (x, y, z), b in bl.items() if y == wy + 1 and "stripped_spruce_log" in b}
    cobble = {(x, z) for (x, y, z), b in bl.items() if y == wy + 1 and b == "minecraft:cobblestone"}
    cobble_in = sorted(c for c in cobble if c in house)
    bad_corner = []
    for c in logs:
        if c not in ring:
            bad_corner.append(("log off the ring", c)); continue
        outside = [(c[0] + dx, c[1] + dz) for dx, dz in N4 if (c[0] + dx, c[1] + dz) not in house]
        if len(outside) != 2 or not all(o in cobble for o in outside):
            bad_corner.append(("pillars", c, outside))
    # the decorations: pots right in front of a window's glass, ledges along the top of the plain walls
    glass = {(x, y, z) for (x, y, z), b in bl.items() if "glass" in b and (x, z) in ring}
    pots = {(x, y, z) for (x, y, z), b in bl.items() if b.startswith("minecraft:potted_")}
    bad_pot = [p for p in pots if not ((p[0], p[2]) not in house and any((p[0] + dx, p[1], p[2] + dz) in glass for dx, dz in N4))]
    slabs = {(x, y, z) for (x, y, z), b in bl.items() if b.startswith("minecraft:spruce_slab") and "type=top" in b and (x, z) not in house}
    bad_slab = [s for s in slabs if not any((s[0] + dx, s[2] + dz) in ring for dx, dz in N4)]
    doors = [(x, y, z) for (x, y, z), b in bl.items() if "_door[" in b and "half=lower" in b and (x, z) in ring]
    windows = len({(x, z) for x, y, z in glass})
    good = (not holes and logs and not bad_corner and not cobble_in and pots and not bad_pot and slabs and not bad_slab and doors
            and len(cobble) == 2 * len(logs))
    ok_all &= bool(good)
    print(f"seed {seed}: ring {len(ring)}, holes {len(holes)} {holes[:4]}; corners {len(logs)} with pillars {len(cobble)} "
          f"(inside the house {cobble_in[:3]}, bad {bad_corner[:3]}); windows {windows}, pots {len(pots)} (misplaced {bad_pot[:3]}); "
          f"ledge slabs {len(slabs)} (misplaced {bad_slab[:3]}); doors {len(doors)}; "
          + " | ".join(l for l in out[2].splitlines() if "decoration" in l or "corner" in l)[:300] + f" -> {'OK' if good else 'FAIL'}")

remove_sidecars()
run("plot reload", "forceload remove all", quiet=True)
print("ALL OK" if ok_all else "SOME FAILED")
