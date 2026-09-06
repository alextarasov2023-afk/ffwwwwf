package fun.wonderful.api.storages.implement;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.utils.render.sky.CosmicSkyRenderer;
import fun.wonderful.client.modules.impl.render.ShaderSky;

/**
 * Шейдер-небо через собственный рендер-конвейер клиента
 * (EventRender.Default — то же событие, что рисует HUD и уведомления).
 * <p>
 * Квад с GLSL-шейдером космоса рисуется ПЕРВЫМ слушателем: тест глубины
 * LEQUAL + z~1 пропускает его только на пикселях неба (depth==1),
 * поэтому мир и HUD остаются поверх. Сторедж зарегистрирован
 * безусловно — не зависит от регистрации листенеров модулей.
 */
public class SkyRenderStorage implements QClient {

    private static boolean announced = false;

    public SkyRenderStorage() {
        EventInvoker.register(this);
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) return;
        if (!ShaderSky.isCosmic()) return;

        if (!announced) {
            announced = true;
            System.out.println("[ShaderSky] rendering via EventRender.Default (own pipeline)");
        }
        ShaderSky.renderCosmic();
    }
}
