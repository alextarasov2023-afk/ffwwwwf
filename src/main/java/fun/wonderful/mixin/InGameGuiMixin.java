package fun.wonderful.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import fun.wonderful.Wonderful;
import fun.wonderful.api.events.implement.EventRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameGuiMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void wonderful$onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Wonderful.INSTANCE != null && Wonderful.isReady()) {
            new EventRender.Default(context, tickCounter.getTickDelta(false)).call();
        }
    }
}
