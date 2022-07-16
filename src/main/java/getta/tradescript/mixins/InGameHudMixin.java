package getta.tradescript.mixins;

import getta.tradescript.TradeScript;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Shadow private int scaledWidth;

    @Shadow private int scaledHeight;

    @Shadow public abstract TextRenderer getTextRenderer();

    @Inject(method = "render", at = @At("HEAD"))
    private void lucianoComePingas(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if(TradeScript.on) {
            int x = scaledWidth / 2 - 230;
            int y = scaledHeight / 2 - 20;
            getTextRenderer().draw(matrices, new LiteralText("YOU HAVE THE SCRIPT ON, DONT GO BED NERD").setStyle(Style.EMPTY.withFont(TradeScript.FONT)), x, y, Color.PINK.getRGB());
        }
    }
}
