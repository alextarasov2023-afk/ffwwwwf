package fun.wonderful.client.modules.impl.render;

import java.awt.Color;

import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.sky.CosmicSkyRenderer;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;

/**
 * Shader Sky — космическое небо: живая переливающаяся туманность
 * (индиго/фиолетовый/маджента/бирюза) с мерцающими звёздами и волнами
 * акцентного цвета темы. Рисуется вместо ванильного купола неба.
 */
public class ShaderSky extends Module {

    public static ShaderSky INSTANCE = new ShaderSky();

    public final ModeSetting view = new ModeSetting("Вид", "Туманность",
            "Туманность", "Аврора", "Звездопад", "Галактика");
    public final FloatSetting speed = new FloatSetting("Скорость", 1.0f, 0.25f, 3.0f, 0.05f);
    public final FloatSetting intensity = new FloatSetting("Яркость", 60f, 0f, 100f, 1f);

    /** Оттенок тумана горизонта для каждого вида. */
    private static final float[] FOG_HUE = {0.66f, 0.42f, 0.74f, 0.60f};
    private static final float[] FOG_BRI = {0.30f, 0.22f, 0.24f, 0.28f};

    public ShaderSky() {
        super("Shader Sky", "Космические небеса: туманность, аврора, звездопад, галактика", ModuleCategory.RENDER);
        addSettings(view, speed, intensity);
    }

    private static int viewIndex() {
        String c = INSTANCE.view.getCurrent();
        for (int i = 0; i < VIEW_NAMES.length; i++) {
            if (VIEW_NAMES[i].equals(c)) return i;
        }
        return 0;
    }

    private static final String[] VIEW_NAMES = {"Туманность", "Аврора", "Звездопад", "Галактика"};

    public static boolean isCosmic() {
        return INSTANCE != null && INSTANCE.isEnable();
    }

    /** Рисует шейдер-небо (вызов из SkyRenderingMixin). */
    public static void renderCosmic() {
        CosmicSkyRenderer.render(viewIndex(), INSTANCE.speed.get(), INSTANCE.intensity.get() / 100f);
    }

    /**
     * Цвет неба/тумана в тон туманности: медленно плывущий космический оттенок,
     * чтобы туман горизонта не спорил с шейдером.
     */
    public static int skyColor(int vanilla) {
        long ms = System.currentTimeMillis();
        int idx = viewIndex();
        float hue = FOG_HUE[idx] + 0.07f * (float) Math.sin(ms / 34000.0);
        float bri = FOG_BRI[idx] + 0.08f * (float) Math.sin(ms / 21000.0 + 1.7);
        int cosmic = Color.HSBtoRGB(hue, 0.72f, bri);
        return ColorUtils.interpolateColor(vanilla, cosmic, 0.45f);
    }
}
