package info.mudbourn.mmscombat.zone;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

// One combat zone: a named region in a single dimension plus the flags that govern what happens inside it. The footprint is an axis-aligned box, or an extruded polygon when edge points are set.
public final class Zone {

    public String name;
    public String dimension;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public List<int[]> polygon = new ArrayList<>();
    public boolean flagCombatOnEnter = true;
    public boolean suppressNaturalSpawns = true;
    public boolean blockExplosionShield = false;
    private transient ResourceKey<Level> dimensionKey;
    private transient boolean dimensionResolved;

    public Zone() {
    }

    public Zone(String name, String dimension, BlockPos a, BlockPos b) {
        this.name = name;
        this.dimension = dimension;
        this.minX = Math.min(a.getX(), b.getX());
        this.minY = Math.min(a.getY(), b.getY());
        this.minZ = Math.min(a.getZ(), b.getZ());
        this.maxX = Math.max(a.getX(), b.getX());
        this.maxY = Math.max(a.getY(), b.getY());
        this.maxZ = Math.max(a.getZ(), b.getZ());
    }

    public Zone(String name, String dimension, int minY, int maxY) {
        this.name = name;
        this.dimension = dimension;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
    }

    // Whether edge points define this zone's footprint instead of the box.
    public boolean isPolygon() {
        return polygon != null && polygon.size() >= 3;
    }

    // Appends an edge point and refreshes the box that bounds the polygon.
    public void addPoint(int x, int z) {
        polygon.add(new int[] {x, z});
        refreshBounds();
    }

    // Drops the last edge point and refreshes the bounding box.
    public boolean removeLastPoint() {
        if (polygon.isEmpty()) {
            return false;
        }
        polygon.remove(polygon.size() - 1);
        refreshBounds();
        return true;
    }

    // Recomputes the XZ box that bounds the polygon, leaving the Y range untouched.
    private void refreshBounds() {
        if (polygon.isEmpty()) {
            return;
        }
        int lowX = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (int[] point : polygon) {
            lowX = Math.min(lowX, point[0]);
            lowZ = Math.min(lowZ, point[1]);
            highX = Math.max(highX, point[0]);
            highZ = Math.max(highZ, point[1]);
        }
        this.minX = lowX;
        this.minZ = lowZ;
        this.maxX = highX;
        this.maxZ = highZ;
    }

    // Whether this zone lies in the given dimension, parsing the saved id only once.
    public boolean inDimension(ResourceKey<Level> dim) {
        if (!dimensionResolved) {
            Identifier id = dimension == null ? null : Identifier.tryParse(dimension);
            dimensionKey = id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
            dimensionResolved = true;
        }
        return dim.equals(dimensionKey);
    }

    public boolean contains(ResourceKey<Level> dim, int x, int y, int z) {
        return y >= minY && shieldsColumn(dim, x, y, z);
    }

    // Whether this position sits within the zone footprint at or below its ceiling, so the floor and terrain beneath the zone are shielded too.
    public boolean shieldsColumn(ResourceKey<Level> dim, int x, int y, int z) {
        if (y > maxY || x < minX || x > maxX || z < minZ || z > maxZ || !inDimension(dim)) {
            return false;
        }
        return !isPolygon() || containsColumn(x, z);
    }

    // Ray-casts the block centre against the polygon edges in the XZ plane.
    private boolean containsColumn(int x, int z) {
        double px = x + 0.5;
        double pz = z + 0.5;
        boolean inside = false;
        int count = polygon.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            double xi = polygon.get(i)[0];
            double zi = polygon.get(i)[1];
            double xj = polygon.get(j)[0];
            double zj = polygon.get(j)[1];
            boolean crosses = (zi > pz) != (zj > pz)
                && px < (xj - xi) * (pz - zi) / (zj - zi) + xi;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    // Applies a named boolean flag, returning false when the name is unknown.
    public boolean setFlag(String flag, boolean value) {
        switch (flag) {
            case "flagCombatOnEnter" -> flagCombatOnEnter = value;
            case "suppressNaturalSpawns" -> suppressNaturalSpawns = value;
            case "blockExplosionShield" -> blockExplosionShield = value;
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        String shape = isPolygon()
            ? polygon.size() + "-point polygon, y " + minY + " -> " + maxY
            : minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ;
        return name + " [" + dimension + "] " + shape
            + " (combat=" + flagCombatOnEnter + " noSpawn=" + suppressNaturalSpawns
            + " blastShield=" + blockExplosionShield + ")";
    }
}
