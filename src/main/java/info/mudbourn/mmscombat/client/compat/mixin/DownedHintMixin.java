package info.mudbourn.mmscombat.client.compat.mixin;

import goetic.mods.absolutrevive.client.StatusEffectLayer;
import info.mudbourn.mmscombat.client.compat.SelfReviveHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Drops the "be searched" part of the downed overlay hints while keepInventory is on.
@Mixin(value = StatusEffectLayer.class, remap = false)
public abstract class DownedHintMixin {

    @ModifyConstant(method = "onHudRender", constant = @Constant(stringValue = "absolutrevive.gui.waiting_for_rescue"))
    private String mmsCombat$waitingHint(String key) {
        return SelfReviveHud.keepInventory() ? "mms_combat.revive.waiting_for_rescue" : key;
    }

    @ModifyConstant(method = "onHudRender", constant = @Constant(stringValue = "absolutrevive.gui.rescue_help"))
    private String mmsCombat$rescueHelp(String key) {
        return SelfReviveHud.keepInventory() ? "mms_combat.revive.rescue_help" : key;
    }
}
