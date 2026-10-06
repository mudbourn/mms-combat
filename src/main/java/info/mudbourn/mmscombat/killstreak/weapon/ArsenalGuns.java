package info.mudbourn.mmscombat.killstreak.weapon;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// mms-arsenal gun rewards, referenced by item id only: each gun with a full magazine and spare ammo.
final class ArsenalGuns {

    static final String NAMESPACE = "mms_arsenal";
    private static final String AMMO_COMPONENT = "mms_arsenal:ammo_count";
    private static final int SPARE_MAGAZINES = 3;

    // One gun's magazine size, the ammo it fires and how many rounds of it come spare.
    private record Loadout(int magazine, String ammo, int spare) {

        static Loadout magazines(int magazine, String ammo) {
            return new Loadout(magazine, ammo, magazine * SPARE_MAGAZINES);
        }
    }

    // Guns that burn ammo too fast for three magazines, and the revolver, carry full stacks instead.
    private static final Map<String, Loadout> LOADOUTS = Map.of(
        "mms_arsenal:revolver", new Loadout(8, "mms_arsenal:pistol_ammo", 64),
        "mms_arsenal:assault_rifle", Loadout.magazines(30, "mms_arsenal:rifle_ammo"),
        "mms_arsenal:bolt_action_rifle", Loadout.magazines(4, "mms_arsenal:rifle_ammo"),
        "mms_arsenal:light_machine_gun", new Loadout(100, "mms_arsenal:rifle_ammo", 4 * 64),
        "mms_arsenal:minigun", new Loadout(1, "mms_arsenal:pistol_ammo", 4 * 64),
        "mms_arsenal:rocket_launcher", Loadout.magazines(1, "mms_arsenal:rocket"),
        "mms_arsenal:blossom_rifle", Loadout.magazines(30, "mms_arsenal:spectre_round"),
        "mms_arsenal:soulhunter_mk2", Loadout.magazines(30, "mms_arsenal:blaze_round"),
        "mms_arsenal:hypersonic_cannon", Loadout.magazines(1, "minecraft:sculk_catalyst"));

    private ArsenalGuns() {
    }

    static List<ItemStack> build(String gunId, ServerPlayer owner) {
        Loadout loadout = LOADOUTS.get(gunId);
        ItemStack gun = loadout == null ? ItemStack.EMPTY : KillstreakWeapons.stack(gunId, 1, KillstreakWeapons.STREAK_ITEM_GUN, owner);
        if (gun.isEmpty()) {
            MmsCombat.LOG.warn("mms-arsenal gun {} has no loadout or is not registered; skipping", gunId);
            return List.of();
        }
        KillstreakWeapons.setIntComponent(gun, AMMO_COMPONENT, loadout.magazine());
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(gun);
        ItemStack sample = KillstreakWeapons.stack(loadout.ammo(), 1, KillstreakWeapons.STREAK_ITEM_GUN, owner);
        if (sample.isEmpty()) {
            return stacks;
        }
        int perStack = sample.getMaxStackSize();
        for (int left = loadout.spare(); left > 0; left -= perStack) {
            stacks.add(sample.copyWithCount(Math.min(perStack, left)));
        }
        return stacks;
    }
}
