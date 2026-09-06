package fun.wonderful.mixin;

import fun.wonderful.client.modules.impl.render.CustomHands;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Custom Hands: подменяет вершинный потребитель руки на прокси с
 * космической анимированной заливкой (рука + предмет в руке).
 * <p>
 * Точка: renderFirstPersonItem — здесь параметр объявлен как
 * VertexConsumerProvider (не Immediate), проверено на 1.21.4.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

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
