"""Render a /plot dump (plugins/TownBuilder/dumps/<name>.json) as an isometric picture with LollyWood-Lore's mc_iso renderer.
python -I tools/render_dump.py <dump.json> <out.png> [--view se|sw|nw|ne] [--top] [--drop name,name]"""
import argparse
import json
import sys

import numpy as np

sys.path.insert(0, "C:/Users/Marek/Documents/LollyWood-Lore/_tools")
import mc_iso  # noqa: E402
import mc_render as M  # noqa: E402


def load(path, drop=()):
    d = json.load(open(path, encoding="utf-8"))
    W, H, D = d["size"]
    names = [p.split("[")[0].replace("minecraft:", "") for p in d["palette"]]
    pal = [("air", None)]
    idx = {}
    remap = []
    for nm in names:
        if nm in ("air", "cave_air", "void_air") or nm in drop:
            remap.append(0)
            continue
        if nm not in idx:
            idx[nm] = len(pal)
            pal.append((nm, M.color_of(nm)))
        remap.append(idx[nm])
    flat = np.array(d["data"], np.int32)
    vol = np.array(remap, np.int32)[flat].reshape(H, D, W)
    return vol, pal


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("dump")
    ap.add_argument("out")
    ap.add_argument("--view", default="se")
    ap.add_argument("--drop", default="")
    ap.add_argument("--T", type=int, default=10)
    a = ap.parse_args()
    vol, pal = load(a.dump, set(x for x in a.drop.split(",") if x))
    vol, _k = mc_iso.rotate(vol, a.view)
    img = mc_iso.render(vol, pal, T=a.T)
    img.save(a.out)
    print("saved", a.out, img.size)
