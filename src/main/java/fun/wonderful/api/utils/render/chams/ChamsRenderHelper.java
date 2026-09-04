package fun.wonderful.api.utils.render.chams;

import fun.wonderful.api.QClient;
import fun.wonderful.client.modules.impl.TestModules;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;

/**
 * Помощник Chams-модуля: решает, является ли сущность целью (по настройкам
 * модуля: категория, радиус, не сам игрок, не невидимка) и если да — оборачивает
 * вершинный потребитель в перекрашивающий прокси.
 * <p>
 * Вызывается из миксина {@code EntityRenderDispatcherMixin}.
 */
public final class ChamsRenderHelper implements QClient {

    private ChamsRenderHelper() {
    }

    public static VertexConsumerProvider wrap(VertexConsumerProvider vcp, Entity entity) {
        if (!TestModules.Chams.enabled) {
            return vcp;
        }
        if (mc.player == null || mc.world == null) {
            return vcp;
        }
        if (!TestModules.Chams.isTarget(entity)) {
            return vcp;
        }
        return new ChamsVertexConsumerProvider(vcp,
                TestModules.Chams.red, TestModules.Chams.green, TestModules.Chams.blue,
                TestModules.Chams.opacity);
    }
}
