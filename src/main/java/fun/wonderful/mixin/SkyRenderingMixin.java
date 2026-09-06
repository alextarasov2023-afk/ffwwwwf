package fun.wonderful.mixin;

import fun.wonderful.Wonderful;
import fun.wonderful.client.modules.impl.render.ShaderSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.SkyRendering;

/**
 * Shader Sky (путь 2 из 2): подменяет ванильный купол на космический шейдер
 * ещё ДО рельефа — квад без теста глубины, рельеф рисуется позже и
 * перекрывает его. Дублирует прямой вызов из WorldRendererMixin
 * (надёжность: картинки одинаковые, двойная отрисовка безвредна).
 */
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {

    private static boolean wonderful$logged = false;

    @Inject(method = "renderSky(FFF)V", at = @At("HEAD"), cancellable = true)
    private void wonderful$cosmicSky(float red, float green, float blue, CallbackInfo ci) {
        if (Wonderful.INSTANCE == null || !Wonderful.isReady()) return;
        if (!ShaderSky.isCosmic()) return;

        if (!wonderful$logged) {
            wonderful$logged = true;
            System.out.println("[ShaderSky] renderSky hook fired — cosmic shader drawn");
        }
        ShaderSky.renderCosmic();
        ci.cancel();
    }
}
