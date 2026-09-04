package fun.wonderful.mixin;

import fun.wonderful.api.utils.render.chams.ChamsRenderHelper;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Перехватывает рендер сущностей и подменяет вершинный потребитель прокси
 * для Chams-модуля (перекрашивание тела живых целей).
 * <p>
 * В {@code EntityRenderDispatcher.render(E, …)} доступен сам объект сущности,
 * поэтому здесь надёжно определяется, является ли она целью.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @ModifyVariable(
            method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private VertexConsumerProvider wonderful$chamsVertexConsumer(
            VertexConsumerProvider vertexConsumerProvider,
            Entity entity) {
        return ChamsRenderHelper.wrap(vertexConsumerProvider, entity);
    }
}
