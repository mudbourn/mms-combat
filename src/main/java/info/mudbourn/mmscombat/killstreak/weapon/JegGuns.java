package info.mudbourn.mmscombat.killstreak.weapon;

import info.mudbourn.mmscombat.MmsCombat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ttv.migami.jeg.gun.GunDefinitions;
import ttv.migami.jeg.gun.GunStats;

// JEG gun rewards, only loaded once JEG is known to be installed.
final class JegGuns {

    private static final int SPARE_MAGAZINES = 3;

    // Guns that burn ammo too fast for three magazines, mapped to the full ammo stacks they come with instead.
    private static final Map<String, Integer> SPARE_STACKS = Map.of(
        "jeg:light_machine_gun", 4,
        "jeg:minigun", 4);

    private JegGuns() {
    }

    static List<Identifier> ids() {
        return GunDefinitions.IDS.stream()
            .filter(id -> !id.getPath().equals("abstract_gun"))
            .sorted()
            .toList();
    }

    static List<ItemStack> build(String gunId, ServerPlayer owner) {
        Identifier id = Identifier.tryParse(gunId);
        GunStats stats = id == null ? null : GunDefinitions.ALL.get(id);
        ItemStack gun = stats == null ? ItemStack.EMPTY : KillstreakWeapons.stack(gunId, 1, KillstreakWeapons.STREAK_ITEM_GUN, owner);
        if (gun.isEmpty()) {
            MmsCombat.LOG.warn("JEG gun {} is not defined; skipping", gunId);
            return List.of();
        }
        KillstreakWeapons.setIntComponent(gun, "jeg:gun_ammo", stats.magazineSize());
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(gun);
        Item ammo = BuiltInRegistries.ITEM.getOptional(stats.ammoItem()).orElse(null);
        if (ammo == null || stats.ammoItem().getPath().equals("air")) {
            return stacks;
        }
        int perStack = new ItemStack(ammo).getMaxStackSize();
        int spare = SPARE_STACKS.containsKey(gunId)
            ? SPARE_STACKS.get(gunId) * perStack
            : Math.max(1, stats.magazineSize()) * SPARE_MAGAZINES;
        for (int left = spare; left > 0; left -= perStack) {
            stacks.add(KillstreakWeapons.stack(stats.ammoItem().toString(), Math.min(perStack, left), KillstreakWeapons.STREAK_ITEM_GUN, owner));
        }
        return stacks;
    }
}
