package info.mudbourn.mmscombat.zone;

import net.minecraft.core.BlockPos;

// One combat zone: a named box in a single dimension plus the flags that govern what happens inside it.
public final class Zone {

    public String name;
    public String dimension;
    public int minX;
    public int minY;
    public int minZ;
    public int maxX;
    public int maxY;
    public int maxZ;
    public boolean flagCombatOnEnter = true;
    public boolean suppressNaturalSpawns = true;
    public boolean blockExplosionShield = false;

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

    public boolean contains(String dim, int x, int y, int z) {
        return dimension.equals(dim)
            && x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
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
        return name + " [" + dimension + "] "
            + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ
            + " (combat=" + flagCombatOnEnter + " noSpawn=" + suppressNaturalSpawns
            + " blastShield=" + blockExplosionShield + ")";
    }
}
