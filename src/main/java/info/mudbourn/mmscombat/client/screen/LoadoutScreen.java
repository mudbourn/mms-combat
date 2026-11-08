package info.mudbourn.mmscombat.client.screen;

import info.mudbourn.mmscombat.combatlog.FairPlayEnchants;
import info.mudbourn.mmscombat.net.LoadoutChoicePayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

// Picker for which enchant levels each over-budget item gives up if its owner combat logs, one item per page.
public final class LoadoutScreen extends Screen {

    private static final int ROW = 22;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;

    private final List<ItemStack> originals;
    private final List<ItemEnchantments.Mutable> picks = new ArrayList<>();
    private int page;
    private Button confirm;

    public LoadoutScreen(List<ItemStack> originals) {
        super(Component.literal("Combat Log Fair Use"));
        this.originals = originals;
        resetPicks();
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = height / 2 - 70;
        List<Holder<Enchantment>> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            Holder<Enchantment> enchantment = rows.get(i);
            int y = top + 30 + i * ROW;
            addRenderableWidget(Button.builder(Component.literal("-"), button -> step(enchantment, -1))
                .bounds(center + 60, y, 20, 20)
                .build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> step(enchantment, 1))
                .bounds(center + 84, y, 20, 20)
                .build());
        }
        int bottom = top + 40 + Math.max(rows.size(), 3) * ROW;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
            .bounds(center - 110, bottom, 20, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
            .bounds(center + 90, bottom, 20, 20)
            .build());
        confirm = addRenderableWidget(Button.builder(Component.literal("Confirm"), button -> send())
            .bounds(center - 80, bottom, 75, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Auto"), button -> sendAuto())
            .bounds(center + 5, bottom, 75, 20)
            .build());
        refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int top = height / 2 - 70;
        ItemStack original = originals.get(page);
        ItemEnchantments chosen = picks.get(page).toImmutable();
        graphics.drawCenteredString(font, title, center, top - 24, WHITE);
        graphics.drawCenteredString(font,
            Component.literal("Pick what your logout body loses. Auto drops levels for you."),
            center, top - 12, GRAY);
        graphics.renderItem(original, center - 110, top);
        graphics.drawString(font, original.getHoverName(), center - 88, top + 4, WHITE);
        graphics.drawString(font, (page + 1) + "/" + originals.size(), center + 84, top + 4, GRAY);
        int cap = FairPlayEnchants.budget(original);
        String budget = cap == FairPlayEnchants.UNCAPPED ? "no cap" : FairPlayEnchants.budgetedLevels(original, chosen) + "/" + cap + " EL";
        graphics.drawString(font, budget, center - 88, top + 16, GRAY);
        List<Holder<Enchantment>> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            Holder<Enchantment> enchantment = rows.get(i);
            int y = top + 36 + i * ROW;
            int level = chosen.getLevel(enchantment);
            String tag = FairPlayEnchants.isStatus(enchantment) ? " [status]"
                : FairPlayEnchants.isDamage(enchantment) ? " [damage]"
                : FairPlayEnchants.isUpkeep(enchantment) ? " [free]" : "";
            Component name = level > 0
                ? Enchantment.getFullname(enchantment, level)
                : Component.literal("off: ").append(enchantment.value().description());
            graphics.drawString(font, name.getString() + tag, center - 110, y, level > 0 ? WHITE : GRAY);
        }
        boolean legal = FairPlayEnchants.fits(original, chosen);
        int bottom = top + 40 + Math.max(rows.size(), 3) * ROW;
        graphics.drawCenteredString(font,
            legal ? "Legal" : "Over budget, or status levels not paid with damage levels",
            center, bottom - 12, legal ? GREEN : RED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<Holder<Enchantment>> rows() {
        List<Holder<Enchantment>> rows = new ArrayList<>();
        for (Holder<Enchantment> enchantment : originals.get(page).getEnchantments().keySet()) {
            if (!enchantment.is(EnchantmentTags.CURSE)) {
                rows.add(enchantment);
            }
        }
        return rows;
    }

    private void step(Holder<Enchantment> enchantment, int delta) {
        ItemEnchantments.Mutable pick = picks.get(page);
        int max = originals.get(page).getEnchantments().getLevel(enchantment);
        pick.set(enchantment, Math.clamp(pick.getLevel(enchantment) + delta, 0, max));
        refresh();
    }

    private void turn(int delta) {
        page = Math.floorMod(page + delta, originals.size());
        rebuildWidgets();
    }

    private void refresh() {
        boolean legal = true;
        for (int i = 0; i < originals.size(); i++) {
            legal &= FairPlayEnchants.fits(originals.get(i), picks.get(i).toImmutable());
        }
        confirm.active = legal;
    }

    private void resetPicks() {
        picks.clear();
        for (ItemStack original : originals) {
            ItemStack nerfed = original.copy();
            FairPlayEnchants.nerf(nerfed);
            picks.add(new ItemEnchantments.Mutable(nerfed.getEnchantments()));
        }
    }

    private void sendAuto() {
        resetPicks();
        send();
    }

    private void send() {
        List<ItemEnchantments> choices = new ArrayList<>();
        for (ItemEnchantments.Mutable pick : picks) {
            choices.add(pick.toImmutable());
        }
        ClientPlayNetworking.send(new LoadoutChoicePayload(choices));
        onClose();
    }
}
