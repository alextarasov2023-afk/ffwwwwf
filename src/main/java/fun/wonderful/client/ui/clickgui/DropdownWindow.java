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

class DropdownWindow {

    static final int WIN_W = 120; // Широкие столбики в стиле CS GUI
    static final int HEADER_H = 32;
    static final int PAD = 7;
    static final int ROW_H = 25;
    static final int ROW_GAP = 3;
    private static final int INSET_W = 94; // Широкий блок настроек (CS GUI стиль)
    private static final int INSET_X = (WIN_W - INSET_W) / 2; // блок настроек центрируется по ширине окна
    // Стандартная ширина содержимого: INSET_W - 12 (запас для заголовка и отступов)

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

    final Module.ModuleCategory category;
    float x, y;
    boolean open;
    long openAtNanos;
    float screenW, screenH;

    float scrollTarget, scrollCurrent;

    final AnimationUtils openAnim = new AnimationUtils(0f, 6f, Easings.BACK_OUT);
    final AnimationUtils scrollVis = new AnimationUtils(0f, 11f, Easings.CUBIC_OUT);

    private final Map<Object, AnimationUtils> animPool = new HashMap<>();
    private final Map<FloatSetting, AnimationUtils> sliderValues = new HashMap<>();
    private final Map<ModeSetting, float[]> modeGlide = new HashMap<>();
    private final Map<ModeSetting, float[]> modePulse = new HashMap<>(); // [time, lastIdx]
    private final Set<Module> expanded = new HashSet<>();

    private Module hoverModule;
    private long hoverSince;

    DropdownWindow(Module.ModuleCategory category, int index) {
        this.category = category;
    }

    private AnimationUtils anim(Object key, float speed, Easing easing) {
        return animPool.computeIfAbsent(key, k -> new AnimationUtils(0f, speed, easing));
    }

    private static Font f(int size) {
        return Fonts.getFont("suisse", size);
    }

    static void text(MatrixStack ms, int size, String s, float px, float py, int color) {
        Font fo = f(size);
        // py — верх заглавных букв (капс). Font.draw смещает y на -1.5 и рендерит в half-size:
        // капс-верх = inputY - 1.5 + (baselineHeight - capHeight) * 0.5 * size = inputY - 1.5 + 0.0792*size
        if (fo != null && s != null && !s.isEmpty()) fo.draw(ms, s, px, py + 1.5f - 0.0792f * size, color);
    }

    static float tw(int size, String s) {
        Font fo = f(size);
        return fo == null ? 0 : fo.getWidth(s);
    }

    static float fh(int size) {
        // Визуальная (капсная) высота букв: capHeight 0.8047em × half-size рендер.
        // Центрирование "yc - fh(size)/2" теперь ставит текст ровно по центру строки.
        return size * 0.4023f;
    }

    void updateOpenState(long now, boolean closing) {
        boolean allowed = !closing && now >= openAtNanos;
        openAnim.update(open && allowed ? 1f : 0f);
    }

    private float openP() {
        return MathHelper.clamp(openAnim.getValue(), 0f, 1f);
    }

    private List<Module> modules() {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.getCategory() == category) out.add(m);
        }
        return out;
    }

    private boolean hasSettings(Module m) {
        for (Setting<?> s : m.getSettings()) {
            if (s != null && s.isVisible()) return true;
        }
        return false;
    }

    private int pillLines(ModeSetting mode) {
        return countLines(pillLayout(mode));
    }

    private int countLines(List<float[]> layout) {
        if (layout.isEmpty()) return 1;
        float lastY = layout.get(layout.size() - 1)[1];
        return Math.max(1, Math.round(lastY / (float) MODE_LINE_H) + 1);
    }

    private List<float[]> pillLayout(ModeSetting mode) {
        List<float[]> out = new ArrayList<>();
        float maxW = INSET_W - 10;
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

    private int settingsHeight(Module m) {
        int h = SETTINGS_TOP_PAD;
        for (Setting<?> s : m.getSettings()) {
            if (s == null || !s.isVisible()) continue;
            if (s instanceof BooleanSetting) h += BOOL_H;
            else if (s instanceof FloatSetting) h += SLIDER_H;
            else if (s instanceof TextSetting) h += TEXT_H;
            else if (s instanceof BindSetting) h += BIND_H;
            else if (s instanceof ModeSetting mode) h += MODE_LABEL_H + pillLines(mode) * MODE_LINE_H + MODE_PAD_B;
            else if (s instanceof ListSetting list) h += LIST_HEAD_H + list.getSettings().size() * LIST_CHILD_H;
        }
        return h;
    }

    private float moduleFullHeight(Module m) {
        float ex = MathHelper.clamp(anim(m.toString() + "_ex", 10f, Easings.CUBIC_OUT).getValue(), 0f, 1f);
        int sh = hasSettings(m) ? settingsHeight(m) : 0;
        return (ROW_H + sh * ex) * fltP(m);
    }

    /** Прогресс схлопывания строки при поиске */
    private float fltP(Module m) {
        return MathHelper.clamp(anim(m.toString() + "_flt", 10f, Easings.CUBIC_OUT).getValue(), 0f, 1f);
    }

    /** Промежуток после строки, тоже анимированно сжимается при фильтрации */
    private float rowGapF(Module m) {
        return ROW_GAP * fltP(m);
    }

    private float fullBodyHeight() {
        float h = 4;
        for (Module m : modules()) h += moduleFullHeight(m) + rowGapF(m);
        return h;
    }

    /** Оценка полной высоты окна (для начальной раскладки без перекрытий).
     *  Не зависит от анимаций: считается из фактического числа строк. */
    float estimatedWindowHeight(float avail) {
        float rows = 4f;
        for (Module m : modules()) rows += ROW_H + ROW_GAP;
        return HEADER_H + Math.min(rows, Math.max(24f, avail)) + 8f;
    }

    private float viewHeight() {
        float avail = screenH - (y + HEADER_H) - 8;
        // Окна не растягиваются на весь экран — появляется внутренний скролл
        float cap = Math.max(120f, screenH * 0.62f);
        return Math.max(24f, Math.min(fullBodyHeight(), Math.min(avail, cap)));
    }

    boolean hitAny(int mx, int my) {
        return hitHeader(mx, my) || hitBody(mx, my);
    }

    boolean hitHeader(int mx, int my) {
        if (openP() < 0.25f) return false;
        return HoveringUtils.isHovered(mx, my, x, y, WIN_W, HEADER_H);
    }

    boolean hitBody(int mx, int my) {
        float p = openP();
        if (p < 0.6f) return false;
        return HoveringUtils.isHovered(mx, my, x, y + HEADER_H, WIN_W, viewHeight() * p);
    }

    private boolean matches(Module m) {
        // Фильтр применяется только когда поле поиска активно (кликнуто).
        // Иначе модули не схлопываются — «тестовых модулей больше нет» пользователем.
        if (!ClickGuiScreen.filterActive) return true;
        String q = ClickGuiScreen.filter.trim().toLowerCase();
        return q.isEmpty() || m.getName().toLowerCase().contains(q);
    }

    Module tooltipCandidate() {
        if (openP() < 0.7f) return null;
        if (hoverModule != null
                && System.currentTimeMillis() - hoverSince > 350
                && matches(hoverModule)) {
            return hoverModule;
        }
        return null;
    }

    void render(MatrixStack ms, ClickGuiScreen gui, int mouseX, int mouseY, float dt) {
        this.screenW = gui.width;
        this.screenH = gui.height;

        float p = openP();
        if (p <= 0.01f) return;

        // Окно плавно "опускается" сверху при открытии
        float slideT = 1f - p;
        float wy = y - slideT * slideT * 14f;

        int ac = ClickGuiScreen.accent();

        float bodyView = viewHeight();
        float bodyDisp = bodyView * p;
        float totalH = HEADER_H + bodyDisp + 4f * p;

        float bodyFull = fullBodyHeight();
        float maxScroll = Math.max(0f, bodyFull - bodyView);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll);
        float sk = 1f - (float) Math.exp(-dt * 16.0);
        scrollCurrent += (scrollTarget - scrollCurrent) * sk;
        if (Math.abs(scrollTarget - scrollCurrent) < 0.05f) scrollCurrent = scrollTarget;

        RenderUtils.drawShadow(ms, x, wy, WIN_W, totalH, 11f,
                12f, ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.5f));

        RenderUtils.drawBlur(ms, x, wy, WIN_W, totalH, 11f, 9f, ColorUtils.rgba(7, 11, 21, (int) (180 * p)));

        RenderUtils.drawRoundedRect(ms, x, wy, WIN_W, totalH, 11f,
                ColorUtils.rgba(12, 15, 24, (int) (246 * p)));

        // Обводка окна — градиент по акценту (сверху/снизу)
        int outTop = ColorUtils.applyAlpha(ClickGuiScreen.accentAt(wy + 3f), (int) (165 * p));
        int outBot = ColorUtils.applyAlpha(ClickGuiScreen.accentAt(wy + totalH - 3f), (int) (110 * p));
        RenderUtils.drawRoundedRectOutline(ms, x, wy, WIN_W, totalH, 11f, 1f,
                outTop, outTop,
                outBot, outBot);

        int acH = ClickGuiScreen.accentAt(wy + HEADER_H / 2f);
        renderHeader(ms, mouseX, mouseY, acH, p, wy);

        ScissorUtils.push();
        ScissorUtils.setFromComponentCoordinates(x + 1, wy + HEADER_H, WIN_W - 2,
                Math.max(0, (int) Math.ceil(bodyDisp) - 1));

        float cy = wy + HEADER_H + 4 - scrollCurrent;
        List<Module> mods = modules();
        for (int mi = 0; mi < mods.size(); mi++) {
            Module m = mods.get(mi);
            int acM = ClickGuiScreen.accentAt(cy);
            renderModule(ms, m, cy, mouseX, mouseY, acM, p, dt, mi);
            cy += moduleFullHeight(m) + rowGapF(m);
        }

        ScissorUtils.pop();

        // Скроллбар проявляется только когда нужен: наведение на тело или активный скролл
        boolean sbHot = hitBody(mouseX, mouseY)
                || Math.abs(scrollTarget - scrollCurrent) > 2f;
        scrollVis.update(sbHot && bodyFull > bodyView + 1f ? 1f : 0f);
        float sba = MathHelper.clamp(scrollVis.getValue(), 0f, 1f);
        if (sba > 0.03f && bodyFull > bodyView + 1f) {
            float trackX = x + WIN_W - 5;
            float trackY = wy + HEADER_H + 4;
            float trackH = bodyDisp - 8;
            float thumbH = Math.max(14f, trackH * (trackH / Math.max(1f, bodyFull)));
            thumbH = Math.min(thumbH, trackH);
            float thumbY = trackY + (scrollCurrent / Math.max(1f, maxScroll)) * (trackH - thumbH);
            thumbY = MathHelper.clamp(thumbY, trackY, Math.max(trackY, trackY + trackH - thumbH));
            RenderUtils.drawRoundedRect(ms, trackX, thumbY, 2f, thumbH, 1f,
                    ColorUtils.setAlphaColor(ac, (int) (170 * sba * p)));
        }
    }

    /** Иконки категорий — кастомный GL-рендер (билинейная фильтрация, без пикселей) */
    private void drawCategoryIcon(MatrixStack ms, Module.ModuleCategory cat, float cx, float cy, float size, int color) {
        CategoryIcons.draw(ms, cat, cx - size / 2f, cy - size / 2f, size, color);
    }

        private void renderHeader(MatrixStack ms, int mouseX, int mouseY, int ac, float p, float wy) {
        // Хедер категории — статичный заголовок, НЕ кнопка: убираем hover-подсветку
        // и анимацию смещения, чтобы надпись не «выбеливалась» и не реагировала на мышь.
        float op = MathHelper.clamp(openAnim.getValue(), 0f, 1f);
        float hcy = wy + HEADER_H / 2f;

        // Иконка категории — PNG-текстура, тинтится акцентом GUI
        if (op > 0.02f) {
            int iconCol = ColorUtils.applyAlpha(ColorUtils.interpolateColor(ac, 0xFFFFFFFF, 0.3f), op);
            float isz = 15f;
            drawCategoryIcon(ms, category, x + PAD + 3f + isz / 2f, hcy, isz, iconCol);
        }

        text(ms, 15, category.getName(), x + PAD + 24, hcy - fh(15) / 2f,
                ColorUtils.rgba(242, 244, 250, (int) (250 * op)));

        float uw = (WIN_W - PAD * 2);
        int hdrAccent = ColorUtils.applyAlpha(ClickGuiScreen.accentAt(wy + HEADER_H), (int) (110 * op));
        RenderUtils.drawRoundedRect(ms, x + PAD, wy + HEADER_H - 1f, uw, 1f, 0.5f, hdrAccent);
    }

    private void renderModule(MatrixStack ms, Module m, float ry, int mouseX, int mouseY,
                              int ac, float winP, float dt, int idx) {
        boolean match = matches(m);
        AnimationUtils fltA = anim(m.toString() + "_flt", 10f, Easings.CUBIC_OUT);
        fltA.update(match ? 1f : 0f);
        float flt = MathHelper.clamp(fltA.getValue(), 0f, 1f);

        float vp = winP * (0.15f + 0.85f * flt);

        AnimationUtils tgA = anim(m.toString() + "_tg", 9f, Easings.BACK_OUT);
        tgA.update(m.isEnable() ? 1f : 0f);
        float enP = MathHelper.clamp(tgA.getValue(), 0f, 1.15f);
        float enC = Math.min(1f, enP);

        AnimationUtils exA = anim(m.toString() + "_ex", 10f, Easings.CUBIC_OUT);
        exA.update(expanded.contains(m) ? 1f : 0f);
        float exP = MathHelper.clamp(exA.getValue(), 0f, 1f);

        if (flt <= 0.02f) return;

        // Hover-отслеживание — НУЖНО для тултипов, но ВИЗУАЛЬНОЙ подсветки нет
        boolean listMoving = Math.abs(scrollTarget - scrollCurrent) > 0.2f;
        boolean hov = !listMoving && match && HoveringUtils.isHovered(mouseX, mouseY, x, ry, WIN_W, ROW_H);
        if (hov) {
            hoverModule = m;
            hoverSince = System.currentTimeMillis();
        } else if (hoverModule == m) {
            hoverModule = null;
        }

        // Подсветка включённого модуля убрана — остаётся только ползунок toggle.
        float nameX = x + PAD + 2;
        int nameCol = ColorUtils.rgba(220, 226, 238, (int) (235 * vp));
        text(ms, 13, m.getName(), nameX, ry + ROW_H / 2f - fh(13) / 2f, nameCol);

        float swX = x + WIN_W - PAD - 2f - ToggleSwitch.W;
        float swY = ry + ROW_H / 2f - ToggleSwitch.H / 2f;

        boolean isListening = ClickGuiScreen.listeningModule == m;
        if (isListening) {
            float lw = tw(10, "[...]") + 12;
            float lh = 14;
            float lx = swX - 8 - lw;
            float ly = ry + ROW_H / 2f - lh / 2f;
            RenderUtils.drawRoundedRect(ms, lx, ly, lw, lh, lh / 2f,
                    ColorUtils.applyAlpha(ac, 0.3f));
            text(ms, 10, "[...]", lx + 7, ry + ROW_H / 2f - fh(10) / 2f,
                    ColorUtils.applyAlpha(ac, 0.9f));
        } else if (m.getKey() != -1) {
            String kn = KeyBoardUtils.getBindName(m.getKey());
            float kA = vp;
            if (kA > 0.04f) {
                float kw = tw(10, kn) + 12;
                float kh = 14;
                float kx = swX - 8 - kw;
                float ky = ry + ROW_H / 2f - kh / 2f;
                RenderUtils.drawRoundedRect(ms, kx, ky, kw, kh, kh / 2f,
                        ColorUtils.rgba(255, 255, 255, (int) (20 * kA)));
                text(ms, 10, kn, kx + 7, ry + ROW_H / 2f - fh(10) / 2f,
                        ColorUtils.rgba(200, 206, 219, (int) (210 * kA)));
            }
        }

        ToggleSwitch.draw(ms, swX, swY, enC, vp, ac);

        if (exP > 0.01f && hasSettings(m)) {
            renderSettings(ms, m, ry + ROW_H, exP, mouseX, mouseY, ac, winP, dt);
        }
    }

    private void renderSettings(MatrixStack ms, Module m, float sy, float exP,
                                int mouseX, int mouseY, int ac, float winP, float dt) {
        float sh = settingsHeight(m) * exP;
        RenderUtils.drawRoundedRect(ms, x + INSET_X, sy, INSET_W, sh, 6f,
                ColorUtils.rgba(2, 5, 12, (int) (72 * exP)));

        float ix = x + INSET_X + 6;
        float iw = INSET_W - 12;
        float cy = sy + 5;

        int rowIdx = 0;
        for (Setting<?> s : m.getSettings()) {
            if (s == null || !s.isVisible()) continue;
            float rowA = MathHelper.clamp(exP * 1.7f - rowIdx * 0.09f, 0f, 1f);
            float ry = cy + (1f - rowA) * 4f;

            if (s instanceof BooleanSetting b) {
                int rowAc = ClickGuiScreen.accentAt(ry);
                drawBoolRow(ms, b, ix, ry, iw, BOOL_H, rowA, rowAc, winP, mouseX, mouseY);
                cy += BOOL_H;
            } else if (s instanceof FloatSetting num) {
                int rowAc = ClickGuiScreen.accentAt(ry);
                drawSliderRow(ms, num, ix, ry, iw, SLIDER_H, rowA, rowAc, winP, mouseX, mouseY);
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
                    int rowAc = ClickGuiScreen.accentAt(childY);
                    drawMiniBoolRow(ms, child, ix, childY, iw, LIST_CHILD_H, rowA, rowAc, winP, mouseX, mouseY);
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

    /** Обрезает текст с многоточием, чтобы он не залезал под чекбокс. */
    private String fitText(String s, int size, float maxW) {
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

        // Тень - мягкое освещение снизу
        RenderUtils.drawRoundedRect(ms, bx, by + 0.4f, CHECKBOX_SIZE, CHECKBOX_SIZE, 4f,
                ColorUtils.rgba(0, 0, 0, (int) (60 * a)));

        // Основной фон квадрата (выключенное состояние)
        RenderUtils.drawRoundedRect(ms, bx, by, CHECKBOX_SIZE, CHECKBOX_SIZE, 4f,
                ColorUtils.rgba(30, 35, 42, (int) (220 * a)));

        // Сияние при наведении
        int glowColor = on ? ColorUtils.applyAlpha(ac, 0.15f) : ColorUtils.rgba(255, 255, 255, (int) (20 * a));
        RenderUtils.drawRoundedRect(ms, bx - 1.5f, by - 1.5f, CHECKBOX_SIZE + 3f, CHECKBOX_SIZE + 3f, 5f,
                glowColor);

        // Закрашивание акцентом темы вместо галочки: плавно растёт из центра
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

        // Обводка с градиентным освещением
        int borderBrightOn = (int) (170 * a);
        int borderDarkOn = (int) (120 * a);
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
            RenderUtils.drawRoundedRect(ms, x0, trackY, fw, trackH, trackH / 2f,
                    ColorUtils.applyAlpha(ac, 0.92f * a * winP));
        }
        float knobR = 3.4f + 1.0f * hp;
        float knobCx = x0 + fw;
        float knobCy = trackY + trackH / 2f;
        RenderUtils.drawRoundCircle(ms, knobCx, knobCy + 0.4f, knobR,
                ColorUtils.rgba(0, 0, 0, (int) (30 * a * winP)));
        // Акцентное кольцо вокруг ползунка при наведении/перетаскивании
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

        List<float[]> layout = pillLayout(mode);

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
            // X и ширина анимируются при переключении выбора, а Y всегда строго следует
            // за строкой (ty) — это исключает «сползание» highlight по вертикали при скролле.
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
            // Пульс при смене выбора — короткая волна по высоте/яркости
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
                // Внешний «ореол»-пульс (расширяется и тает)
                if (pulseFade > 0.02f) {
                    RenderUtils.drawRoundedRect(ms, pxG, pyG, pwG, phG, 8f,
                            ColorUtils.applyAlpha(ac, (int) (90 * pulseFade * a * winP)));
                }
                // Основная заливка
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

            // Плавная анимация наведения и «всплытие» выбранной пилюли
            AnimationUtils hA = anim(mode.toString() + "_p" + idx + "_h", 12f, Easings.CUBIC_OUT);
            hA.update(hov ? 1f : 0f);
            float hp = MathHelper.clamp(hA.getValue(), 0f, 1f);
            AnimationUtils sA = anim(mode.toString() + "_p" + idx + "_s", 12f, Easings.BACK_OUT);
            sA.update(active ? 1f : 0f);
            float sp = MathHelper.clamp(sA.getValue(), 0f, 1f);

            // Лёгкое масштабирование активной пилюли поверх глида
            float scale = 1f + 0.05f * sp;
            float pwS = pw * scale;
            float pxS = px + (pw - pwS) / 2f;

            if (!active) {
                RenderUtils.drawRoundedRect(ms, pxS, py, pwS, MODE_PILL_H, 6f,
                        ColorUtils.rgba(255, 255, 255, (int) ((8 + 8 * hp) * a * winP)));
            }
            // Текст центрируется по исходной (немасштабированной) пилюле — иначе при
            // выборе мода надпись «съезжает» вправо вслед за scale/pxS.
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

              void handleClick(int button, int mx, int my) {
        float exGate = 0.85f;

        float cy = y + HEADER_H + 4 - scrollCurrent;
        for (Module m : modules()) {
            float rowH = moduleFullHeight(m);

            if (!matches(m)) {
                cy += rowH + rowGapF(m);
                continue;
            }

            if (my >= cy && my <= cy + ROW_H && mx >= x && mx <= x + WIN_W) {
                if (button == 0) {
                    float swX = x + WIN_W - PAD - 2f - ToggleSwitch.W;
                    float swY = cy + ROW_H / 2f - ToggleSwitch.H / 2f;
                    if (ToggleSwitch.hit(mx, my, swX, swY)) {
                        m.toggle();
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
        float ix = x + INSET_X + 6;
        float iw = INSET_W - 12;
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
                float trackY = cy + SLIDER_H - 10;
                if (button == 0 && HoveringUtils.isHovered(mx, my, ix, trackY - 6, iw, 16)) {
                    ClickGuiScreen.draggingSlider = num;
                    ClickGuiScreen.dragTrackX = ix;
                    ClickGuiScreen.dragTrackW = iw;
                    applySliderAt(num, mx, ix, iw);
                    return true;
                }
                cy += SLIDER_H;
            } else if (s instanceof TextSetting ts) {
                float by = cy + 11;
                if (button == 0 && HoveringUtils.isHovered(mx, my, ix, by, iw, 15)) {
                    ClickGuiScreen.activeTextSetting = ts;
                    return true;
                }
                cy += TEXT_H;
            } else if (s instanceof BindSetting bind) {
                if (button == 0 && HoveringUtils.isHovered(mx, my, ix, cy, iw, BIND_H)) {
                    // Клик по бинду — режим прослушки: следующая клавиша/кнопка мыши станет биндом
                    ClickGuiScreen.listeningBind = bind;
                    return true;
                }
                cy += BIND_H;
            } else if (s instanceof ModeSetting mode) {
                List<float[]> layout = pillLayout(mode);
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
