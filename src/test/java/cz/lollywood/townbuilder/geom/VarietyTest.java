package cz.lollywood.townbuilder.geom;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 0.8.1 (Arag 11-19: "z nějakého důvodu moc nefunguje seed generator pro outline"): a new seed must give a new plan. */
class VarietyTest {
    private static Set<Long> plot(int w, int d) {
        Set<Long> inside = new HashSet<>();
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) inside.add(PlotPolygon.key(x, z));
        return inside;
    }

    private static String plan(long seed, double variety, List<String> shapes) {
        Planner pl = new Planner(plot(40, 34), 2, new Random(seed));
        pl.variety = variety;
        boolean[] reach = SegmentFiller.reachable(List.of(3, 4, 5), 80);
        Planner.House h = pl.house(shapes, reach, List.of(3), 400, new double[]{20, 33});
        assertNotNull(h, "seed " + seed);
        StringBuilder sb = new StringBuilder();
        for (Footprint.Rect r : h.fp().wings) sb.append(r).append(';');
        // the shed behind it, as the outline phase asks for it
        Set<Long> taken = new HashSet<>(h.fp().cells());
        sb.append(" shed ").append(pl.farSpot(5, 4, 20, 33, taken));
        return sb.toString();
    }

    @Test
    void newSeedsGiveNewPlans() {
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 12; seed++) seen.add(plan(seed, 0.3, List.of("rectangle")));
        assertTrue(seen.size() >= 6, "12 seeds gave only " + seen.size() + " plans: " + seen);
    }

    @Test
    void theSameSeedGivesTheSamePlan() {
        for (long seed = 0; seed < 5; seed++) assertEquals(plan(seed, 0.3, List.of("rectangle", "L_shape")), plan(seed, 0.3, List.of("rectangle", "L_shape")));
    }

    @Test
    void varietyZeroKeepsTheOldBestPlan() {
        // 0.8.0's plan: the biggest house nearest the gate, the same for every seed
        String first = plan(0, 0, List.of("rectangle"));
        for (long seed = 1; seed < 8; seed++) assertEquals(first.split(" shed ")[0], plan(seed, 0, List.of("rectangle")).split(" shed ")[0]);
    }

    @Test
    void varietyKeepsTheHouseNearTheGateAndBigEnough() {
        Planner best = new Planner(plot(40, 34), 2, new Random(0));
        boolean[] reach = SegmentFiller.reachable(List.of(3, 4, 5), 80);
        int top = best.house(List.of("rectangle"), reach, List.of(3), 400, new double[]{20, 33}).fp().area();
        for (long seed = 0; seed < 20; seed++) {
            Planner pl = new Planner(plot(40, 34), 2, new Random(seed));
            pl.variety = 0.3;
            Planner.House h = pl.house(List.of("rectangle"), reach, List.of(3), 400, new double[]{20, 33});
            assertTrue(h.fp().area() >= 0.7 * top - 1e-9, "seed " + seed + ": " + h.fp().area() + " of " + top);
            Footprint.Rect b = h.fp().bounds();
            double cz = (b.z0() + b.z1()) / 2.0;
            assertTrue(cz >= 33 - (b.depth() / 2.0) - 2 - 0.3 * 33 - 0.5, "seed " + seed + " too far from the gate: " + b);
            assertEquals(Dir.SOUTH, h.frontDir(), "seed " + seed + " should face the gate");
        }
    }
}
