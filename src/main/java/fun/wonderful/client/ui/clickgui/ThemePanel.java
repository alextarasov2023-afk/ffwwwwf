package fun.wonderful.client.ui.clickgui;

import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easing;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.math.HoveringUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.scissor.ScissorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Панель тем — «столбик» в том же стиле, что и окна категорий Combat/Movement:
 * хедер с иконкой, внутри полноценные настройки: переключатель градиента,
 * скорость переливания, превью, RGB-слайдеры для двух цветов и сетка пресетов.
 * Акцент клик-гуи берётся отсюда с настоящим градиентом по вертикали экрана.
 */
public class ThemePanel {

    /** Ширина панели, как у окон категорий верхнего ряда (CSS-раскладка берет её отсюда). */
    public static final float W = 180f;
    private static final float HEADER_H = 34f;
    private static final float PAD_X = 12f;
    private static final float CONTENT_W = W - PAD_X * 2f;

    public static float panelX, panelY, panelH;

    // ===== Состояние темы =====
    public static int r1 = 40, g1 = 90, b1 = 255;      // первый цвет
    public static int r2 = 148, g2 = 64, b2 = 220;     // второй цвет (для градиента)
    public static boolean gradient = true;              // градиент включён
    public static String hexBuffer = "285AFF";

    // ===== Внутреннее состояние (скролл, анимации) =====
    private static float panelOpen = 1f;
    private static final AnimationUtils openAnim = new AnimationUtils(1f, 8f, Easings.BACK_OUT);

    /** Открыта ли панель тем (кнопка-палитра в шапке ClickGUI). */
    private static boolean open = false;
    private static final AnimationUtils openFade = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);
    /** Якорь позиции — задаётся ClickGuiScreen каждый кадр. */
    private static float anchorX = 6f, anchorY = 44f;

    public static boolean isOpen() {
        return open;
    }

    public static void setOpen(boolean value) {
        open = value;
        if (value) {
            entranceAnim.setValue(0f);
        }
    }

    public static boolean isShown() {
        return open || MathHelper.clamp(openFade.getValue(), 0f, 1f) > 0.02f;
    }

    public static void updateOpen() {
        openFade.update(open ? 1f : 0f);
    }

    public static void anchor(float x, float y) {
        anchorX = x;
        anchorY = y;
    }

    /** Пулы анимаций: появление панели, переключатель градиента, пипетка, hover-состояния. */
    private static final Map<String, AnimationUtils> hoverA = new HashMap<>();
    private static final AnimationUtils entranceAnim = new AnimationUtils(0f, 4f, Easings.BACK_OUT);
    private static final AnimationUtils gradAnim = new AnimationUtils(1f, 9f, Easings.BACK_OUT);
    private static final AnimationUtils popAnim = new AnimationUtils(0f, 7f, Easings.BACK_OUT);
    private static int lastPaletteTarget = -1;

    private static float scrollTarget, scrollCurrent;
    private static int dragChannel = -1;
    private static float dragTrackX, dragTrackW;

    /** Какая пипетка открыта: -1 = закрыта, 0 = для «Цвет 1», 1 = для «Цвет 2». */
    private static int paletteTarget = -1;

    /** HSV-состояние пипетки + флаг перетаскивания. */
    private static float palHue = 0.6f, palSat = 1f, palVal = 1f;
    private static boolean palDragging = false;

    /** каналы: 0..2 = R/G/B первого цвета, 3..5 = R/G/B второго */
    private static final int CH_R1 = 0, CH_G1 = 1, CH_B1 = 2, CH_R2 = 3, CH_G2 = 4, CH_B2 = 5;

    // 12 пресетов палитры (4 колонки x 3 ряда)
    private static final int[][] PRESETS = {
            {65, 105, 225}, {0, 168, 255}, {155, 81, 224}, {200, 90, 255},
            {255, 92, 160}, {232, 62, 90}, {255, 146, 43}, {250, 200, 48},
            {46, 204, 113}, {30, 190, 165}, {130, 215, 180}, {94, 53, 177},
    };
    private static final int PRESET_COLS = 4;
    private static final float PRESET_STEP = 34f;
    private static final float PRESET_D = 14f;

    // Мини-палитра-пипетка по ПКМ: поле S/V + полоса оттенка
    private static final float PAL_PAD = 10f;
    private static final float PAL_FIELD_W = 118f;
    private static final float PAL_FIELD_H = 62f;
    private static final float PAL_HUE_GAP = 7f;
    private static final float PAL_HUE_H = 10f;

    private ThemePanel() {
    }

    public static void init() {
        hexBuffer = String.format("%02X%02X%02X", r1, g1, b1);
        gradAnim.setValue(gradient ? 1f : 0f);
        dragChannel = -1;
    }

    /** Общий пул hover-анимаций (ключ — стабильная строка элемента). */
    private static AnimationUtils anim(String key, float speed, Easing easing) {
        return hoverA.computeIfAbsent(key, k -> new AnimationUtils(0f, speed, easing));
    }

    public static int rgb(int r, int g, int b, int a) {
        return ColorUtils.rgba(r, g, b, a);
    }

    private static int color1() {
        return ColorUtils.rgba(r1, g1, b1, 255);
    }

    private static int color2() {
        return ColorUtils.rgba(r2, g2, b2, 255);
    }

    private static int mixColor() {
        return ColorUtils.interpolateColor(color1(), color2(), 0.5f);
    }

    /**
     * Акцент с учётом градиента и позиции по экрану.
     * Пространственный градиент от первого ко второму цвету по вертикали экрана.
     * Анимации во времени нет — это статичный градиент.
     */
    public static int accent(float y) {
        if (!gradient) return color1();
        float h = ClickGuiScreen.screenHeightCached;
        float t = h > 1f ? MathHelper.clamp(y / h, 0f, 1f) : 0f;
        return ColorUtils.interpolateColor(color1(), color2(), t);
    }

    public static int accentSolid() {
        return gradient ? mixColor() : color1();
    }

    public static void setFromConfig(int nr1, int ng1, int nb1, int nr2, int ng2, int nb2, boolean grad) {
        r1 = nr1;
        g1 = ng1;
        b1 = nb1;
        r2 = nr2;
        g2 = ng2;
        b2 = nb2;
        gradient = grad;
        init();
    }
// ============================================================
    // Layout: все вертикальные позиции контента (с учётом скролла)
    // ============================================================

    private static float contentTop() {
        return panelY + HEADER_H + 6f - scrollCurrent;
    }

    private static float gradTop() {
        return contentTop();
    }

    private static float previewTop() {
        return gradTop() + 22f + 8f;
    }

    private static float c1LabelTop() {
        return previewTop() + 32f;
    }

    private static float c1SliderTop(int i) {
        return c1LabelTop() + 18f + i * 20f;
    }

    private static float c2LabelTop() {
        return c1SliderTop(2) + 20f + 10f;
    }

    private static float c2SliderTop(int i) {
        return c2LabelTop() + 18f + i * 20f;
    }

    private static float presetLabelTop() {
        return c2SliderTop(2) + 20f + 10f;
    }

    private static float presetGridTop() {
        return presetLabelTop() + 18f;
    }

    private static float contentHeight() {
        return presetGridTop() + 3f * PRESET_STEP + 6f - contentTop();
    }

    private static float sliderX() {
        return panelX + PAD_X;
    }

    private static float sliderW() {
        return CONTENT_W;
    }

    /**
     * Прямоугольник квадрата-превью у заголовка секции цвета.
     */
    private static float[] colorSwatchRect(boolean isFirst) {
        float labelTop = isFirst ? c1LabelTop() : c2LabelTop();
        float s = 14f;
        return new float[]{panelX + PAD_X + 6f - s / 2f, labelTop + 9f - s / 2f, s, s};
    }

    /** Прямоугольник всплывающей пипетки (в пределах панели). */
    private static float[] palettePopupRect(int target) {
        float labelTop = target == 0 ? c1LabelTop() : c2LabelTop();
        float w = PAL_PAD * 2f + PAL_FIELD_W;
        float h = PAL_PAD * 2f + PAL_FIELD_H + PAL_HUE_GAP + PAL_HUE_H;
        float x = panelX + PAD_X;
        float y = labelTop + 20f;
        y = MathHelper.clamp(y, panelY + HEADER_H + 2f, panelY + panelH - h - 4f);
        return new float[]{x, y, w, h};
    }

    /** Тот палитровый таргет, который рисуем прямо сейчас: открытый или последний при фейде. */
    private static int paletteDrawTarget() {
        return paletteTarget >= 0 ? paletteTarget : lastPaletteTarget;
    }

    /** Прямоугольник поля S/V (насыщенность × светлота). */
    private static float[] svFieldRect() {
        float[] r = palettePopupRect(paletteDrawTarget());
        return new float[]{r[0] + PAL_PAD, r[1] + PAL_PAD, PAL_FIELD_W, PAL_FIELD_H};
    }

    /** Прямоугольник полосы оттенка (радуга). */
    private static float[] hueBarRect() {
        float[] r = palettePopupRect(paletteDrawTarget());
        return new float[]{r[0] + PAL_PAD, r[1] + PAL_PAD + PAL_FIELD_H + PAL_HUE_GAP,
                PAL_FIELD_W, PAL_HUE_H};
    }

    /** 0 = поле S/V, 1 = полоса оттенка, -1 = вне пипетки. */
    private static int paletteAreaAt(int mx, int my) {
        float[] r = palettePopupRect(paletteTarget);
        if (!HoveringUtils.isHovered(mx, my, r[0], r[1], r[2], r[3])) return -1;
        float[] sv = svFieldRect();
        if (HoveringUtils.isHovered(mx, my, sv[0], sv[1], sv[2], sv[3])) return 0;
        float[] hb = hueBarRect();
        if (HoveringUtils.isHovered(mx, my, hb[0], hb[1], hb[2], hb[3])) return 1;
        return -1;
    }

    /** Инициализировать HSV пипетки из текущего цвета цели. */
    private static void initPickerFromTarget() {
        int cr = paletteTarget == 0 ? r1 : r2;
        int cg = paletteTarget == 0 ? g1 : g2;
        int cb = paletteTarget == 0 ? b1 : b2;
        float[] hsb = Color.RGBtoHSB(cr, cg, cb, null);
        palHue = hsb[0];
        palSat = hsb[1];
        palVal = hsb[2];
    }

    /** Применить цвета пипетки к выбранной цели. */
    private static void pickerToTarget() {
        int rgb = Color.HSBtoRGB(palHue, palSat, palVal);
        int pr = ColorUtils.red(rgb), pg = ColorUtils.green(rgb), pb = ColorUtils.blue(rgb);
        if (paletteTarget == 0) {
            r1 = pr; g1 = pg; b1 = pb;
        } else {
            r2 = pr; g2 = pg; b2 = pb;
        }
        init();
    }

    /** Обновить HSV пипетки по координате мыши и сразу применить к цели. */
    private static void applyPickerAt(int mx, int my, int area) {
        if (area == 0) {
            float[] sv = svFieldRect();
            palSat = MathHelper.clamp((mx - sv[0]) / Math.max(1f, sv[2]), 0f, 1f);
            palVal = 1f - MathHelper.clamp((my - sv[1]) / Math.max(1f, sv[3]), 0f, 1f);
        } else if (area == 1) {
            float[] hb = hueBarRect();
            palHue = MathHelper.clamp((mx - hb[0]) / Math.max(1f, hb[2]), 0f, 1f);
        }
        pickerToTarget();
    }

    /** Смещение сетки пресетов для центрирования по ширине панели. */
    private static float presetGridOffsetX() {
        float gridW = (PRESET_COLS - 1) * PRESET_STEP + PRESET_D;
        return Math.max(0f, (CONTENT_W - gridW) / 2f);
    }

    private static void setChannelValue(int ch, int mx) {
        float frac = MathHelper.clamp((mx - dragTrackX) / Math.max(1f, dragTrackW), 0f, 1f);
        float raw = frac * 255f;
        float val = Math.round(raw);
        val = MathHelper.clamp(val, 0f, 255f);
        switch (ch) {
            case CH_R1: r1 = (int) val; init(); break;
            case CH_G1: g1 = (int) val; init(); break;
            case CH_B1: b1 = (int) val; init(); break;
            case CH_R2: r2 = (int) val; init(); break;
            case CH_G2: g2 = (int) val; init(); break;
            case CH_B2: b2 = (int) val; init(); break;
            default: break;
        }
    }

    private static float[] sliderTrack(int ch) {
        float y;
        if (ch < 3) {
            y = c1SliderTop(ch) + 14f;
        } else {
            y = c2SliderTop(ch - 3) + 14f;
        }
        return new float[]{sliderX(), y, sliderW()};
    }
// ============================================================
    // Рендер
    // ============================================================

    public static void render(DrawContext context, int mouseX, int mouseY, float alpha) {
        if (alpha < 0.05f) return;
        MatrixStack ms = context.getMatrices();

        float screenH = ClickGuiScreen.screenHeightCached;

        // Анимации: плавное появление панели (въезд сверху + фейд), переключатель градиента и пипетка
        entranceAnim.update(1f);
        float epRaw = entranceAnim.getValue();
        float ep = MathHelper.clamp(epRaw, 0f, 1f);
        float eA = MathHelper.clamp(ep * 2.4f, 0f, 1f);
        gradAnim.update(gradient ? 1f : 0f);
        popAnim.update(paletteTarget >= 0 ? 1f : 0f);

        panelX = anchorX;
        panelY = anchorY - (1f - epRaw) * 14f;

        float contentH = contentHeight();
        float availH = screenH - panelY - 12f;
        float neededH = HEADER_H + 8f + contentH;
        panelH = Math.max(120f, Math.min(availH, neededH));

        openAnim.setValue(panelOpen);
        float ov = MathHelper.clamp(openFade.getValue(), 0f, 1f);
        float a = MathHelper.clamp(openAnim.getValue(), 0f, 1f) * alpha * eA * ov;
        if (a < 0.02f) return;

        // Плавный скролл
        float viewH = Math.max(24f, panelH - HEADER_H - 4f);
        float maxScroll = Math.max(0f, contentH - viewH);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        scrollCurrent += (scrollTarget - scrollCurrent) * 0.14f;
        if (Math.abs(scrollTarget - scrollCurrent) < 0.05f) scrollCurrent = scrollTarget;

        int acTop = ColorUtils.applyAlpha(accent(panelY + 3f), (int) (165 * a));
        int acBot = ColorUtils.applyAlpha(accent(panelY + panelH - 3f), (int) (110 * a));

        // Акцентное свечение + тень + блюр + фон
        int glowC = ColorUtils.applyAlpha(ClickGuiScreen.accent(), 0.20f * a);
        RenderUtils.drawShadow(ms, panelX - 1f, panelY - 1f, W + 2f, panelH + 2f, 13f, 15f,
                glowC, glowC, glowC, glowC);
        RenderUtils.drawShadow(ms, panelX, panelY, W, panelH, 11f, 12f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.5f));
        RenderUtils.drawBlur(ms, panelX, panelY, W, panelH, 11f, 9f,
                ColorUtils.rgba(7, 11, 21, (int) (180 * a)));
        RenderUtils.drawRoundedRect(ms, panelX, panelY, W, panelH, 11f,
                ColorUtils.rgba(12, 15, 24, (int) (246 * a)));
        RenderUtils.drawRoundedRectOutline(ms, panelX, panelY, W, panelH, 11f, 1f,
                acTop, acTop, acBot, acBot);

        drawHeader(ms, a);

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates((int) (panelX + 1), (int) (panelY + HEADER_H),
                (int) W - 2, Math.max(0, (int) Math.ceil(panelH - HEADER_H) - 1));

        drawGradientRow(ms, a, mouseX, mouseY);
        drawPreview(ms, a);
        drawColorSection(ms, "Цвет 1", r1, g1, b1, true, a, mouseX, mouseY);
        drawColorSection(ms, "Цвет 2", r2, g2, b2, false, a, mouseX, mouseY);
        drawPresets(ms, a, mouseX, mouseY);

        ScissorUtils.pop();

        // Мини-палитра рисуется поверх контента, без обрезки скроллом
        drawPalettePopup(ms, a, mouseX, mouseY);

        // Скроллбар
        boolean needScroll = contentH > viewH + 1f;
        if (needScroll && hit(mouseX, mouseY)) {
            float trackX = panelX + W - 5f;
            float trackY = panelY + HEADER_H + 4f;
            float trackH = panelH - HEADER_H - 8f;
            float thumbH = Math.max(14f, trackH * (viewH / contentH));
            thumbH = Math.min(thumbH, trackH);
            float thumbY = trackY + (scrollCurrent / maxScroll) * (trackH - thumbH);
            int ac = ClickGuiScreen.accent();
            RenderUtils.drawRoundedRect(ms, trackX, thumbY, 2f, thumbH, 1f,
                    ColorUtils.setAlphaColor(ac, (int) (170 * a)));
        }
    }

    private static void drawHeader(MatrixStack ms, float a) {
        float hcy = panelY + HEADER_H / 2f;
        // Иконка-палитра — PNG из /assets/wonderful/textures/gui/theme_palette.png,
        // тонируется акцентом GUI с лёгким приглушением яркости.
        int icoCol = ColorUtils.applyAlpha(
                ColorUtils.interpolateColor(ClickGuiScreen.accent(), 0xFFFFFFFF, 0.18f),
                a);
        float icoSize = 16f;
        GuiIcons.draw(ms, "theme_palette",
                panelX + 18f - icoSize / 2f,
                hcy - icoSize / 2f,
                icoSize, icoCol);

        ModuleList.text(ms, 15, "theme", panelX + 33, hcy - ModuleList.fh(15) / 2f,
                ColorUtils.rgba(242, 244, 250, (int) (250 * a)));

        int hdrAccent = ColorUtils.applyAlpha(accent(panelY + HEADER_H), (int) (110 * a));
        RenderUtils.drawRoundedRect(ms, panelX + PAD_X, panelY + HEADER_H - 1f, W - PAD_X * 2f, 1f, 0.5f, hdrAccent);
    }
/** Строка-переключатель «Градиент» */
    private static void drawGradientRow(MatrixStack ms, float a, int mouseX, int mouseY) {
        float y = gradTop();
        ModuleList.text(ms, 11, "Градиент", panelX + PAD_X, y + 11f - ModuleList.fh(11) / 2f,
                ColorUtils.rgba(228, 232, 240, (int) (232 * a)));

        float swX = panelX + W - PAD_X - ToggleSwitch.W;
        float swY = y + (20f - ToggleSwitch.H) / 2f;
        float gp = MathHelper.clamp(gradAnim.getValue(), 0f, 1f);
        ToggleSwitch.draw(ms, swX, swY, gp, a, accent(swY));
    }

    /** Полоса-превью текущего градиента */
    private static void drawPreview(MatrixStack ms, float a) {
        float y = previewTop();
        float x = panelX + PAD_X;
        float w = CONTENT_W;
        float h = 22f;

        RenderUtils.drawShadow(ms, x, y + 1f, w, h, 5f, 6f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.35f * a));
        if (gradient) {
            RenderUtils.drawGradientRect(ms, x, y, w, h, 6f,
                    ColorUtils.applyAlpha(color1(), (int) (235 * a)),
                    ColorUtils.applyAlpha(color2(), (int) (235 * a)), true);
        } else {
            RenderUtils.drawRoundedRect(ms, x, y, w, h, 6f,
                    ColorUtils.applyAlpha(color1(), (int) (235 * a)));
        }
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 6f, 1f,
                ColorUtils.rgba(255, 255, 255, (int) (18 * a)),
                ColorUtils.rgba(255, 255, 255, (int) (18 * a)),
                ColorUtils.rgba(0, 0, 0, (int) (40 * a)),
                ColorUtils.rgba(0, 0, 0, (int) (40 * a)));

        String hex = String.format("%02X%02X%02X", r1, g1, b1) + " -> " + String.format("%02X%02X%02X", r2, g2, b2);
        ModuleList.text(ms, 10, hex, x + w - ModuleList.tw(10, hex),
                y + h / 2f - ModuleList.fh(10) / 2f,
                ColorUtils.rgba(255, 255, 255, (int) (235 * a)));
    }

    /** Секция одного цвета: заголовок + R/G/B слайдеры */
    private static void drawColorSection(MatrixStack ms, String title, int cr, int cg, int cb,
                                         boolean isFirst, float a, int mouseX, int mouseY) {
        float labelTop = isFirst ? c1LabelTop() : c2LabelTop();
        float ly = labelTop + 9f;
        float[] sw = colorSwatchRect(isFirst);
        RenderUtils.drawRoundedRect(ms, sw[0], sw[1], sw[2], sw[3], 3f,
                ColorUtils.applyAlpha(ColorUtils.rgba(cr, cg, cb, 255), (int) (245 * a)));
        ModuleList.text(ms, 11, title, panelX + PAD_X + 18, ly - ModuleList.fh(11) / 2f,
                ColorUtils.rgba(220, 225, 235, (int) (220 * a)));
        String hex = String.format("%02X%02X%02X", cr, cg, cb);
        ModuleList.text(ms, 10, hex, panelX + PAD_X + CONTENT_W - ModuleList.tw(10, hex),
                ly - ModuleList.fh(10) / 2f, ColorUtils.applyAlpha(ColorUtils.rgba(cr, cg, cb, 255), 0.9f * a));

                String[] names = {"R", "G", "B"};
        int[] vals = {cr, cg, cb};
                int[] chanCols = {
                ColorUtils.rgba(245, 80, 90, 255),
                ColorUtils.rgba(90, 200, 100, 255),
                ColorUtils.rgba(80, 140, 255, 255)
        };
        for (int i = 0; i < 3; i++) {
            float y = isFirst ? c1SliderTop(i) : c2SliderTop(i);
            drawMiniSlider(ms, names[i], vals[i], 0f, 255f, panelX + PAD_X, y, CONTENT_W,
                    a, true, chanCols[i]);
        }
    }
/** Компактный слайдер: мгновенный отклик значения (без анимации — важно при зажатии/перетаскивании). */
    private static void drawMiniSlider(MatrixStack ms, String label, float value, float min, float max,
                                       float x, float y, float w, float a, boolean enabled, int chanCol) {
        if (a < 0.01f) return;
        float effA = enabled ? a : a * 0.55f;

        ModuleList.text(ms, 11, label, x, y, ColorUtils.rgba(222, 226, 236, (int) (225 * effA)));
        String valTxt = max >= 3f ? String.valueOf(Math.round(value))
                : String.format(Locale.US, "%.2f", value);
        ModuleList.text(ms, 10, valTxt, x + w - ModuleList.tw(10, valTxt),
                y + ModuleList.fh(11) - ModuleList.fh(10), ColorUtils.applyAlpha(chanCol, 0.92f * effA));

        float trackY = y + 14f;
        float trackH = 3f;
        RenderUtils.drawRoundedRect(ms, x, trackY, w, trackH, trackH / 2f,
                ColorUtils.rgba(255, 255, 255, (int) (20 * effA)));
        float frac = MathHelper.clamp((value - min) / Math.max(0.0001f, max - min), 0f, 1f);
        float fw = w * frac;
        if (fw > 0.5f) {
            int fillL = ColorUtils.applyAlpha(chanCol, 0.60f * effA);
            int fillR = ColorUtils.applyAlpha(ColorUtils.interpolateColor(chanCol, 0xFFFFFFFF, 0.20f), 0.95f * effA);
            RenderUtils.drawGradientRect(ms, x, trackY, fw, trackH, trackH / 2f, fillL, fillR, true);
        }
        float knobD = 6f;
        float knobCx = x + fw;
        float knobCy = trackY + trackH / 2f;
        RenderUtils.drawRoundCircle(ms, knobCx, knobCy + 0.6f, knobD + 0.6f,
                ColorUtils.rgba(0, 0, 0, (int) (34 * effA)));
        RenderUtils.drawRoundCircle(ms, knobCx, knobCy, knobD,
                enabled ? ColorUtils.rgba(250, 251, 253, (int) (255 * effA))
                        : ColorUtils.rgba(140, 148, 165, (int) (220 * effA)));
    }

    /** Сетка пресетов палитры */
    private static void drawPresets(MatrixStack ms, float a, int mouseX, int mouseY) {
        float labelTop = presetLabelTop();
        ModuleList.text(ms, 11, "Пресеты", panelX + PAD_X, labelTop,
                ColorUtils.rgba(220, 225, 235, (int) (215 * a)));

        float gridTop = presetGridTop();
        for (int i = 0; i < PRESETS.length; i++) {
            int col = i % PRESET_COLS;
            int row = i / PRESET_COLS;
            float cx = panelX + PAD_X + presetGridOffsetX() + col * PRESET_STEP;
            float cy = gridTop + row * PRESET_STEP + 7.5f;

            int[] pc = PRESETS[i];
            boolean isC1 = pc[0] == r1 && pc[1] == g1 && pc[2] == b1;
            boolean isC2 = pc[0] == r2 && pc[1] == g2 && pc[2] == b2;

            // Кольцо рисуется только у выбранного пресета (цвет 1/2) — без подсветки наведения
            AnimationUtils prs = anim("prs" + i, 12f, Easings.CUBIC_OUT);
            prs.update((isC1 || isC2) ? 1f : 0f);
            float pp = MathHelper.clamp(prs.getValue(), 0f, 1f);
            float d = PRESET_D;

            int colA = ColorUtils.applyAlpha(ColorUtils.rgba(pc[0], pc[1], pc[2], 255), (int) (250 * a));

            // Скруглённый квадрат вместо кружка
            RenderUtils.drawRoundedRect(ms, cx - d / 2f, cy - d / 2f + 1f, d, d, 3f,
                    ColorUtils.rgba(0, 0, 0, (int) (60 * a)));
            RenderUtils.drawRoundedRect(ms, cx - d / 2f, cy - d / 2f, d, d, 3f, colA);

            if (pp > 0.04f) {
                float pad = 1.8f;
                RenderUtils.drawRoundedRectOutline(ms, cx - (d + pad) / 2f, cy - (d + pad) / 2f,
                        d + pad, d + pad, 3.5f, 1.4f,
                        ColorUtils.rgba(255, 255, 255, (int) (235 * pp * a)),
                        ColorUtils.rgba(255, 255, 255, (int) (235 * pp * a)),
                        ColorUtils.rgba(255, 255, 255, (int) (165 * pp * a)),
                        ColorUtils.rgba(255, 255, 255, (int) (165 * pp * a)));
            }
        }
    }

    /** Всплывающая пипетка по ПКМ: поле S/V + полоса оттенка. */
    private static void drawPalettePopup(MatrixStack ms, float a, int mouseX, int mouseY) {
        if (paletteTarget < 0) {
            if (lastPaletteTarget < 0) return;
            popAnim.update(0f);
        } else {
            lastPaletteTarget = paletteTarget;
            popAnim.update(1f);
        }
        float pop = MathHelper.clamp(popAnim.getValue(), 0f, 1f);
        if (pop <= 0.02f) {
            if (paletteTarget < 0) lastPaletteTarget = -1;
            return;
        }

        float[] r = palettePopupRect(paletteDrawTarget());
        float x = r[0], y = r[1], w = r[2], h = r[3];

        // Плавное «появление» пипетки: масштаб 0.9 → 1.0 вокруг центра + фейд
        float scale = 0.9f + 0.1f * pop;
        float cx = x + w / 2f, cy = y + h / 2f;
        ms.push();
        ms.translate(cx, cy, 0f);
        ms.scale(scale, scale, 1f);
        ms.translate(-cx, -cy, 0f);
        a = a * MathHelper.clamp(pop * 1.8f, 0f, 1f);

        RenderUtils.drawShadow(ms, x, y + 1f, w, h, 6f, 7f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.5f * pop));
        RenderUtils.drawRoundedRect(ms, x, y, w, h, 8f,
                ColorUtils.rgba(13, 17, 28, (int) (250 * a)));
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 8f, 1f,
                ColorUtils.applyAlpha(accent(y), (int) (150 * a)),
                ColorUtils.applyAlpha(accent(y), (int) (150 * a)),
                ColorUtils.applyAlpha(accent(y + h), (int) (110 * a)),
                ColorUtils.applyAlpha(accent(y + h), (int) (110 * a)));

        // ---- Поле S/V: X = насыщенность, Y = светлота (сверху светлее) ----
        float[] sv = svFieldRect();
        float fx = sv[0], fy = sv[1], fw = sv[2], fh = sv[3];

        int hueFull = Color.HSBtoRGB(palHue, 1f, 1f);
        int hueR = ColorUtils.red(hueFull), hueG = ColorUtils.green(hueFull),
                hueB = ColorUtils.blue(hueFull);

        // Поле S/V рисуется одним 4-угловым билинейным градиентом:
        //  TL=белый (sat=0,val=1), TR=чистый оттенок (sat=1,val=1),
        //  BL=чёрный (val=0), BR=чёрный (val=0).
        // Это даёт точное поле HSV при интерполяции углов.
        int tf = (int) (255 * a); // фактор альфы для цветов поля
        RenderUtils.drawGradientRect(ms, fx, fy, fw, fh, 4f,
                ColorUtils.rgba(255, 255, 255, tf),
                ColorUtils.rgba(hueR, hueG, hueB, tf),
                ColorUtils.rgba(0, 0, 0, tf),
                ColorUtils.rgba(0, 0, 0, tf));

        RenderUtils.drawRoundedRectOutline(ms, fx, fy, fw, fh, 4f, 1f,
                ColorUtils.rgba(0, 0, 0, (int) (65 * a)),
                ColorUtils.rgba(0, 0, 0, (int) (65 * a)),
                ColorUtils.rgba(0, 0, 0, (int) (65 * a)),
                ColorUtils.rgba(0, 0, 0, (int) (65 * a)));

        // Курсор пипетки в поле S/V
        float cx2 = fx + palSat * fw;
        float cy2 = fy + (1f - palVal) * fh;
        boolean svHover = HoveringUtils.isHovered(mouseX, mouseY, fx, fy, fw, fh)
                || palDragging;
        float curD = svHover ? 7.5f : 6f;
        int curActual = Color.HSBtoRGB(palHue, palSat, palVal);
        int curCol = ColorUtils.rgba(ColorUtils.red(curActual), ColorUtils.green(curActual),
                ColorUtils.blue(curActual), (int) (255 * a));
        RenderUtils.drawRoundCircle(ms, cx2, cy2 + 1f, curD + 1f,
                ColorUtils.rgba(0, 0, 0, (int) (95 * a)));
        RenderUtils.drawRoundCircle(ms, cx2, cy2, curD,
                ColorUtils.rgba(255, 255, 255, (int) (255 * a)));
        RenderUtils.drawRoundCircle(ms, cx2, cy2, curD - 3f,
                curCol);

        // ---- Полоса оттенка (радуга) ----
        float[] hb = hueBarRect();
        float bx = hb[0], by = hb[1], bw = hb[2], bh = hb[3];

        float segW = bw / 6f;
        for (int i = 0; i < 6; i++) {
            int cA = Color.HSBtoRGB(i / 6f, 1f, 1f);
            int cB = Color.HSBtoRGB((i + 1) / 6f, 1f, 1f);
            int cl = ColorUtils.rgba(ColorUtils.red(cA), ColorUtils.green(cA),
                    ColorUtils.blue(cA), (int) (255 * a));
            int cr2 = ColorUtils.rgba(ColorUtils.red(cB), ColorUtils.green(cB),
                    ColorUtils.blue(cB), (int) (255 * a));
            RenderUtils.drawGradientRect(ms, bx + i * segW, by, segW + (i == 5 ? 0.5f : 0f),
                    bh, 0f, cl, cr2, true);
        }

        // Курсор полосы оттенка
        float hx2 = bx + palHue * bw;
        RenderUtils.drawRoundCircle(ms, hx2, by + bh / 2f + 1f, 6f,
                ColorUtils.rgba(0, 0, 0, (int) (90 * a)));
        RenderUtils.drawRoundCircle(ms, hx2, by + bh / 2f, 5.5f,
                ColorUtils.rgba(255, 255, 255, (int) (255 * a)));
        ms.pop();
    }
// ============================================================
    // Взаимодействие
    // ============================================================

    public static boolean hit(float mx, float my) {
        return panelH > 0 && HoveringUtils.isHovered(mx, my, panelX, panelY, W, panelH);
    }

    public static boolean hitHexFocused() {
        return false;
    }

    public static void mouseClick(int mx, int my, int button) {
        if (!hit(mx, my)) return;

        // Открытая пипетка перехватывает клики
        if (paletteTarget >= 0) {
            if (button == 0) {
                int area = paletteAreaAt(mx, my);
                if (area >= 0) {
                    palDragging = true;
                    applyPickerAt(mx, my, area);
                    return;
                }
            }
            paletteTarget = -1;
            palDragging = false;
            return;
        }

        // ПКМ по квадрату цвета — открыть пипетку для этого цвета
        if (button == 1) {
            float[] s1 = colorSwatchRect(true);
            float[] s2 = colorSwatchRect(false);
            if (HoveringUtils.isHovered(mx, my, s1[0], s1[1], s1[2], s1[3])) {
                paletteTarget = 0;
                initPickerFromTarget();
                return;
            }
            if (HoveringUtils.isHovered(mx, my, s2[0], s2[1], s2[2], s2[3])) {
                paletteTarget = 1;
                initPickerFromTarget();
                return;
            }
        }

        // Переключатель градиента
        float gy = gradTop();
        if (button == 0 && HoveringUtils.isHovered(mx, my, panelX + PAD_X, gy, CONTENT_W, 20f)) {
            gradient = !gradient;
            return;
        }

        // RGB слайдеры двух цветов
        for (int ch = 0; ch < 6; ch++) {
            float[] tr = sliderTrack(ch);
            if (button == 0 && HoveringUtils.isHovered(mx, my, tr[0], tr[1] - 5f, tr[2], 11f)) {
                dragChannel = ch;
                dragTrackX = tr[0];
                dragTrackW = tr[2];
                setChannelValue(ch, mx);
                return;
            }
        }

        // Пресеты: ЛКМ — первый цвет, ПКМ — второй
        float gridTop = presetGridTop();
        for (int i = 0; i < PRESETS.length; i++) {
            int col = i % PRESET_COLS;
            int row = i / PRESET_COLS;
            float cx = panelX + PAD_X + presetGridOffsetX() + col * PRESET_STEP;
            float cy = gridTop + row * PRESET_STEP + 7.5f;
            if (HoveringUtils.isHovered(mx, my, cx - 9f, cy - 9f, 18f, 18f)) {
                int[] pc = PRESETS[i];
                if (button == 0) {
                    r1 = pc[0];
                    g1 = pc[1];
                    b1 = pc[2];
                    init();
                } else if (button == 1) {
                    r2 = pc[0];
                    g2 = pc[1];
                    b2 = pc[2];
                    init();
                }
                return;
            }
        }
    }

    public static void mouseDrag(int mx, int my) {
        if (palDragging) {
            int area = paletteAreaAt(mx, my);
            if (area >= 0) applyPickerAt(mx, my, area);
            return;
        }
        if (dragChannel < 0) return;
        setChannelValue(dragChannel, mx);
    }

    public static void mouseRelease() {
        dragChannel = -1;
        palDragging = false;
    }

    public static void mouseScroll(float amount, int mx, int my) {
        if (!HoveringUtils.isHovered(mx, my, panelX, panelY, W, panelH)) return;
        scrollTarget -= amount * 22f;
    }

    public static void charTyped(char chr) {
    }

    public static void keyPressed(int key) {
        if (paletteTarget >= 0) {
            paletteTarget = -1;
            palDragging = false;
        }
    }
}