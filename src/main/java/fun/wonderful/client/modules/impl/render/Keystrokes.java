package fun.wonderful.client.modules.impl.render;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keystrokes — панель клавиш движения внизу по центру: WASD, пробел,
 * ЛКМ/ПКМ с живым счётчиком CPS. Стиль клик-гуи: блюр-панели, акцент
 * при нажатии, плавное затухание клавиши после отпускания.
 */
public class Keystrokes extends Module {

    public static Keystrokes INSTANCE = new Keystrokes();

    public final BooleanSetting showCps = new BooleanSetting("CPS", true);
    public final FloatSetting scale = new FloatSetting("Масштаб", 1f, 0.75f, 1.6f, 0.05f);

    private static final float KEY = 22f;
    private static final float GAP = 3f;

    private final Deque<Long> lmbClicks = new ArrayDeque<>();
    private final Deque<Long> rmbClicks = new ArrayDeque<>();
    private boolean lastLmb, lastRmb;

    private final AnimationUtils appear = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);

    public Keystrokes() {
        super("Keystrokes", "Панель клавиш WASD с CPS", ModuleCategory.RENDER);
        addSettings(showCps, scale);
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) return;
        MatrixStack ms = event.getContext().getMatrices();

        appear.update(isEnable() ? 1f : 0f);
        float ap = MathHelper.clamp(appear.getValue(), 0f, 1f);
        if (ap <= 0.02f) return;

        float sc = scale.get();
        float key = KEY * sc;
        float gap = GAP * sc;
        float ac = ThemePanel.accentSolid();

        // CPS: считаем передние фронты нажатий, окно 1 секунда
        long now = System.currentTimeMillis();
        boolean lmb = mc.options.attackKey.isPressed();
        boolean rmb = mc.options.useKey.isPressed();
        if (lmb && !lastLmb) lmbClicks.addLast(now);
        if (rmb && !lastRmb) rmbClicks.addLast(now);
        lastLmb = lmb;
        lastRmb = rmb;
        while (!lmbClicks.isEmpty() && now - lmbClicks.peekFirst() > 1000L) lmbClicks.pollFirst();
        while (!rmbClicks.isEmpty() && now - rmbClicks.peekFirst() > 1000L) rmbClicks.pollFirst();

        Font f12 = Fonts.getFont("suisse", 12);
        Font f9 = Fonts.getFont("suisse", 9);
        if (f12 == null || f9 == null) return;
        f12ref = f12;

        // Раскладка: ЛКМ ПКМ / W A S D / пробел — внизу по центру
        float panelW = key * 3 + gap * 2;
        float rowsH = showCps.isState() ? key : 0f;
        float panelH = rowsH + key * 2 + gap + (key * 0.45f) + gap * 2;
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float px = (int) (sw / 2f - panelW / 2f);
        float py = (int) (sh - panelH - 10f + (1f - ap) * 14f);

        // Клавиша: блюр-панель + заливка акцентом при нажатии
        float cy = py;
        if (rowsH > 0f) {
            drawKey(ms, px, cy, panelW / 2f - gap / 2f, key, lmb, lmbClicks.size() + " CPS", f9, ac, ap, sc);
            drawKey(ms, px + panelW / 2f + gap / 2f, cy, panelW / 2f - gap / 2f, key, rmb, rmbClicks.size() + " CPS", f9, ac, ap, sc);
            cy += key + gap;
        }

        boolean w = mc.options.forwardKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();
        boolean space = mc.options.jumpKey.isPressed();

        drawKey(ms, px + key + gap, cy, key, key, w, "W", f12, ac, ap, sc);
        drawKey(ms, px, cy + key + gap, key, key, a, "A", f12, ac, ap, sc);
        drawKey(ms, px + key + gap, cy + key + gap, key, key, s, "S", f12, ac, ap, sc);
        drawKey(ms, px + (key + gap) * 2f, cy + key + gap, key, key, d, "D", f12, ac, ap, sc);

        // Пробел — широкая низкая клавиша
        float spY = cy + (key + gap) * 2f;
        float spH = key * 0.45f;
        drawSpace(ms, px, spY, panelW, spH, space, ac, ap);
    }

    private void drawKey(MatrixStack ms, float x, float y, float w, float h, boolean pressed,
                         String label, Font font, int ac, float ap, float sc) {
        AnimationUtils a = keyAnim(label, x, y);
        a.update(pressed ? 1f : 0f);
        float p = MathHelper.clamp(a.getValue(), 0f, 1f);

        RenderUtils.drawShadow(ms, x, y + 0.5f, w, h, 5f * sc, 5f * sc,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.35f * ap));
        RenderUtils.drawBlur(ms, x, y, w, h, 5f * sc, 6f * sc,
                ColorUtils.rgba(7, 11, 21, (int) (160 * ap)));
        RenderUtils.drawRoundedRect(ms, x, y, w, h, 5f * sc,
                ColorUtils.rgba(12, 15, 24, (int) ((150 + 80 * p) * ap)));

        if (p > 0.03f) {
            RenderUtils.drawRoundedRect(ms, x, y, w, h, 5f * sc,
                    ColorUtils.applyAlpha(ac, 0.30f * p * ap));
            RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 5f * sc, 1f,
                    ColorUtils.applyAlpha(ac, (int) (120 * p * ap)),
                    ColorUtils.applyAlpha(ac, (int) (120 * p * ap)),
                    ColorUtils.applyAlpha(ac, (int) (70 * p * ap)),
                    ColorUtils.applyAlpha(ac, (int) (70 * p * ap)));
        } else {
            RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 5f * sc, 1f,
                    ColorUtils.rgba(255, 255, 255, (int) (16 * ap)),
                    ColorUtils.rgba(255, 255, 255, (int) (16 * ap)),
                    ColorUtils.rgba(0, 0, 0, (int) (36 * ap)),
                    ColorUtils.rgba(0, 0, 0, (int) (36 * ap)));
        }

        float cy = y + h / 2f;
        int col = p > 0.5f
                ? ColorUtils.rgba(245, 247, 252, (int) (250 * ap))
                : ColorUtils.rgba(205, 211, 224, (int) (225 * ap));
        // капс-высота = size * 0.4023 (MSDF half-size рендер), центрируем по ней
        int fsz = font == f12ref ? 12 : 9;
        float ty = cy - fsz * 0.4023f / 2f + 1.5f - 0.0792f * fsz;
        font.draw(ms, label, x + (w - font.getWidth(label)) / 2f, ty, col);
    }

    private void drawSpace(MatrixStack ms, float x, float y, float w, float h, boolean pressed,
                           int ac, float ap) {
        AnimationUtils a = keyAnim("space", x, y);
        a.update(pressed ? 1f : 0f);
        float p = MathHelper.clamp(a.getValue(), 0f, 1f);

        RenderUtils.drawBlur(ms, x, y, w, h, h / 2f, 6f,
                ColorUtils.rgba(7, 11, 21, (int) (160 * ap)));
        RenderUtils.drawRoundedRect(ms, x, y, w, h, h / 2f,
                ColorUtils.rgba(12, 15, 24, (int) ((150 + 80 * p) * ap)));
        if (p > 0.03f) {
            RenderUtils.drawRoundedRect(ms, x + w * 0.28f, y + h / 2f - 1f, w * 0.44f, 2f, 1f,
                    ColorUtils.applyAlpha(ac, 0.85f * p * ap));
        } else {
            RenderUtils.drawRoundedRect(ms, x + w * 0.28f, y + h / 2f - 1f, w * 0.44f, 2f, 1f,
                    ColorUtils.rgba(205, 211, 224, (int) (150 * ap)));
        }
    }

    private final java.util.Map<String, AnimationUtils> keyAnims = new java.util.HashMap<>();
    private Font f12ref;

    private AnimationUtils keyAnim(String label, float x, float y) {
        String key = label + "@" + (int) x + "," + (int) y;
        return keyAnims.computeIfAbsent(key, k -> new AnimationUtils(0f, 15f, Easings.CUBIC_OUT));
    }
}
