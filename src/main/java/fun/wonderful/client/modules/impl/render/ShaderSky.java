package fun.wonderful.client.modules.impl.render;

import java.awt.Color;

import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.sky.CosmicSkyRenderer;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.FloatSetting;

/**
 * Shader Sky — космическое небо: живая переливающаяся туманность
 * (индиго/фиолетовый/маджента/бирюза) с мерцающими звёздами и волнами
 * акцентного цвета темы. Рисуется вместо ванильного купола неба.
 */
public class ShaderSky extends Module {

    public static ShaderSky INSTANCE = new ShaderSky();

    public final FloatSetting speed = new FloatSetting("Скорость", 1.0f, 0.25f, 3.0f, 0.05f);
    public final FloatSetting intensity = new FloatSetting("Яркость", 60f, 0f, 100f, 1f);

    public ShaderSky() {
        super("Shader Sky", "Космическое анимированное небо-шейдер", ModuleCategory.RENDER);
        addSettings(speed, intensity);
    }

    public static boolean isCosmic() {
        return INSTANCE != null && INSTANCE.isEnable();
    }

    /** Рисует шейдер-небо (вызов из SkyRenderingMixin). */
    public static void renderCosmic() {
        CosmicSkyRenderer.render(INSTANCE.speed.get(), INSTANCE.intensity.get() / 100f);
    }

    /**
     * Цвет неба/тумана в тон туманности: медленно плывущий космический оттенок,
     * чтобы туман горизонта не спорил с шейдером.
     */
    public static int skyColor(int vanilla) {
        long ms = System.currentTimeMillis();
        float hue = 0.66f + 0.09f * (float) Math.sin(ms / 34000.0);
        float bri = 0.30f + 0.10f * (float) Math.sin(ms / 21000.0 + 1.7);
        int cosmic = Color.HSBtoRGB(hue, 0.72f, bri);
        return ColorUtils.interpolateColor(vanilla, cosmic, 0.45f);
    }
}
