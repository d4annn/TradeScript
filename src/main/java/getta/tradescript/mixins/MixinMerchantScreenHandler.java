package getta.tradescript.mixins;

import getta.tradescript.IMerchantScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOfferList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MerchantScreenHandler.class)
public abstract class MixinMerchantScreenHandler extends ScreenHandler implements IMerchantScreenHandler {

    @Shadow
    @Final
    private Merchant merchant;


    protected MixinMerchantScreenHandler(@Nullable ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }



    @Override
    public TradeOfferList getOriginalList()
    {
        return this.merchant.getOffers();
    }
}
