package cz.lollywood.townbuilder.geom;

/**
 * How /plot save reads a module's orientation from where the builder stands (pure, for the tests).
 * The builder stands in FRONT of a wall's facade, and diagonally OUTSIDE a corner.
 */
public final class Orient {
    private Orient() {}

    /**
     * The facade direction of a wall box {@code sx} x {@code sz} with the builder at ({@code dx}, {@code dz}) from the box's
     * centre: perpendicular to the long side, toward the builder; on a square box the side the builder is nearest to.
     * Null when the builder stands off the end of a long wall (then the facade cannot be told: give facing=).
     */
    public static Dir wallFacing(int sx, int sz, double dx, double dz) {
        if (sx > sz) return Math.abs(dz) < 0.5 ? null : (dz > 0 ? Dir.SOUTH : Dir.NORTH);
        if (sz > sx) return Math.abs(dx) < 0.5 ? null : (dx > 0 ? Dir.EAST : Dir.WEST);
        if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5) return null;
        return Dir.nearest(dx, dz);
    }

    /** The wall's length (blocks along the facade) for a box sx x sz facing {@code f}. */
    public static int length(int sx, int sz, Dir f) {
        return (f == Dir.NORTH || f == Dir.SOUTH) ? sx : sz;
    }

    /** The two outside directions of a corner with the builder at (dx, dz) from it; null unless the builder is diagonal. */
    public static Dir[] outside(double dx, double dz) {
        if (Math.abs(dx) < 0.5 || Math.abs(dz) < 0.5) return null;
        return new Dir[]{dz > 0 ? Dir.SOUTH : Dir.NORTH, dx > 0 ? Dir.EAST : Dir.WEST};
    }

    /**
     * The {@code front_facing} a corner must be saved with so the build turns it right, from its two outside directions.
     * The build walks the house clockwise and turns a corner's {@code front_facing} onto the face of the wall that starts at
     * it: for an OUTER corner that is the clockwise one of the two (north-west → NORTH, north-east → EAST), for an INNER
     * corner the counter-clockwise one (outside to the south-east → EAST), which is the README's convention in every turn.
     */
    public static Dir cornerFront(boolean outer, Dir a, Dir b) {
        if (a.cw() == b) return outer ? b : a;
        if (b.cw() == a) return outer ? a : b;
        throw new IllegalArgumentException("a corner's two outside directions must be perpendicular: " + a + ", " + b);
    }
}
