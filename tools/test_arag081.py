"""Test of 0.8.1 on the LOCAL test server (Arag 11-19: "z nějakého důvodu moc nefunguje seed generator pro outline"): the same
plot outlined with eight seeds must give several different plans (the house's size and place, the garden, pond and shed), the
same seed twice the same plan, and every house must still face the gate.
python -I tools/test_arag081.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon import run as rcon_run  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
SRV = "D:/LollyWood-work/mc-test-server/plugins/TownBuilder/"
TAGS = "city,union,residential"
X0, Z0, X1, Z1 = 700, 0, 735, 34
SEL = f"at tbtest {X0 - 2} -64 {Z0 - 2} {X1 + 2} -30 {Z1 + 2}"
COLOURS = {"minecraft:red_concrete": "house", "minecraft:orange_concrete": "front", "minecraft:green_concrete": "garden",
           "minecraft:cyan_concrete": "pond", "minecraft:blue_concrete": "shed"}


def run(*cmds):
    return [rcon_run([c])[0] for c in cmds]


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


def outline(seed):
    run(f"fill {X0 - 3} -60 {Z0 - 3} {X1 + 3} -46 {Z1 + 3} minecraft:air",
        f"fill {X0 - 3} -61 {Z0 - 3} {X1 + 3} -61 {Z1 + 3} minecraft:grass_block",
        f"setblock {X0} -60 {Z0} minecraft:redstone_block", f"setblock {X1} -60 {Z0} minecraft:redstone_block",
        f"setblock {X1} -60 {Z1} minecraft:redstone_block", f"setblock {X0} -60 {Z1} minecraft:redstone_block",
        f"setblock {(X0 + X1) // 2} -60 {Z1} minecraft:gold_block")
    out = run(f"plot outline {TAGS} {SEL} seed {seed}", f"plot dump tbtest {X0 - 3} -60 {Z0 - 3} {X1 + 3} -60 {Z1 + 3} t81")[0]
    plan = {}
    for (x, y, z), b in dump("t81").items():
        if b in COLOURS:
            plan.setdefault(COLOURS[b], set()).add((x, z))
    return plan, out


def box(cells):
    xs = [x for x, z in cells]; zs = [z for x, z in cells]
    return (min(xs), min(zs), max(xs), max(zs))


run("plot testkit", "plot reload", "forceload add 696 -4 740 38")
plans, ok = {}, True
for seed in range(1, 9):
    plan, out = outline(seed)
    if "house" not in plan:
        print("seed", seed, "NO HOUSE:", out.strip()[:300]); ok = False; continue
    key = tuple(sorted((k, box(v)) for k, v in plan.items()))
    plans[seed] = key
    front = plan.get("front", set())
    faces = bool(front) and max(z for x, z in front) == max(z for x, z in plan["house"] | front)   # the front on the gate (south) side
    ok &= faces
    print(f"seed {seed}: " + ", ".join(f"{k} {b[2] - b[0] + 1}x{b[3] - b[1] + 1} at {b[0]},{b[1]}" for k, b in key)
          + ("" if faces else "  FRONT NOT TOWARD THE GATE"))
again, _ = outline(3)
same = tuple(sorted((k, box(v)) for k, v in again.items())) == plans.get(3)
distinct = len(set(plans.values()))
print(f"{distinct} different plans of {len(plans)}; seed 3 again the same: {same}")
ok &= same and distinct >= 4
run(f"fill {X0 - 3} -60 {Z0 - 3} {X1 + 3} -46 {Z1 + 3} minecraft:air", "forceload remove all")
print("ALL OK" if ok else "SOME FAILED")
