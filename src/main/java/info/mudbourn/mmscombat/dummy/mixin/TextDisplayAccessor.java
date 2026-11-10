package info.mudbourn.mmscombat.dummy.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Exposes the text display's private background setter so dummy readouts can drop their backing quad.
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {

    @Invoker("setBackgroundColor")
    void mmsCombat$setBackgroundColor(int color);
}
