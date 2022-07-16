package getta.tradescript.mixins;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantScreen.class)
public interface IMixinMerchantScreen
{
    @Accessor("selectedIndex")
    int getSelectedMerchantRecipe();
}
