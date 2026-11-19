package cz.lollywood.townbuilder.geom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** /plot save's orientation rules: the facade from where the builder stands, a corner's front_facing in any turn. */
class OrientTest {
    @Test
    void wallFacesTheBuilder() {
        // a 3-long wall along x, 1 deep: the builder south of it -> the facade looks SOUTH, length 3
        assertEquals(Dir.SOUTH, Orient.wallFacing(3, 1, 0.2, 4.0));
        assertEquals(Dir.NORTH, Orient.wallFacing(3, 1, -1.0, -3.0));
        assertEquals(3, Orient.length(3, 1, Dir.SOUTH));
        // along z: east or west
        assertEquals(Dir.WEST, Orient.wallFacing(1, 2, -5.0, 0.0));
        assertEquals(2, Orient.length(1, 2, Dir.WEST));
        // off the end of a long wall: cannot tell
        assertNull(Orient.wallFacing(3, 1, 5.0, 0.1));
        // a 1x1 piece: the nearer side
        assertEquals(Dir.EAST, Orient.wallFacing(1, 1, 3.0, 1.0));
    }

    @Test
    void cornersMatchTheReadmeConventionInEveryTurn() {
        // outer: north-west -> NORTH (the README's saved form), north-east -> EAST, south-east -> SOUTH, south-west -> WEST
        assertEquals(Dir.NORTH, front(true, -2, -2));
        assertEquals(Dir.EAST, front(true, 2, -2));
        assertEquals(Dir.SOUTH, front(true, 2, 2));
        assertEquals(Dir.WEST, front(true, -2, 2));
        // inner: the outside to the south-east -> EAST (the README's saved form), and turned on
        assertEquals(Dir.EAST, front(false, 2, 2));
        assertEquals(Dir.SOUTH, front(false, -2, 2));
        assertEquals(Dir.WEST, front(false, -2, -2));
        assertEquals(Dir.NORTH, front(false, 2, -2));
        // not diagonal: cannot tell
        assertNull(Orient.outside(0.2, 3));
    }

    @Test
    void savedCornersLandOnTheBuildsOwnFaces() {
        // every corner of a real L and T ring (the build's own walk): its two outside sides -> cornerFront must be its face()
        java.util.List<Footprint> fps = java.util.List.of(
                new Footprint(java.util.List.of(new Footprint.Rect(0, 0, 9, 4), new Footprint.Rect(0, 5, 4, 9))),
                new Footprint(java.util.List.of(new Footprint.Rect(0, 0, 14, 4), new Footprint.Rect(5, 5, 9, 9))));
        int seen = 0;
        for (Footprint fp : fps)
            for (Footprint.Corner c : fp.ring().corners()) {
                // clockwise walk: an outer corner's outside sides are the arriving direction and the reverse of the leaving one,
                // an inner corner's the leaving direction and the reverse of the arriving one
                Dir a = c.outer() ? c.in() : c.out(), b = c.outer() ? c.out().opposite() : c.in().opposite();
                assertEquals(c.face(), Orient.cornerFront(c.outer(), a, b), "corner at " + c.x() + "," + c.z());
                seen++;
            }
        assertTrue(seen >= 14);
    }

    private static Dir front(boolean outer, double dx, double dz) {
        Dir[] o = Orient.outside(dx, dz);
        return Orient.cornerFront(outer, o[0], o[1]);
    }
}
