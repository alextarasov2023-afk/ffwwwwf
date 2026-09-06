package fun.wonderful.mixin;

import fun.wonderful.client.modules.impl.render.CustomHands;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Custom Hands (2 точки обёртки для надёжности): подменяет вершинный
 * потребитель руки на прокси с космической анимированной заливкой.
 * wrap() идемпотентен — двойная обёртка исключена.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @ModifyVariable(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private VertexConsumerProvider wonderful$handsShaderOuter(VertexConsumerProvider provider) {
        return CustomHands.wrap(provider);
    }

    @ModifyVariable(
            method = "renderFirstPersonItem",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private VertexConsumerProvider wonderful$handsShaderInner(VertexConsumerProvider provider) {
        return CustomHands.wrap(provider);
    }
}
