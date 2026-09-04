package fun.wonderful.api.utils.render.chams;

import fun.wonderful.api.QClient;
import fun.wonderful.client.modules.impl.TestModules;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

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

        int r = TestModules.Chams.red;
        int g = TestModules.Chams.green;
        int b = TestModules.Chams.blue;

        // Rainbow: цвет плывёт по кругу оттенков со временем
        if (TestModules.Chams.rainbow) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000f;
            int rgb = MathHelper.hsvToRgb(hue, 0.75f, 1.0f);
            r = (rgb >> 16) & 0xFF;
            g = (rgb >> 8) & 0xFF;
            b = rgb & 0xFF;
        }

        return new ChamsVertexConsumerProvider(vcp, r, g, b, TestModules.Chams.opacity);
    }
}
