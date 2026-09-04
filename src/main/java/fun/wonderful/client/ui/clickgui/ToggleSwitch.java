package fun.wonderful.client.ui.clickgui;

import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.math.HoveringUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Единый iOS-style переключатель: трек + скользящий кружок внутри.
 * Без акцентной «закраски» — выключен серый, включён белый.
 */
final class ToggleSwitch {

    static final float W = 19f;
    static final float H = 11f;
    private static final float PAD = 1.2f;

    private ToggleSwitch() {}

    static void draw(MatrixStack ms, float x, float y, float progress, float alpha, int accent) {
        float p = MathHelper.clamp(progress, 0f, 1f);
        float a = MathHelper.clamp(alpha, 0f, 1f);
        if (a < 0.01f) return;

        int offTrack = ColorUtils.rgba(46, 52, 67, (int) (245 * a));
        int onTrack  = ColorUtils.interpolateColor(
                ColorUtils.rgba(195, 200, 212, (int) (245 * a)),
                accent,
                0.55f);
        int track = ColorUtils.interpolateColor(offTrack, onTrack, p);

        // Тень + основной трек
        RenderUtils.drawRoundedRect(ms, x, y + 0.4f, W, H, H / 2f,
                ColorUtils.rgba(0, 0, 0, (int) (42 * a)));
        RenderUtils.drawRoundedRect(ms, x, y, W, H, H / 2f, track);

        RenderUtils.drawRoundedRectOutline(ms, x, y, W, H, H / 2f, 1f,
                ColorUtils.rgba(255, 255, 255, (int) ((14 + 26 * p) * a)),
                ColorUtils.rgba(255, 255, 255, (int) ((14 + 26 * p) * a)),
                ColorUtils.rgba(0, 0, 0, (int) (40 * a)),
                ColorUtils.rgba(0, 0, 0, (int) (40 * a)));

        // Ползунок: чистый круг с мягкой тенью
        float knobD = H - PAD * 2f;
        float knobR = knobD / 2f;
        float knobMin = x + PAD + knobR;
        float knobMax = x + W - PAD - knobR;
        float knobCx = knobMin + (knobMax - knobMin) * p;
        float knobCy = y + H / 2f;

        RenderUtils.drawRoundCircle(ms, knobCx, knobCy + 0.8f, knobD + 0.5f,
                ColorUtils.rgba(0, 0, 0, (int) (48 * a)));
        RenderUtils.drawRoundCircle(ms, knobCx, knobCy, knobD,
                ColorUtils.rgba(250, 251, 253, (int) (255 * a)));
    }

    static boolean hit(float mx, float my, float x, float y) {
        return HoveringUtils.isHovered(mx, my, x, y, W, H);
    }
}
