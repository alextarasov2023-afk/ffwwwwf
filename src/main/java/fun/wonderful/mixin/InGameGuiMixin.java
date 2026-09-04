package fun.wonderful.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import fun.wonderful.Wonderful;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.utils.render.blur.BlurProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameGuiMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void wonderful$onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Wonderful.INSTANCE != null && Wonderful.isReady()) {
            // Обновляем kawase-блюр ДО отрисовки HUD: drawBlur-потребители (GUI,
            // Nametags, Watermark, Target HUD) требуют готовые буферы BlurProgram.
            // Без этого вызова blur-проходы никогда не выполнялись и блюр молча не работал.
            BlurProgram.getInstance().beginFrame();
            new EventRender.Default(context, tickCounter.getTickDelta(false)).call();
        }
    }
}
