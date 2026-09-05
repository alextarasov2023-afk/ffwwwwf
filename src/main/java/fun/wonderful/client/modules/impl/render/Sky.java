package fun.wonderful.client.modules.impl.render;

import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.util.math.MathHelper;

/**
 * Sky — shader-небо: цвет неба и тумана плавно уходит в выбранный режим.
 * Акцент — цвет клиента; Радуга — медленный перелив оттенков;
 * Закат — живой градиент оранжевый↔фиолетовый; Ночь — глубокая синь.
 */
public class Sky extends Module {

    public static Sky INSTANCE = new Sky();

    public final ModeSetting mode = new ModeSetting("Режим", "Акцент", "Акцент", "Радуга", "Закат", "Ночь");
    public final FloatSetting intensity = new FloatSetting("Интенсивность", 55f, 0f, 100f, 5f);

    public Sky() {
        super("Sky", "Подмена цвета неба и тумана", ModuleCategory.RENDER);
        addSettings(mode, intensity);
    }

    /** Смешать ванильный ARGB-цвет неба с целевым (вызывается из миксина). */
    public static int mixSkyColor(int vanilla) {
        if (INSTANCE == null || !INSTANCE.isEnable()) return vanilla;
        float t = MathHelper.clamp(INSTANCE.intensity.get() / 100f, 0f, 1f);
        if (t <= 0.001f) return vanilla;

        int target = targetColor();
        return ColorUtils.interpolateColor(vanilla, target, t);
    }

    private static int targetColor() {
        return switch (INSTANCE.mode.getCurrent()) {
            case "Радуга" -> {
                float hue = (System.currentTimeMillis() % 12000L) / 12000f;
                yield MathHelper.hsvToRgb(hue, 0.55f, 1.0f);
            }
            case "Закат" -> {
                float w = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 2400.0);
                yield ColorUtils.interpolateColor(0xFFFF7832, 0xFF964BC8, w);
            }
            case "Ночь" -> ColorUtils.rgba(8, 12, 32, 255);
            default -> ThemePanel.accentSolid();
        };
    }
}
