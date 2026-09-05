package fun.wonderful.client.ui.clickgui;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easing;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.input.KeyBoardUtils;
import fun.wonderful.api.utils.math.HoveringUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.api.utils.scissor.ScissorUtils;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.Setting;
import fun.wonderful.client.modules.settings.implement.BindSetting;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ListSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
import fun.wonderful.client.modules.settings.implement.TextSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Список модулей выбранной категории внутри главной панели ClickGUI.
 * ДВА столбика: модули делятся пополам (левый — первая половина, правый —
 * вторая), общий плавный скролл, каскад появления, hover-пилюли, мягкая
 * вспышка при переключении, настройки раскрываются под строкой в своей колонке.
 */
class ModuleList {

    // ===== Геометрия =====
    private float x, y, w, h;
    /** Текущая колонка (для рендера строк и кликов). */
    private float colX, colW;

    static final int ROW_H = 26;
    private static final int ROW_GAP = 2;
    private static final int PAD = 12;
    private static final float COL_GAP = 8f;

    // Высоты строк настроек — единый источник для рендера, кликов и расчёта высоты
    static final int SETTINGS_TOP_PAD = 12;
    static final int BOOL_H = 20;
    static final int SLIDER_H = 29;
    static final int BIND_H = 21;
    static final int TEXT_H = 29;
    static final int LIST_HEAD_H = 16;
    static final int LIST_CHILD_H = 17;
    static final int MODE_LABEL_H = 13;
    static final int MODE_LINE_H = 21;
    static final int MODE_PAD_B = 4;
    static final int MODE_PILL_H = 17;
    private static final float CHECKBOX_SIZE = 11f;

    private final Map<Object, AnimationUtils> animPool = new HashMap<>();
    private final Map<FloatSetting, AnimationUtils> sliderValues = new HashMap<>();
    private final Map<ModeSetting, float[]> modeGlide = new HashMap<>();
    private final Map<ModeSetting, float[]> modePulse = new HashMap<>(); // [time, lastIdx]
    private final Map<Module, Long> lastToggleAt = new HashMap<>();
    private final Set<Module> expanded = new HashSet<>();

    private Module hoverModule;
    private long hoverSince;

    private float scrollTarget, scrollCurrent;
    private final AnimationUtils scrollVis = new AnimationUtils(0f, 11f, Easings.CUBIC_OUT);

    /** Каскад появления строк: ключ контента + время последней смены. */
    private String contentKey = "";
    private long staggerAt;

    // ===== Текстовые хелперы (общие для всего GUI) =====

    static Font f(int size) {
        return Fonts.getFont("suisse", size);
    }

    static void text(MatrixStack ms, int size, String s, float px, float py, int color) {
        Font fo = f(size);
        if (fo != null && s != null && !s.isEmpty()) fo.draw(ms, s, px, py + 1.5f - 0.0792f * size, color);
    }

    static float tw(int size, String s) {
        Font fo = f(size);
        return fo == null ? 0 : fo.getWidth(s);
    }

    static float fh(int size) {
        return size * 0.4023f;
    }

    // ===== Публичный интерфейс =====

    void bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    List<Module> modules(Module.ModuleCategory category) {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.getCategory() == category) out.add(m);
        }
        return out;
    }

    boolean hit(int mx, int my) {
        return HoveringUtils.isHovered(mx, my, x, y, w, h);
    }

    void scroll(float amount) {
        scrollTarget -= amount * 26f;
    }

    /** Свернуть все раскрытые настройки модулей (кнопка в подвале). */
    void collapseAll() {
        expanded.clear();
    }

    Module tooltipCandidate() {
        if (hoverModule != null
                && System.currentTimeMillis() - hoverSince > 350
                && matches(hoverModule)) {
            return hoverModule;
        }
        return null;
    }

    // ===== Раскладка колонок =====

    private List<Module> visibleModules(Module.ModuleCategory category) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules(category)) {
            if (matches(m)) out.add(m);
        }
        return out;
    }

    private boolean matches(Module m) {
        if (ClickGuiScreen.showOnlyEnabled && !m.isEnable()) return false;
        String q = ClickGuiScreen.filter.trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(q);
    }

    private float columnWidth() {
        return (w - COL_GAP) / 2f;
    }

    private float columnHeight(List<Module> list, float cw) {
        float hh = 4;
        for (Module m : list) hh += moduleFullHeight(m, cw) + rowGapF(m);
        return hh;
    }

    // ===== Рендер =====

    void render(MatrixStack ms, int mouseX, int mouseY, float dt, float alpha,
                Module.ModuleCategory category, int accent) {
        List<Module> mods = visibleModules(category);

        String key = category.name() + "|" + ClickGuiScreen.filter + "|" + ClickGuiScreen.showOnlyEnabled;
        if (!key.equals(contentKey)) {
            contentKey = key;
            staggerAt = System.currentTimeMillis();
            scrollTarget = 0f;
        }

        float cw = columnWidth();
        int mid = (mods.size() + 1) / 2;
        List<Module> left = mods.subList(0, mid);
        List<Module> right = mods.subList(Math.min(mid, mods.size()), mods.size());

        float bodyFull = Math.max(4f, Math.max(columnHeight(left, cw), columnHeight(right, cw)));
        float maxScroll = Math.max(0f, bodyFull - h);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        float sk = 1f - (float) Math.exp(-dt * 16.0);
        scrollCurrent += (scrollTarget - scrollCurrent) * sk;
        if (Math.abs(scrollTarget - scrollCurrent) < 0.05f) scrollCurrent = scrollTarget;

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates((int) x, (int) y - 1, (int) w,
                (int) Math.ceil(h) + 2);

        renderColumn(ms, left, x, cw, 0, mouseX, mouseY, accent, alpha, dt);
        if (!right.isEmpty()) {
            renderColumn(ms, right, x + cw + COL_GAP, cw, mid, mouseX, mouseY, accent, alpha, dt);
        }

        ScissorUtils.pop();

        // Тонкий скроллбар — проявляется при наведении на список или во время прокрутки
        boolean sbHot = hit(mouseX, mouseY) || Math.abs(scrollTarget - scrollCurrent) > 2f;
        scrollVis.update(sbHot && bodyFull > h + 1f ? 1f : 0f);
        float sba = MathHelper.clamp(scrollVis.getValue(), 0f, 1f);
        if (sba > 0.03f && bodyFull > h + 1f) {
            float trackX = x + w - 4f;
            float trackY = y + 3f;
            float trackH = h - 6f;
            float thumbH = Math.max(16f, trackH * (h / Math.max(1f, bodyFull)));
            thumbH = Math.min(thumbH, trackH);
            float thumbY = trackY + (scrollCurrent / Math.max(1f, maxScroll)) * (trackH - thumbH);
            thumbY = MathHelper.clamp(thumbY, trackY, Math.max(trackY, trackY + trackH - thumbH));
            RenderUtils.drawRoundedRect(ms, trackX, thumbY, 2f, thumbH, 1f,
                    ColorUtils.applyAlpha(accent, 0.55f * sba * alpha));
        }
    }

    private void renderColumn(MatrixStack ms, List<Module> list, float cx, float cw, int baseIdx,
                              int mouseX, int mouseY, int ac, float alpha, float dt) {
        this.colX = cx;
        this.colW = cw;
        long sinceStagger = System.currentTimeMillis() - staggerAt;

        float cy = y + 4 - scrollCurrent;
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            float rowT = MathHelper.clamp((sinceStagger - (baseIdx + i) * 14L) / 110f, 0f, 1f);
            float rowA = alpha * rowT;
            float rowOff = (1f - rowT) * 7f;
            if (rowA > 0.01f) {
                renderModule(ms, m, cy + rowOff, mouseX, mouseY, ac, alpha, dt, rowA, i);
            }
            cy += moduleFullHeight(m, cw) + rowGapF(m);
        }
    }

    private AnimationUtils anim(Object key, float speed, Easing ease) {
        return animPool.computeIfAbsent(key, k -> new AnimationUtils(0f, speed, ease));
    }

    private boolean hasSettings(Module m) {
        for (Setting<?> s : m.getSettings()) {
            if (s != null && s.isVisible()) return true;
        }
        return false;
    }

    private int pillLines(ModeSetting mode, float cw) {
        return countLines(pillLayout(mode, insetWFor(cw) - 12f));
    }

    /** Ширина блока настроек для колонки шириной cw. */
    private static float insetWFor(float cw) {
        return cw - (PAD + 8f) * 2f;
    }

    private int countLines(List<float[]> layout) {
        if (layout.isEmpty()) return 1;
        float lastY = layout.get(layout.size() - 1)[1];
        return Math.max(1, Math.round(lastY / (float) MODE_LINE_H) + 1);
    }

    private List<float[]> pillLayout(ModeSetting mode, float forWidth) {
        float maxW = (forWidth > 0f ? forWidth : insetW()) - 10f;
        List<float[]> out = new ArrayList<>();
        float px = 0;
        float py = 0;
        for (String mdn : mode.getModes()) {
            float pw = tw(11, mdn) + 12;
            if (px + pw > maxW && px > 0) {
                px = 0;
                py += MODE_LINE_H;
            }
            out.add(new float[]{px, py, pw});
            px += pw + 4;
        }
        return out;
    }

    private float insetX() {
        return colX + PAD + 8f;
    }

    private float insetW() {
        return colW - (PAD + 8f) * 2f;
    }

    private int settingsHeight(Module m, float cw) {
        int hh = SETTINGS_TOP_PAD;
        for (Setting<?> s : m.getSettings()) {
            if (s == null || !s.isVisible()) continue;
            if (s instanceof BooleanSetting) hh += BOOL_H;
            else if (s instanceof FloatSetting) hh += SLIDER_H;
            else if (s instanceof TextSetting) hh += TEXT_H;
            else if (s instanceof BindSetting) hh += BIND_H;
            else if (s instanceof ModeSetting mode) hh += MODE_LABEL_H + pillLines(mode, cw) * MODE_LINE_H + MODE_PAD_B;
            else if (s instanceof ListSetting list) hh += LIST_HEAD_H + list.getSettings().size() * LIST_CHILD_H;
        }
        return hh;
    }

    private float moduleFullHeight(Module m, float cw) {
        float ex = MathHelper.clamp(anim(m.toString() + "_ex", 10f, Easings.CUBIC_OUT).getValue(), 0f, 1f);
        int sh = hasSettings(m) ? settingsHeight(m, cw) : 0;
        return ROW_H + sh * ex;
    }

    private float rowGapF(Module m) {
        return ROW_GAP;
    }

    // ===== Строка модуля =====

    private void renderModule(MatrixStack ms, Module m, float ry, int mouseX, int mouseY,
                              int ac, float winP, float dt, float vp, int idxInCol) {
        AnimationUtils tgA = anim(m.toString() + "_tg", 9f, Easings.BACK_OUT);
        tgA.update(m.isEnable() ? 1f : 0f);
        float enP = MathHelper.clamp(tgA.getValue(), 0f, 1.15f);
        float enC = Math.min(1f, enP);

        AnimationUtils exA = anim(m.toString() + "_ex", 10f, Easings.CUBIC_OUT);
        exA.update(expanded.contains(m) ? 1f : 0f);
        float exP = MathHelper.clamp(exA.getValue(), 0f, 1f);

        boolean listMoving = Math.abs(scrollTarget - scrollCurrent) > 0.2f;
        boolean hov = !listMoving && HoveringUtils.isHovered(mouseX, mouseY, colX, ry, colW, ROW_H);
        if (hov) {
            hoverModule = m;
            hoverSince = System.currentTimeMillis();
        } else if (hoverModule == m) {
            hoverModule = null;
        }
        AnimationUtils hovA = anim(m.toString() + "_hov", 14f, Easings.CUBIC_OUT);
        hovA.update(hov ? 1f : 0f);
        float hp = MathHelper.clamp(hovA.getValue(), 0f, 1f);

        // Тонкая полоска-зебра: каждый второй ряд чуть притушён для читаемости
        if (idxInCol % 2 == 1 && hp <= 0.02f) {
            RenderUtils.drawRoundedRect(ms, colX + 4f, ry + 0.5f, colW - 8f, ROW_H - 1f, 6f,
                    ColorUtils.rgba(255, 255, 255, (int) (5 * vp)));
        }

        if (hp > 0.02f) {
            RenderUtils.drawRoundedRect(ms, colX + 4f, ry + 0.5f, colW - 8f, ROW_H - 1f, 6f,
                    ColorUtils.rgba(255, 255, 255, (int) (14 * hp * vp)));
        }

        // Акцентная риска слева у включённого модуля
        float enBar = Math.min(1f, enP);
        if (enBar > 0.03f) {
            float barH = (ROW_H - 10f) * enBar;
            RenderUtils.drawRoundedRect(ms, colX + 6.2f, ry + (ROW_H - barH) / 2f, 1.8f, barH, 0.9f,
                    ColorUtils.applyAlpha(ac, 0.9f * enBar * vp));
        }

        // Имя по центру ПРОМЕЖУТКА между чипом бинда и тумблером — не залезает ни на что
        float leftReserve = 44f;
        float rightReserve = PAD + 4f + ToggleSwitch.W + 16f;
        float nameAvail = colW - leftReserve - rightReserve;
        String name = fitText(m.getName(), 13, nameAvail);
        float nameW = tw(13, name);
        float nameX = colX + leftReserve + (nameAvail - nameW) / 2f;
        int nameBase = ColorUtils.rgba(205, 211, 224, (int) (225 * vp));
        int nameOn = ColorUtils.interpolateColor(nameBase,
                ColorUtils.rgba(243, 246, 252, (int) (245 * vp)), Math.min(1f, enBar * 0.8f));
        int nameCol = ColorUtils.interpolateColor(nameOn,
                ColorUtils.rgba(235, 240, 248, (int) (248 * vp)), hp);
        text(ms, 13, name, nameX, ry + ROW_H / 2f - fh(13) / 2f, nameCol);

        // Стрелка-индикатор наличия настроек (ПКМ)
        if (hasSettings(m)) {
            AnimationUtils rotA = anim(m.toString() + "_arr", 10f, Easings.CUBIC_OUT);
            rotA.update(expanded.contains(m) ? 1f : 0f);
            float rp = MathHelper.clamp(rotA.getValue(), 0f, 1f);
            float ax = colX + colW - PAD - 4f - ToggleSwitch.W - 14f;
            float ay = ry + ROW_H / 2f;
            ms.push();
            ms.translate(ax, ay, 0f);
            ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-90f * rp));
            int arrCol = ColorUtils.rgba(150, 157, 172, (int) ((150 + 60 * rp) * vp));
            text(ms, 10, "›", -fh(10) / 2f - 1f, -fh(10) / 2f, arrCol);
            ms.pop();
        }

        // Бинд-чип слева: клик — перебинд
        boolean isListening = ClickGuiScreen.listeningModule == m;
        float swX = colX + colW - PAD - 4f - ToggleSwitch.W;
        float swY = ry + ROW_H / 2f - ToggleSwitch.H / 2f;
        if (isListening) {
            float lw = tw(10, "[...]") + 12;
            float lh = 14;
            float lx = colX + 10f;
            float ly = ry + ROW_H / 2f - lh / 2f;
            RenderUtils.drawRoundedRect(ms, lx, ly, lw, lh, lh / 2f,
                    ColorUtils.applyAlpha(ac, 0.3f * vp));
            text(ms, 10, "[...]", lx + 6, ry + ROW_H / 2f - fh(10) / 2f,
                    ColorUtils.applyAlpha(ac, 0.9f * vp));
        } else if (m.getKey() != -1) {
            String kn = KeyBoardUtils.getBindName(m.getKey());
            float kw = tw(10, kn) + 12;
            float kh = 14;
            float kx = colX + 10f;
            float ky = ry + ROW_H / 2f - kh / 2f;
            RenderUtils.drawRoundedRect(ms, kx, ky, kw, kh, kh / 2f,
                    ColorUtils.rgba(255, 255, 255, (int) (20 * vp)));
            text(ms, 10, kn, kx + 6, ry + ROW_H / 2f - fh(10) / 2f,
                    ColorUtils.rgba(200, 206, 219, (int) (210 * vp)));
        }

        ToggleSwitch.draw(ms, swX, swY, enC, vp, ac);

        // Мягкая вспышка строки при переключении (плавно гаснет, без колец)
        Long tgAt = lastToggleAt.get(m);
        if (tgAt != null) {
            float age = (System.currentTimeMillis() - tgAt) / 420f;
            if (age < 1f) {
                float fl = (1f - age) * (1f - age);
                RenderUtils.drawRoundedRect(ms, colX + 4f, ry + 0.5f, colW - 8f, ROW_H - 1f, 6f,
                        ColorUtils.applyAlpha(ac, 0.16f * fl * vp));
            } else {
                lastToggleAt.remove(m);
            }
        }

        if (exP > 0.01f && hasSettings(m)) {
            renderSettings(ms, m, ry + ROW_H, exP, mouseX, mouseY, ac, winP, dt);
        }
    }

    private void renderSettings(MatrixStack ms, Module m, float sy, float exP,
                                int mouseX, int mouseY, int ac, float winP, float dt) {
        float sh = settingsHeight(m, colW) * exP;
        RenderUtils.drawRoundedRect(ms, insetX(), sy, insetW(), sh, 6f,
                ColorUtils.rgba(2, 5, 12, (int) (72 * exP)));

        float ix = insetX() + 6;
        float iw = insetW() - 12;
        float cy = sy + 5;

        int rowIdx = 0;
        for (Setting<?> s : m.getSettings()) {
            if (s == null || !s.isVisible()) continue;
            float rowA = MathHelper.clamp(exP * 1.7f - rowIdx * 0.09f, 0f, 1f);
            float ry = cy + (1f - rowA) * 4f;

            if (s instanceof BooleanSetting b) {
                drawBoolRow(ms, b, ix, ry, iw, BOOL_H, rowA, ac, winP, mouseX, mouseY);
                cy += BOOL_H;
            } else if (s instanceof FloatSetting num) {
                drawSliderRow(ms, num, ix, ry, iw, SLIDER_H, rowA, ac, winP, mouseX, mouseY);
                cy += SLIDER_H;
            } else if (s instanceof TextSetting ts) {
                drawTextRow(ms, ts, ix, ry, iw, TEXT_H, rowA, ac, winP);
                cy += TEXT_H;
            } else if (s instanceof BindSetting bind) {
                drawBindRow(ms, bind, ix, ry, iw, BIND_H, rowA, ac, winP, mouseX, mouseY);
                cy += BIND_H;
            } else if (s instanceof ModeSetting mode) {
                cy += drawModeRow(ms, mode, ix, ry, iw, rowA, ac, winP, dt, mouseX, mouseY);
            } else if (s instanceof ListSetting list) {
                text(ms, 11, list.name(), ix, ry, ColorUtils.rgba(214, 219, 230, (int) (215 * rowA * winP)));
                cy += LIST_HEAD_H;
                for (BooleanSetting child : list.getSettings()) {
                    float childY = cy + (1f - rowA) * 4f;
                    drawMiniBoolRow(ms, child, ix, childY, iw, LIST_CHILD_H, rowA, ac, winP, mouseX, mouseY);
                    cy += LIST_CHILD_H;
                }
            }
            rowIdx++;
        }
    }

    private void drawBoolRow(MatrixStack ms, BooleanSetting b, float x0, float y0, float w0, float h0,
                             float a, int ac, float winP, int mouseX, int mouseY) {
        boolean on = b.isState();
        float cp = on ? 1f : 0f;

        float maxTw = w0 - CHECKBOX_SIZE - 8f;
        text(ms, 11, fitText(b.name(), 11, maxTw), x0, y0 + h0 / 2f - fh(11) / 2f,
                ColorUtils.rgba(b.isState() ? 238 : 198, b.isState() ? 241 : 204, b.isState() ? 249 : 216,
                        (int) ((b.isState() ? 240 : 190) * a * winP)));

        boolean hov = HoveringUtils.isHovered(mouseX, mouseY, x0 + w0 - CHECKBOX_SIZE - 4, y0, CHECKBOX_SIZE + 8, h0);
        drawCheckbox(ms, b.isState(), cp, x0 + w0 - CHECKBOX_SIZE, y0 + (h0 - CHECKBOX_SIZE) / 2f, a * winP, ac, hov);
    }

    private void drawMiniBoolRow(MatrixStack ms, BooleanSetting b, float x0, float y0, float w0, float h0,
                                 float a, int ac, float winP, int mouseX, int mouseY) {
        boolean on = b.isState();
        float cp = on ? 1f : 0f;

        float maxTw = w0 - CHECKBOX_SIZE - 10f;
        text(ms, 10, fitText(b.name(), 10, maxTw), x0 + 4, y0 + h0 / 2f - fh(10) / 2f,
                ColorUtils.rgba(205, 210, 222, (int) ((b.isState() ? 225 : 165) * a * winP)));

        boolean hov = HoveringUtils.isHovered(mouseX, mouseY, x0 + w0 - CHECKBOX_SIZE - 4, y0, CHECKBOX_SIZE + 8, h0);
        drawCheckbox(ms, b.isState(), cp, x0 + w0 - CHECKBOX_SIZE, y0 + (h0 - CHECKBOX_SIZE) / 2f, a * winP, ac, hov);
    }

    /** Строка бинда: имя слева, текущая клавиша справа; клик — режим прослушки клавиши. */
    private void drawBindRow(MatrixStack ms, BindSetting bind, float x0, float y0, float w0, float h0,
                             float a, int ac, float winP, int mouseX, int mouseY) {
        boolean listening = ClickGuiScreen.listeningBind == bind;
        boolean hov = HoveringUtils.isHovered(mouseX, mouseY, x0, y0, w0, h0);

        text(ms, 11, fitText(bind.name(), 11, w0 * 0.6f), x0, y0 + h0 / 2f - fh(11) / 2f,
                ColorUtils.rgba(205, 210, 222, (int) (195 * a * winP)));

        String kn = listening ? "[...]" : KeyBoardUtils.getBindName(bind.getKey());
        int kCol = listening
                ? ColorUtils.applyAlpha(ac, 0.95f)
                : ColorUtils.rgba(bind.getKey() != -1 ? 236 : 150,
                                  bind.getKey() != -1 ? 240 : 156,
                                  bind.getKey() != -1 ? 252 : 170,
                                  (int) ((hov ? 245 : 195) * a * winP));
        text(ms, 11, kn, x0 + w0 - tw(11, kn) - 2f, y0 + h0 / 2f - fh(11) / 2f, kCol);
    }

    /** Обрезает текст с многоточием под заданную ширину. */
    private String fitText(String s, int size, float maxW) {
        if (maxW <= 8f) return "...";
        if (tw(size, s) <= maxW) return s;
        String cut = s;
        while (cut.length() > 1 && tw(size, cut + "...") > maxW) {
            cut = cut.substring(0, cut.length() - 2);
        }
        return cut + "...";
    }

    /** Чекбокс: включённое состояние — закрашивание акцентом вместо галочки. */
    private void drawCheckbox(MatrixStack ms, boolean on, float cp, float bx, float by, float a, int ac, boolean hov) {
        if (a < 0.01f) return;

        RenderUtils.drawRoundedRect(ms, bx, by + 0.4f, CHECKBOX_SIZE, CHECKBOX_SIZE, 4f,
                ColorUtils.rgba(0, 0, 0, (int) (60 * a)));
        RenderUtils.drawRoundedRect(ms, bx, by, CHECKBOX_SIZE, CHECKBOX_SIZE, 4f,
                ColorUtils.rgba(30, 35, 42, (int) (220 * a)));

        if (cp > 0.01f) {
            float inset = 2f;
            float inner = CHECKBOX_SIZE - inset * 2f;
            float grow = 0.45f + 0.55f * cp;
            float iw = inner * grow;
            float ix = bx + (CHECKBOX_SIZE - iw) / 2f;
            float iy = by + (CHECKBOX_SIZE - iw) / 2f;
            RenderUtils.drawRoundedRect(ms, ix, iy, iw, iw, iw / 4f,
                    ColorUtils.applyAlpha(ac, 0.92f * cp * a));
        }

        int borderBrightOn = (int) ((hov ? 200 : 150) * a);
        int borderDarkOn = (int) (110 * a);
        RenderUtils.drawRoundedRectOutline(ms, bx, by, CHECKBOX_SIZE, CHECKBOX_SIZE, 4f, 1f,
                ColorUtils.rgba(255, 255, 255, borderBrightOn),
                ColorUtils.rgba(255, 255, 255, borderBrightOn),
                ColorUtils.rgba(255, 255, 255, borderDarkOn),
                ColorUtils.rgba(255, 255, 255, borderDarkOn));
    }

    private void drawSliderRow(MatrixStack ms, FloatSetting num, float x0, float y0, float w0, float h0,
                               float a, int ac, float winP, int mouseX, int mouseY) {
        AnimationUtils va = sliderValues.computeIfAbsent(num, k -> new AnimationUtils(num.get(), 10f, Easings.CUBIC_OUT));
        va.update(num.get());
        float shown = MathHelper.clamp(va.getValue(), num.getMin(), num.getMax());

        AnimationUtils ha = anim(num, 13f, Easings.CUBIC_OUT);
        boolean hot = ClickGuiScreen.draggingSlider == num
                || HoveringUtils.isHovered(mouseX, mouseY, x0, y0, w0, h0);
        ha.update(hot ? 1f : 0f);
        float hp = MathHelper.clamp(ha.getValue(), 0f, 1f);

        text(ms, 11, num.name(), x0, y0, ColorUtils.rgba(222, 226, 236, (int) (228 * a * winP)));
        String val = formatValue(num, shown);
        text(ms, 10, val, x0 + w0 - tw(10, val), y0 + fh(11) - fh(10), ColorUtils.applyAlpha(ac, 0.92f * a * winP));

        float trackY = y0 + h0 - 10;
        float trackH = 3.5f;
        RenderUtils.drawRoundedRect(ms, x0, trackY, w0, trackH, trackH / 2f,
                ColorUtils.rgba(255, 255, 255, (int) (22 * a * winP)));
        float frac = (shown - num.getMin()) / Math.max(0.0001f, num.getMax() - num.getMin());
        float fw = w0 * MathHelper.clamp(frac, 0f, 1f);
        if (fw > 0.5f) {
            int fillL = ColorUtils.applyAlpha(ac, 0.60f * a * winP);
            int fillR = ColorUtils.applyAlpha(ColorUtils.interpolateColor(ac, 0xFFFFFFFF, 0.22f), 0.95f * a * winP);
            RenderUtils.drawGradientRect(ms, x0, trackY, fw, trackH, trackH / 2f, fillL, fillR, true);
        }
        float knobR = 3.4f + 1.0f * hp;
        float knobCx = x0 + fw;
        float knobCy = trackY + trackH / 2f;
        RenderUtils.drawRoundCircle(ms, knobCx, knobCy + 0.4f, knobR,
                ColorUtils.rgba(0, 0, 0, (int) (30 * a * winP)));
        if (hp > 0.04f) {
            RenderUtils.drawRoundCircle(ms, knobCx, knobCy, knobR + 1.0f,
                    ColorUtils.applyAlpha(ac, 0.22f * hp * a * winP));
        }
        RenderUtils.drawRoundCircle(ms, knobCx, knobCy, knobR,
                ColorUtils.rgba(250, 251, 253, (int) (255 * a * winP)));

        float bp = hot ? 1f : hp * 0.9f;
        if (bp > 0.04f) {
            String bVal = formatValue(num, num.get());
            float bw3 = tw(10, bVal) + 12;
            float bh3 = 13;
            float bcx = MathHelper.clamp(x0 + fw, x0 + bw3 / 2f, x0 + w0 - bw3 / 2f);
            float bx3 = bcx - bw3 / 2f;
            float by3 = trackY - 15;
            RenderUtils.drawShadow(ms, bx3, by3, bw3, bh3, 4f, 6f,
                    ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.35f * bp));
            RenderUtils.drawRoundedRect(ms, bx3, by3, bw3, bh3, 4f,
                    ColorUtils.rgba(6, 9, 17, (int) (238 * bp)));
            RenderUtils.drawRoundedRectOutline(ms, bx3, by3, bw3, bh3, 4f, 1f,
                    ColorUtils.applyAlpha(ac, 0.75f * bp), ColorUtils.applyAlpha(ac, 0.75f * bp),
                    ColorUtils.applyAlpha(ac, 0.4f * bp), ColorUtils.applyAlpha(ac, 0.4f * bp));
            text(ms, 10, bVal, bcx - tw(10, bVal) / 2f, by3 + bh3 / 2f - fh(10) / 2f,
                    ColorUtils.rgba(240, 244, 252, (int) (245 * bp)));
        }
    }

    private void drawTextRow(MatrixStack ms, TextSetting ts, float x0, float y0, float w0, float h0,
                             float a, int ac, float winP) {
        text(ms, 10, ts.name(), x0, y0, ColorUtils.rgba(222, 226, 236, (int) (228 * a * winP)));

        AnimationUtils fa = anim(ts, 13f, Easings.CUBIC_OUT);
        fa.update(ClickGuiScreen.activeTextSetting == ts ? 1f : 0f);
        float fp = MathHelper.clamp(fa.getValue(), 0f, 1f);

        float bh = 15;
        float by = y0 + 11;
        RenderUtils.drawRoundedRect(ms, x0, by, w0, bh, 4f,
                ColorUtils.rgba(255, 255, 255, (int) ((8 + 7 * fp) * a * winP)));
        boolean focused = ClickGuiScreen.activeTextSetting == ts;
        int borderA = Math.max(6, (int) ((20 + 70 * fp) * a));
        int borderColor = focused ? ColorUtils.setAlphaColor(ac, borderA) : ColorUtils.rgba(255, 255, 255, borderA);
        RenderUtils.drawRoundedRectOutline(ms, x0, by, w0, bh, 4f, 1f,
                borderColor, borderColor, borderColor, borderColor);

        String display = ts.get().isEmpty() ? "" : ts.get();
        text(ms, 10, display, x0 + 6, by + bh / 2f - fh(10) / 2f,
                ts.get().isEmpty()
                        ? ColorUtils.rgba(140, 146, 160, (int) (150 * a * winP))
                        : ColorUtils.rgba(236, 239, 246, (int) (240 * a * winP)));

        if (ClickGuiScreen.activeTextSetting == ts && (System.currentTimeMillis() / 500) % 2 == 0) {
            float caretX = x0 + 6 + tw(10, display) + 1;
            RenderUtils.drawRoundedRect(ms, caretX, by + 3f, 1f, bh - 6f, 0.5f,
                    ColorUtils.rgba(236, 239, 246, (int) (210 * a * winP)));
        }
    }

    private float drawModeRow(MatrixStack ms, ModeSetting mode, float x0, float y0, float w0,
                              float a, int ac, float winP, float dt, int mouseX, int mouseY) {
        text(ms, 11, mode.name(), x0, y0, ColorUtils.rgba(222, 226, 236, (int) (228 * a * winP)));

        List<float[]> layout = pillLayout(mode, w0);

        float[] glide = modeGlide.computeIfAbsent(mode, k -> new float[5]);
        int curIdx = -1;
        for (int ti = 0; ti < mode.getModes().length; ti++) {
            if (mode.getModes()[ti].equals(mode.getCurrent())) {
                curIdx = ti;
                break;
            }
        }
        if (curIdx >= 0) {
            float[] target = layout.get(curIdx);
            float tx = x0 + target[0];
            float tw2 = target[2];
            float ty = y0 + MODE_LABEL_H + target[1];
            float gk = 1f - (float) Math.exp(-dt * 18.0);
            if (glide[4] != curIdx) {
                glide[0] = tx;
                glide[2] = tw2;
                glide[3] = MODE_PILL_H;
                glide[4] = curIdx;
            } else {
                glide[0] += (tx - glide[0]) * gk;
                glide[2] += (tw2 - glide[2]) * gk;
                glide[3] += ((float) MODE_PILL_H - glide[3]) * gk;
            }
            float[] pulse = modePulse.computeIfAbsent(mode, k -> new float[]{0f, -1f});
            pulse[0] += dt;
            if ((int) pulse[1] != curIdx) {
                pulse[0] = 0f;
                pulse[1] = curIdx;
            }
            float pulseT = Math.min(1f, pulse[0] / 0.45f);
            float pulseFade = 1f - pulseT;
            float pulseScale = 1f + 0.18f * pulseFade;
            float pxG = glide[0] - (glide[2] * pulseScale - glide[2]) / 2f;
            float pyG = ty - (glide[3] * pulseScale - glide[3]) / 2f;
            float pwG = glide[2] * pulseScale;
            float phG = glide[3] * pulseScale;
            if (a > 0.02f) {
                if (pulseFade > 0.02f) {
                    RenderUtils.drawRoundedRect(ms, pxG, pyG, pwG, phG, 8f,
                            ColorUtils.applyAlpha(ac, (int) (90 * pulseFade * a * winP)));
                }
                RenderUtils.drawRoundedRect(ms, glide[0], ty, glide[2], glide[3], 6f,
                        ColorUtils.applyAlpha(ac, (int) (230 * a * winP)));
            }
        }

        int idx = 0;
        for (String mdn : mode.getModes()) {
            float[] pb = layout.get(idx);
            float px = x0 + pb[0];
            float py = y0 + MODE_LABEL_H + pb[1];
            float pw = pb[2];
            boolean active = mdn.equals(mode.getCurrent());
            boolean hov = HoveringUtils.isHovered(mouseX, mouseY, px, py, pw, MODE_PILL_H);

            AnimationUtils hA = anim(mode.toString() + "_p" + idx + "_h", 12f, Easings.CUBIC_OUT);
            hA.update(hov ? 1f : 0f);
            float hp = MathHelper.clamp(hA.getValue(), 0f, 1f);
            AnimationUtils sA = anim(mode.toString() + "_p" + idx + "_s", 12f, Easings.BACK_OUT);
            sA.update(active ? 1f : 0f);
            float sp = MathHelper.clamp(sA.getValue(), 0f, 1f);

            float scale = 1f + 0.05f * sp;
            float pwS = pw * scale;
            float pxS = px + (pw - pwS) / 2f;

            if (!active) {
                RenderUtils.drawRoundedRect(ms, pxS, py, pwS, MODE_PILL_H, 6f,
                        ColorUtils.rgba(255, 255, 255, (int) ((8 + 8 * hp) * a * winP)));
            }
            float textX = px + (pw - tw(11, mdn)) / 2f;
            text(ms, 11, mdn, textX, py + MODE_PILL_H / 2f - fh(11) / 2f,
                    active
                            ? ColorUtils.rgba(255, 255, 255, (int) (255 * a * winP))
                            : ColorUtils.rgba((int) (196 + 34 * hp), (int) (202 + 26 * hp), (int) (214 + 22 * hp), (int) (205 * a * winP)));
            idx++;
        }

        return MODE_LABEL_H + countLines(layout) * MODE_LINE_H + MODE_PAD_B;
    }

    private String formatValue(FloatSetting num, float value) {
        float step = num.getStep();
        if (step >= 1f) return String.valueOf(Math.round(value));
        int dec = step >= 0.1f ? 1 : (step >= 0.01f ? 2 : 3);
        return String.format(Locale.US, "%." + dec + "f", value);
    }

    // ===== Клики =====

    void handleClick(int button, int mx, int my, Module.ModuleCategory category) {
        List<Module> mods = visibleModules(category);
        if (mods.isEmpty()) return;

        float cw = columnWidth();
        int mid = (mods.size() + 1) / 2;
        boolean rightCol = mx > x + w / 2f;
        List<Module> col = rightCol ? mods.subList(Math.min(mid, mods.size()), mods.size()) : mods.subList(0, mid);
        if (col.isEmpty()) return;

        this.colX = rightCol ? x + cw + COL_GAP : x;
        this.colW = cw;

        float exGate = 0.85f;
        float cy = y + 4 - scrollCurrent;
        for (Module m : col) {
            float rowH = moduleFullHeight(m, cw);

            if (my >= cy && my <= cy + ROW_H && mx >= colX && mx <= colX + colW) {
                float swX = colX + colW - PAD - 4f - ToggleSwitch.W;
                float swY = cy + ROW_H / 2f - ToggleSwitch.H / 2f;

                if (button == 0) {
                    if (m.getKey() != -1 && ClickGuiScreen.listeningModule != m) {
                        String kn = KeyBoardUtils.getBindName(m.getKey());
                        float kw = tw(10, kn) + 12;
                        float kx = colX + 10f;
                        float ky = cy + ROW_H / 2f - 7f;
                        if (HoveringUtils.isHovered(mx, my, kx, ky, kw, 14f)) {
                            ClickGuiScreen.listeningModule = m;
                            return;
                        }
                    }
                    if (ToggleSwitch.hit(mx, my, swX, swY)
                            || HoveringUtils.isHovered(mx, my, colX + 4f, cy, colW - 8f, ROW_H)) {
                        m.toggle();
                        lastToggleAt.put(m, System.currentTimeMillis());
                    }
                } else if (button == 1 && hasSettings(m)) {
                    if (!expanded.add(m)) expanded.remove(m);
                } else if (button == 2) {
                    ClickGuiScreen.listeningModule = m;
                }
                return;
            }

            if (expanded.contains(m) && my > cy + ROW_H && my <= cy + rowH) {
                if (MathHelper.clamp(anim(m.toString() + "_ex", 10f, Easings.CUBIC_OUT).getValue(), 0f, 1f) < exGate) {
                    return;
                }
                if (handleSettingClick(button, m, mx, my, cy + ROW_H)) return;
                return;
            }

            cy += rowH + rowGapF(m);
        }
    }

    private boolean handleSettingClick(int button, Module m, int mx, int my, float startCy) {
        float ix = insetX() + 6;
        float iw = insetW() - 12;
        float cy = startCy + 5;

        for (Setting<?> s : m.getSettings()) {
            if (s == null || !s.isVisible()) continue;

            if (s instanceof BooleanSetting b) {
                float cbX = ix + iw - CHECKBOX_SIZE;
                float cbY = cy + (BOOL_H - CHECKBOX_SIZE) / 2f;
                if (button == 0 && (HoveringUtils.isHovered(mx, my, cbX, cbY, CHECKBOX_SIZE, CHECKBOX_SIZE)
                        || HoveringUtils.isHovered(mx, my, ix, cy, iw - CHECKBOX_SIZE - 4, BOOL_H))) {
                    b.setState(!b.isState());
                    return true;
                }
                cy += BOOL_H;
            } else if (s instanceof FloatSetting num) {
                // Клик в любом месте строки слайдера начинает перетаскивание
                if (button == 0 && HoveringUtils.isHovered(mx, my, ix, cy, iw, SLIDER_H)) {
                    ClickGuiScreen.draggingSlider = num;
                    ClickGuiScreen.dragTrackX = ix;
                    ClickGuiScreen.dragTrackW = iw;
                    applySliderAt(num, mx, ix, iw);
                    return true;
                }
                cy += SLIDER_H;
            } else if (s instanceof TextSetting ts) {
                if (button == 0 && HoveringUtils.isHovered(mx, my, ix, cy, iw, TEXT_H)) {
                    ClickGuiScreen.activeTextSetting = ts;
                    return true;
                }
                cy += TEXT_H;
            } else if (s instanceof BindSetting bind) {
                if (button == 0 && HoveringUtils.isHovered(mx, my, ix, cy, iw, BIND_H)) {
                    ClickGuiScreen.listeningBind = bind;
                    return true;
                }
                cy += BIND_H;
            } else if (s instanceof ModeSetting mode) {
                List<float[]> layout = pillLayout(mode, insetW() - 12);
                for (int pi = 0; pi < mode.getModes().length; pi++) {
                    float[] pb = layout.get(pi);
                    float px = ix + pb[0];
                    float py = cy + MODE_LABEL_H + pb[1];
                    if (HoveringUtils.isHovered(mx, my, px, py, pb[2], MODE_PILL_H)) {
                        mode.set(mode.getModes()[pi]);
                        return true;
                    }
                }
                cy += MODE_LABEL_H + countLines(layout) * MODE_LINE_H + MODE_PAD_B;
            } else if (s instanceof ListSetting list) {
                cy += LIST_HEAD_H;
                for (BooleanSetting child : list.getSettings()) {
                    float cbX = ix + iw - CHECKBOX_SIZE;
                    float cbY = cy + (LIST_CHILD_H - CHECKBOX_SIZE) / 2f;
                    if (button == 0 && (HoveringUtils.isHovered(mx, my, cbX, cbY, CHECKBOX_SIZE, CHECKBOX_SIZE)
                            || HoveringUtils.isHovered(mx, my, ix, cy, iw - CHECKBOX_SIZE - 4, LIST_CHILD_H))) {
                        child.setState(!child.isState());
                        return true;
                    }
                    cy += LIST_CHILD_H;
                }
            }
        }
        return false;
    }

    private void applySliderAt(FloatSetting num, int mouseX, float trackX, float trackW) {
        float frac = MathHelper.clamp((mouseX - trackX) / Math.max(1f, trackW), 0f, 1f);
        float raw = num.getMin() + (num.getMax() - num.getMin()) * frac;
        float snapped = Math.round(raw / num.getStep()) * num.getStep();
        num.setValue(snapped);
    }
}
