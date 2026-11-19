package cz.lollywood.townbuilder.geom;

/** The four horizontal directions; Minecraft's north is -Z, east +X. */
public enum Dir {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    public final int dx, dz;

    Dir(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Dir cw() { return values()[(ordinal() + 1) % 4]; }
    public Dir ccw() { return values()[(ordinal() + 3) % 4]; }
    public Dir opposite() { return values()[(ordinal() + 2) % 4]; }

    /** Quarter turns clockwise (seen from above) that bring {@code this} onto {@code target}: 0-3. */
    public int quarterTurnsTo(Dir target) {
        return (target.ordinal() - ordinal() + 4) % 4;
    }

    public static Dir of(int dx, int dz) {
        for (Dir d : values()) if (d.dx == dx && d.dz == dz) return d;
        throw new IllegalArgumentException(dx + "," + dz);
    }

    /** The axis direction closest to a vector (ties go to the x axis). */
    public static Dir nearest(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? EAST : WEST;
        return dz >= 0 ? SOUTH : NORTH;
    }

    public static Dir parse(String s) {
        return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
