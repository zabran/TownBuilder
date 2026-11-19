package cz.lollywood.townbuilder.geom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Phase 1's layout, pure Java: the house's plan (a rectangle, L or T whose every wall length the modules can fill, with a door
 * on the side facing the nearest gate) and free rectangles for the shed, pond and garden.
 */
public final class Planner {
    public record House(Footprint fp, Footprint.Segment front, Dir frontDir) {}

    private final Set<Long> free;          // cells the house and the rest may use (inside the plot, off the fence, with margin)
    private final int bx0, bz0, bx1, bz1;
    private final int[][] pre;             // prefix sums over free
    private final Random rnd;
    /** The longest side of the house's main body against its shortest (the layout's max_house_aspect). */
    public double maxAspect = 2.0;
    /** How wide the style's outer corner modules are (0.5.0): a side = 2 corners + modules. */
    public int cornerWidth = 1;
    /** How wide the inner corner modules are (0.6.0). */
    public int innerCornerWidth = 1;

    /**
     * @param inside cells inside the plot and not on the fence
     * @param margin cells kept free next to the fence
     */
    public Planner(Set<Long> inside, int margin, Random rnd) {
        this.rnd = rnd;
        Set<Long> f = new HashSet<>();
        for (long k : inside) {
            int x = PlotPolygon.kx(k), z = PlotPolygon.kz(k);
            boolean ok = true;
            for (int dx = -margin; dx <= margin && ok; dx++)
                for (int dz = -margin; dz <= margin && ok; dz++) if (!inside.contains(PlotPolygon.key(x + dx, z + dz))) ok = false;
            if (ok) f.add(k);
        }
        this.free = f;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long k : f) {
            x0 = Math.min(x0, PlotPolygon.kx(k)); z0 = Math.min(z0, PlotPolygon.kz(k));
            x1 = Math.max(x1, PlotPolygon.kx(k)); z1 = Math.max(z1, PlotPolygon.kz(k));
        }
        if (f.isEmpty()) { x0 = z0 = 0; x1 = z1 = -1; }
        bx0 = x0; bz0 = z0; bx1 = x1; bz1 = z1;
        int w = Math.max(0, bx1 - bx0 + 1), d = Math.max(0, bz1 - bz0 + 1);
        pre = new int[w + 1][d + 1];
        for (int i = 0; i < w; i++)
            for (int j = 0; j < d; j++)
                pre[i + 1][j + 1] = pre[i][j + 1] + pre[i + 1][j] - pre[i][j] + (f.contains(PlotPolygon.key(bx0 + i, bz0 + j)) ? 1 : 0);
    }

    public boolean fits(Footprint.Rect r) {
        if (r.x0() < bx0 || r.z0() < bz0 || r.x1() > bx1 || r.z1() > bz1) return false;
        int i0 = r.x0() - bx0, j0 = r.z0() - bz0, i1 = r.x1() - bx0 + 1, j1 = r.z1() - bz0 + 1;
        return pre[i1][j1] - pre[i0][j1] - pre[i1][j0] + pre[i0][j0] == r.area();
    }

    /**
     * @param reach which wall lengths the wall modules can make (index = length)
     * @param doorLens lengths of the door modules
     * @param target where the house should face (the nearest gate), or null
     */
    public House house(List<String> shapes, boolean[] reach, List<Integer> doorLens, int maxArea, double[] target) {
        List<Integer> totals = new ArrayList<>();
        int cw = Math.max(1, cornerWidth);
        for (int t = Math.max(5, 2 * cw + 1); t - 2 * cw < reach.length; t++) if (reach[t - 2 * cw]) totals.add(t);
        if (totals.isEmpty()) return null;
        double tx = target != null ? target[0] : (bx0 + bx1) / 2.0, tz = target != null ? target[1] : (bz0 + bz1) / 2.0;
        List<Footprint> cands = new ArrayList<>();
        // the rectangles that fit, biggest first (up to maxArea)
        List<int[]> sizes = new ArrayList<>();
        for (int w : totals) for (int d : totals)
            if (w * d <= maxArea && Math.max(w, d) <= maxAspect * Math.min(w, d)) sizes.add(new int[]{w, d});
        // biggest first; of equal areas the squarer
        sizes.sort(Comparator.comparingInt((int[] s) -> -s[0] * s[1]).thenComparingInt(s -> Math.abs(s[0] - s[1])));
        String shape = shapes.isEmpty() ? "rectangle" : shapes.get(rnd.nextInt(shapes.size()));
        for (int[] s : sizes) {
            Footprint.Rect at = bestSpot(s[0], s[1], tx, tz, null);
            if (at == null) continue;
            if (shape.equalsIgnoreCase("rectangle")) {
                cands.add(new Footprint(List.of(at)));
            } else {
                for (Footprint f : wingsOn(s[0], s[1], totals, shape, maxArea, tx, tz)) cands.add(f);
            }
            if (cands.size() >= 12) break;
        }
        if (cands.isEmpty() && !shape.equalsIgnoreCase("rectangle")) return house(List.of("rectangle"), reach, doorLens, maxArea, target);
        cands.sort(Comparator.comparingInt((Footprint f) -> -f.area()));
        for (Footprint f : cands) {
            House h = validate(f, reach, doorLens, tx, tz, cornerWidth, innerCornerWidth);
            if (h != null) return h;
        }
        // no L or T whose every wall the modules can make (with one module length most wing sizes fail): a rectangle instead
        if (!shape.equalsIgnoreCase("rectangle")) return house(List.of("rectangle"), reach, doorLens, maxArea, target);
        return null;
    }

    private List<Footprint> wingsOn(int w, int d, List<Integer> totals, String shape, int maxArea, double tx, double tz) {
        List<Footprint> out = new ArrayList<>();
        boolean tee = shape.equalsIgnoreCase("T_shape");
        for (int ww : totals) {
            if (ww >= (tee ? w - 1 : w) && ww >= (tee ? d - 1 : d)) continue;   // the wing narrower than the side it sits on
            for (int dd = 3; dd <= 9; dd++) {
                if (w * d + ww * dd > maxArea) break;
                for (Dir side : Dir.values()) {
                    int sideLen = side == Dir.NORTH || side == Dir.SOUTH ? w : d;
                    if (ww > sideLen - (tee ? 4 : 2)) continue;
                    // place the main body first where it fits, then try the wing on it
                    for (int pass = 0; pass < (tee ? 1 : 2); pass++) {
                        Footprint.Rect main = bestSpot(w, d, tx, tz, null);
                        if (main == null) return out;
                        int off = tee ? (sideLen - ww) / 2 : (pass == 0 ? 0 : sideLen - ww);
                        Footprint.Rect wing = switch (side) {
                            case NORTH -> new Footprint.Rect(main.x0() + off, main.z0() - dd, main.x0() + off + ww - 1, main.z0() - 1);
                            case SOUTH -> new Footprint.Rect(main.x0() + off, main.z1() + 1, main.x0() + off + ww - 1, main.z1() + dd);
                            case WEST -> new Footprint.Rect(main.x0() - dd, main.z0() + off, main.x0() - 1, main.z0() + off + ww - 1);
                            case EAST -> new Footprint.Rect(main.x1() + 1, main.z0() + off, main.x1() + dd, main.z0() + off + ww - 1);
                        };
                        if (fits(main) && fits(wing)) out.add(new Footprint(List.of(main, wing)));
                    }
                }
            }
            if (out.size() > 8) break;
        }
        return out;
    }

    /** The spot for a w x d rectangle nearest the target, or null. */
    public Footprint.Rect bestSpot(int w, int d, double tx, double tz, Set<Long> taken) {
        Footprint.Rect best = null;
        double bestD = Double.MAX_VALUE;
        for (int x = bx0; x + w - 1 <= bx1; x++)
            for (int z = bz0; z + d - 1 <= bz1; z++) {
                Footprint.Rect r = new Footprint.Rect(x, z, x + w - 1, z + d - 1);
                if (!fits(r) || (taken != null && overlaps(r, taken))) continue;
                double cx = x + (w - 1) / 2.0, cz = z + (d - 1) / 2.0;
                double dist = (cx - tx) * (cx - tx) + (cz - tz) * (cz - tz);
                if (dist < bestD) { bestD = dist; best = r; }
            }
        return best;
    }

    /** The spot for a w x d rectangle FARTHEST from a point (a shed behind the house), avoiding taken cells. */
    public Footprint.Rect farSpot(int w, int d, double tx, double tz, Set<Long> taken) {
        Footprint.Rect best = null;
        double bestD = -1;
        for (int x = bx0; x + w - 1 <= bx1; x++)
            for (int z = bz0; z + d - 1 <= bz1; z++) {
                Footprint.Rect r = new Footprint.Rect(x, z, x + w - 1, z + d - 1);
                if (!fits(r) || overlaps(r, taken)) continue;
                double cx = x + (w - 1) / 2.0, cz = z + (d - 1) / 2.0;
                double dist = (cx - tx) * (cx - tx) + (cz - tz) * (cz - tz) + rnd.nextDouble();
                if (dist > bestD) { bestD = dist; best = r; }
            }
        return best;
    }

    private static boolean overlaps(Footprint.Rect r, Set<Long> taken) {
        for (int x = r.x0(); x <= r.x1(); x++)
            for (int z = r.z0(); z <= r.z1(); z++) if (taken.contains(PlotPolygon.key(x, z))) return true;
        return false;
    }

    /** Every wall length fillable, and a side toward the target that takes a door. */
    public static House validate(Footprint f, boolean[] reach, List<Integer> doorLens, double tx, double tz) {
        return validate(f, reach, doorLens, tx, tz, 1, 1);
    }

    /** The same with outer corners {@code cw} wide: the walls between them are that much shorter. */
    public static House validate(Footprint f, boolean[] reach, List<Integer> doorLens, double tx, double tz, int cw, int cwi) {
        Footprint.Ring ring;
        try {
            ring = Footprint.trimmed(f.ring(), cw, cwi);
        } catch (IllegalStateException e) {
            return null;
        }
        for (Footprint.Segment s : ring.segments()) if (s.length() >= reach.length || !reach[s.length()]) return null;
        Footprint.Rect b = f.bounds();
        double cx = (b.x0() + b.x1()) / 2.0, cz = (b.z0() + b.z1()) / 2.0;
        Dir want = Dir.nearest(tx - cx, tz - cz);
        for (Dir d : new Dir[]{want, want.cw(), want.ccw(), want.opposite()}) {
            Footprint.Segment bestSeg = null;
            for (Footprint.Segment s : ring.segments()) {
                if (s.normal() != d) continue;
                boolean door = doorLens.isEmpty() || doorLens.stream().anyMatch(l -> SegmentFiller.doorStart(s.length(), l, reach) >= 0);
                if (door && (bestSeg == null || s.length() > bestSeg.length())) bestSeg = s;
            }
            if (bestSeg != null) return new House(f, bestSeg, d);
        }
        return null;
    }

    /** Cells of a footprint plus a ring of {@code pad} around it. */
    public static Set<Long> padded(Footprint.Rect r, int pad) {
        Set<Long> s = new HashSet<>();
        for (int x = r.x0() - pad; x <= r.x1() + pad; x++)
            for (int z = r.z0() - pad; z <= r.z1() + pad; z++) s.add(PlotPolygon.key(x, z));
        return s;
    }
}
