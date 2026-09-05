package fun.wonderful.client.ui.clickgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.client.ClientSoundPlayer;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.input.KeyBoardUtils;
import fun.wonderful.api.utils.math.HoveringUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BindSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.TextSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClickGUI — одна панель по центру в две колонки:
 * шапка (имя клиента + поиск), слева вертикальный список категорий,
 * справа модули выбранной категории (имя по центру, настройки под строкой),
 * подвал с подсказкой и кнопками (только вкл / свернуть / тема).
 * Стиль: тёмная панель + один акцентный цвет, плавные понятные анимации.
 */
public class ClickGuiScreen extends Screen {

    // ===== Общее состояние ввода (используется ModuleList/ThemePanel) =====
    static BindSetting listeningBind;
    static Module listeningModule;
    static TextSetting activeTextSetting;
    static FloatSetting draggingSlider;
    static float dragTrackX, dragTrackW;
    static String filter = "";
    /** Фильтр «только включённые» (кнопка в подвале). */
    static boolean showOnlyEnabled = false;

    static int screenHeightCached;

    // ===== Геометрия панели =====
    private static final float HEADER_H = 40f;
    private static final float FOOTER_H = 32f;
    private static final float RAIL_W = 118f;
    private static final float RAIL_ITEM_H = 30f;
    private static final float MIN_W = 340f;
    private static final float MIN_H = 230f;

    private final List<Module.ModuleCategory> categories = new ArrayList<>();
    private final ModuleList list = new ModuleList();

    private Module.ModuleCategory activeCategory;

    private boolean closing = false;
    private long lastFrameNanos;

    // Анимации
    private final AnimationUtils openAnim = new AnimationUtils(0f, 9f, Easings.CUBIC_OUT);
    private final AnimationUtils tooltipAnim = new AnimationUtils(0f, 12f, Easings.CUBIC_OUT);
    private final AnimationUtils searchAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);

    /** Пилюля активной категории: Y плывёт к целевой строке. */
    private float pillY = -1f;

    /** Hover-анимации кнопок подвала. */
    private final Map<String, AnimationUtils> btnHover = new HashMap<>();

    private Module tooltipModule;

    // Кэш координат для кликов
    private float panelX, panelY, panelW, panelH;
    private float searchX, searchY, searchW, searchH = 22f;
    private float railTop;
    private float btnOnX, btnOnW, btnCollapseX, btnCollapseW, btnThemeX;
    private static final float BTN_H = 20f;

    public ClickGuiScreen() {
        super(Text.literal("wonderful"));
        for (Module.ModuleCategory c : Module.ModuleCategory.values()) {
            categories.add(c);
        }
        activeCategory = categories.get(0);
        lastFrameNanos = System.nanoTime();
        ThemePanel.init();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Единый акцент GUI — один цвет темы. */
    static int accent() {
        return ThemePanel.accentSolid();
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        listeningBind = null;
        activeTextSetting = null;
        draggingSlider = null;
        ClientSoundPlayer.playSound("closegui.wav", 0.6, 1.0f);
    }

    private AnimationUtils btn(String key) {
        return btnHover.computeIfAbsent(key, k -> new AnimationUtils(0f, 13f, Easings.CUBIC_OUT));
    }

    // ============================================================
    // Рендер
    // ============================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack ms = context.getMatrices();

        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000f, 0.0001f, 0.05f);
        lastFrameNanos = now;
        screenHeightCached = this.height;

        openAnim.update(closing ? 0f : 1f);
        float p = MathHelper.clamp(openAnim.getValue(), 0f, 1f);
        if (p <= 0.01f) {
            if (closing) {
                closing = false;
                close();
            }
            return;
        }

        // Панель: мягкий въезд (масштаб + сдвиг вверх + фейд)
        float scale = 0.965f + 0.035f * p;
        float rise = (1f - p) * 10f;

        panelW = Math.min(Math.max(MIN_W, 460f), this.width - 20f);
        panelH = Math.min(Math.max(MIN_H, 320f), this.height - 24f);
        panelX = (this.width - panelW) / 2f;
        panelY = (this.height - panelH) / 2f - 6f;

        float cxp = panelX + panelW / 2f;
        float cyp = panelY + panelH / 2f;

        // Фон: матовое стекло (блюр + тёмный тинт)
        RenderUtils.drawBlur(ms, -2, -2, this.width + 4, this.height + 4, 0f, 16f,
                ColorUtils.rgba(3, 5, 11, (int) (148 * p)));

        ms.push();
        ms.translate(cxp, cyp, 0f);
        ms.scale(scale, scale, 1f);
        ms.translate(-cxp, -cyp, 0f);
        float py = panelY - rise;

        int ac = accent();

        // Панель: тень + блюр + фон + тонкая обводка
        RenderUtils.drawShadow(ms, panelX, py, panelW, panelH, 12f, 16f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.55f * p));
        RenderUtils.drawBlur(ms, panelX, py, panelW, panelH, 12f, 10f,
                ColorUtils.rgba(7, 10, 18, (int) (185 * p)));
        RenderUtils.drawRoundedRect(ms, panelX, py, panelW, panelH, 12f,
                ColorUtils.rgba(12, 15, 23, (int) (246 * p)));
        RenderUtils.drawRoundedRectOutline(ms, panelX, py, panelW, panelH, 12f, 1f,
                ColorUtils.rgba(255, 255, 255, (int) (22 * p)),
                ColorUtils.rgba(255, 255, 255, (int) (22 * p)),
                ColorUtils.rgba(0, 0, 0, (int) (50 * p)),
                ColorUtils.rgba(0, 0, 0, (int) (50 * p)));

        renderHeader(ms, py, p, ac);
        renderRail(ms, mouseX, mouseY, py, dt, p, ac);

        // Контент модулей — правая колонка
        float contentY = py + HEADER_H;
        float contentH = panelH - HEADER_H - FOOTER_H;
        list.bounds(panelX + RAIL_W, contentY, panelW - RAIL_W - 2f, contentH);
        list.render(ms, mouseX, mouseY, dt, p, activeCategory, ac);

        renderFooter(ms, mouseX, mouseY, py, p, ac);

        ms.pop();

        // Панель тем — отдельным окном рядом (кнопка «тема» в подвале)
        ThemePanel.updateOpen();
        if (ThemePanel.isShown()) {
            float ax = panelX + panelW + 10f;
            if (ax + ThemePanel.W > this.width - 4f) {
                ax = Math.max(4f, panelX - ThemePanel.W - 10f);
            }
            ThemePanel.anchor(ax, panelY + HEADER_H);
            ThemePanel.render(context, mouseX, mouseY, p);
        }

        renderTooltip(ms, mouseX, mouseY, p, ac);
    }

    /** Шапка: точка-акцент + название слева, поле поиска справа. */
    private void renderHeader(MatrixStack ms, float py, float p, int ac) {
        float hcy = py + HEADER_H / 2f;

        RenderUtils.drawRoundCircle(ms, panelX + 16f, hcy, 2.6f,
                ColorUtils.applyAlpha(ac, 0.9f * p));
        ModuleList.text(ms, 15, "wonderful", panelX + 26f, hcy - ModuleList.fh(15) / 2f,
                ColorUtils.rgba(242, 244, 250, (int) (250 * p)));

        // Поле поиска: расширяется при вводе
        boolean focused = !filter.isEmpty();
        searchAnim.update(focused ? 1f : 0f);
        float sp = MathHelper.clamp(searchAnim.getValue(), 0f, 1f);
        float baseW = 128f;
        searchW = baseW + 52f * sp;
        searchX = panelX + panelW - 14f - searchW;
        searchY = hcy - searchH / 2f;

        RenderUtils.drawRoundedRect(ms, searchX, searchY, searchW, searchH, searchH / 2f,
                ColorUtils.rgba(255, 255, 255, (int) ((focused ? 16 : 9) * p)));
        RenderUtils.drawRoundedRectOutline(ms, searchX, searchY, searchW, searchH, searchH / 2f, 1f,
                ColorUtils.applyAlpha(ac, (int) ((focused ? 90 : 24) * p)),
                ColorUtils.applyAlpha(ac, (int) ((focused ? 90 : 24) * p)),
                ColorUtils.rgba(255, 255, 255, (int) (12 * p)),
                ColorUtils.rgba(255, 255, 255, (int) (12 * p)));

        // Лупа
        RenderUtils.drawRoundCircle(ms, searchX + 9f, hcy - 1.6f, 2.4f,
                ColorUtils.rgba(150, 157, 172, (int) (200 * p)));
        RenderUtils.drawRoundedRect(ms, searchX + 10.6f, hcy + 1.2f, 3.4f, 1f, 0.5f,
                ColorUtils.rgba(150, 157, 172, (int) (200 * p)));

        Font sf = ModuleList.f(11);
        if (sf == null) return;
        float textX = searchX + 18f;
        float maxTextW = searchW - 26f;
        if (focused) {
            String shown = filter;
            while (shown.length() > 1 && ModuleList.tw(11, shown) > maxTextW) {
                shown = shown.substring(1);
            }
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            ModuleList.text(ms, 11, shown + (blink ? "|" : ""), textX, hcy - ModuleList.fh(11) / 2f,
                    ColorUtils.rgba(236, 240, 248, (int) (240 * p)));
        } else {
            ModuleList.text(ms, 11, "Поиск...", textX, hcy - ModuleList.fh(11) / 2f,
                    ColorUtils.rgba(132, 140, 156, (int) (165 * p)));
        }

        // Разделитель под шапкой
        RenderUtils.drawRoundedRect(ms, panelX + 10f, py + HEADER_H - 1f, panelW - 20f, 1f, 0.5f,
                ColorUtils.rgba(255, 255, 255, (int) (18 * p)));
    }

    /** Левая колонка: категории текстом + плавающая пилюля активной. */
    private void renderRail(MatrixStack ms, int mouseX, int mouseY, float py, float dt, float p, int ac) {
        float railX = panelX + 8f;
        float railW = RAIL_W - 18f;
        railTop = py + HEADER_H + 8f;

        // Пилюля: Y плывёт к строке активной категории
        float targetY = railTop + categories.indexOf(activeCategory) * RAIL_ITEM_H;
        if (pillY < 0f) pillY = targetY;
        float k = 1f - (float) Math.exp(-dt * 15.0);
        pillY += (targetY - pillY) * k;

        RenderUtils.drawRoundedRect(ms, railX, pillY, railW, RAIL_ITEM_H - 4f, 7f,
                ColorUtils.applyAlpha(ac, 0.16f * p));
        RenderUtils.drawRoundedRect(ms, railX + 3.2f, pillY + 6f, 1.8f, RAIL_ITEM_H - 16f, 0.9f,
                ColorUtils.applyAlpha(ac, 0.85f * p));

        for (int i = 0; i < categories.size(); i++) {
            Module.ModuleCategory c = categories.get(i);
            float iy = railTop + i * RAIL_ITEM_H;
            float icy = iy + (RAIL_ITEM_H - 4f) / 2f;
            boolean active = c == activeCategory;
            boolean hov = !active && HoveringUtils.isHovered(mouseX, mouseY, railX, iy, railW, RAIL_ITEM_H - 4f);

            if (hov) {
                RenderUtils.drawRoundedRect(ms, railX, iy, railW, RAIL_ITEM_H - 4f, 7f,
                        ColorUtils.rgba(255, 255, 255, (int) (12 * p)));
            }

            ModuleList.text(ms, 12, c.getName(), railX + 12f, icy - ModuleList.fh(12) / 2f,
                    active
                            ? ColorUtils.rgba(240, 243, 250, (int) (248 * p))
                            : ColorUtils.rgba(176, 183, 197, (int) (205 * p)));

            // Счётчик включённых модулей категории — тусклая цифра справа
            int cnt = list.enabledCount(c);
            if (cnt > 0) {
                String cs = String.valueOf(cnt);
                ModuleList.text(ms, 10, cs, railX + railW - 4f - ModuleList.tw(10, cs),
                        icy - ModuleList.fh(10) / 2f,
                        active
                                ? ColorUtils.applyAlpha(ac, 0.85f * p)
                                : ColorUtils.rgba(130, 137, 152, (int) (170 * p)));
            }
        }

        // Вертикальный разделитель колонок
        RenderUtils.drawRoundedRect(ms, panelX + RAIL_W, py + HEADER_H + 8f, 1f,
                panelH - HEADER_H - FOOTER_H - 16f, 0.5f,
                ColorUtils.rgba(255, 255, 255, (int) (16 * p)));
    }

    /** Подвал: подсказка слева, кнопки справа (только вкл / свернуть / тема). */
    private void renderFooter(MatrixStack ms, int mouseX, int mouseY, float py, float p, int ac) {
        float fy = py + panelH - FOOTER_H / 2f;

        String hint = "ЛКМ — вкл · ПКМ — настройки · СКМ — бинд";
        ModuleList.text(ms, 10, hint, panelX + 14f, fy - ModuleList.fh(10) / 2f,
                ColorUtils.rgba(120, 127, 142, (int) (185 * p)));

        // Кнопка «тема» (иконка палитры)
        btnThemeX = panelX + panelW - 14f - BTN_H - 8f;
        drawIconBtn(ms, mouseX, mouseY, btnThemeX, fy - BTN_H / 2f, BTN_H, "theme",
                ThemePanel.isOpen(), p, ac);

        // Кнопка «свернуть»
        String collapse = "свернуть";
        btnCollapseW = ModuleList.tw(10, collapse) + 16f;
        btnCollapseX = btnThemeX - 8f - btnCollapseW;
        drawTextBtn(ms, mouseX, mouseY, btnCollapseX, fy - BTN_H / 2f, btnCollapseW, collapse,
                false, p, ac);

        // Кнопка «только вкл»
        String only = "только вкл";
        btnOnW = ModuleList.tw(10, only) + 16f;
        btnOnX = btnCollapseX - 8f - btnOnW;
        drawTextBtn(ms, mouseX, mouseY, btnOnX, fy - BTN_H / 2f, btnOnW, only,
                showOnlyEnabled, p, ac);

        // Разделитель над подвалом
        RenderUtils.drawRoundedRect(ms, panelX + 10f, py + panelH - FOOTER_H, panelW - 20f, 1f, 0.5f,
                ColorUtils.rgba(255, 255, 255, (int) (18 * p)));
    }

    /** Кнопка-текст в подвале: пилюля, при активном режиме — заливка акцентом. */
    private void drawTextBtn(MatrixStack ms, int mouseX, int mouseY, float bx, float by, float bw,
                             String label, boolean on, float p, int ac) {
        boolean hov = HoveringUtils.isHovered(mouseX, mouseY, bx, by, bw, BTN_H);
        AnimationUtils a = btn(label);
        a.update(hov ? 1f : 0f);
        float hp = MathHelper.clamp(a.getValue(), 0f, 1f);

        if (on) {
            RenderUtils.drawRoundedRect(ms, bx, by, bw, BTN_H, BTN_H / 2f,
                    ColorUtils.applyAlpha(ac, 0.20f * p));
        } else if (hp > 0.03f) {
            RenderUtils.drawRoundedRect(ms, bx, by, bw, BTN_H, BTN_H / 2f,
                    ColorUtils.rgba(255, 255, 255, (int) (14 * hp * p)));
        }

        ModuleList.text(ms, 10, label, bx + bw / 2f - ModuleList.tw(10, label) / 2f,
                by + BTN_H / 2f - ModuleList.fh(10) / 2f,
                on
                        ? ColorUtils.applyAlpha(ColorUtils.interpolateColor(ac, 0xFFFFFFFF, 0.25f), 0.95f * p)
                        : ColorUtils.rgba((int) (176 + 40 * hp), (int) (183 + 35 * hp),
                          (int) (197 + 30 * hp), (int) (215 * p)));
    }

    /** Кнопка-иконка в подвале (палитра тем). */
    private void drawIconBtn(MatrixStack ms, int mouseX, int mouseY, float bx, float by, float size,
                             String key, boolean on, float p, int ac) {
        boolean hov = HoveringUtils.isHovered(mouseX, mouseY, bx, by, size, size);
        AnimationUtils a = btn(key);
        a.update(hov || on ? 1f : 0f);
        float hp = MathHelper.clamp(a.getValue(), 0f, 1f);

        if (hp > 0.03f) {
            RenderUtils.drawRoundedRect(ms, bx, by, size, size, size / 2f,
                    on
                            ? ColorUtils.applyAlpha(ac, 0.20f * p)
                            : ColorUtils.rgba(255, 255, 255, (int) (16 * hp * p)));
        }
        GuiIcons.draw(ms, "theme_palette", bx + 3f, by + 3f, size - 6f,
                ColorUtils.applyAlpha(
                        on ? ac : ColorUtils.rgba(178, 185, 200, 255),
                        (on ? 0.95f : 0.80f + 0.15f * hp) * p));
    }

    /** Тултип с описанием модуля. */
    private void renderTooltip(MatrixStack ms, int mouseX, int mouseY, float alpha, int ac) {
        boolean blocked = draggingSlider != null || listeningBind != null
                || listeningModule != null || activeTextSetting != null;
        Module cand = blocked ? null : list.tooltipCandidate();

        if (blocked && tooltipModule != null) {
            tooltipAnim.update(0f);
            float a0 = MathHelper.clamp(tooltipAnim.getValue(), 0f, 1f);
            if (a0 <= 0.03f) tooltipModule = null;
            return;
        }
        if (cand != tooltipModule) {
            tooltipModule = cand;
            tooltipAnim.setValue(0f);
        }
        tooltipAnim.update(cand != null ? 1f : 0f);
        float al = MathHelper.clamp(tooltipAnim.getValue(), 0f, 1f);
        if (al <= 0.03f || tooltipModule == null) return;

        String desc = tooltipModule.getDescription();
        Font ft = ModuleList.f(11);
        if (ft == null) return;
        float tw = ModuleList.tw(11, desc) + 18f;
        float th = 22f;

        float tx = MathHelper.clamp(mouseX + 14, 4, this.width - tw - 4);
        float ty = MathHelper.clamp(mouseY + 16, 4, this.height - th - 4);

        RenderUtils.drawShadow(ms, tx, ty, tw, th, 6f, 10f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.45f * al * alpha));
        RenderUtils.drawRoundedRect(ms, tx, ty, tw, th, 6f,
                ColorUtils.rgba(8, 11, 19, (int) (240 * al * alpha)));
        RenderUtils.drawRoundedRectOutline(ms, tx, ty, tw, th, 6f, 1f,
                ColorUtils.applyAlpha(ac, (int) (110 * al * alpha)),
                ColorUtils.applyAlpha(ac, (int) (110 * al * alpha)),
                ColorUtils.rgba(255, 255, 255, (int) (18 * al * alpha)),
                ColorUtils.rgba(255, 255, 255, (int) (18 * al * alpha)));
        RenderUtils.drawRoundedRect(ms, tx + 5, ty + 5.5f, 2.2f, th - 11f, 1.1f,
                ColorUtils.applyAlpha(ac, 0.9f * al * alpha));
        ModuleList.text(ms, 11, desc, tx + 13, ty + th / 2f - ModuleList.fh(11) / 2f,
                ColorUtils.rgba(233, 237, 245, (int) (245 * al * alpha)));
    }

    // ============================================================
    // Ввод
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (closing) return true;

        // Панель тем — поверх, перехватывает свои клики
        if (ThemePanel.isShown() && ThemePanel.hit(mx, my)) {
            ThemePanel.mouseClick(mx, my, button);
            return true;
        }

        if (listeningBind != null) {
            applyBind(listeningBind, KeyBoardUtils.createMouseBind(button));
            listeningBind = null;
            return true;
        }

        if (HoveringUtils.isHovered(mx, my, panelX, panelY, panelW, panelH)) {
            float fy = panelY + panelH - FOOTER_H / 2f;

            // Кнопки подвала
            if (button == 0) {
                if (HoveringUtils.isHovered(mx, my, btnThemeX, fy - BTN_H / 2f, BTN_H, BTN_H)) {
                    ThemePanel.setOpen(!ThemePanel.isOpen());
                    return true;
                }
                if (HoveringUtils.isHovered(mx, my, btnCollapseX, fy - BTN_H / 2f, btnCollapseW, BTN_H)) {
                    list.collapseAll();
                    return true;
                }
                if (HoveringUtils.isHovered(mx, my, btnOnX, fy - BTN_H / 2f, btnOnW, BTN_H)) {
                    showOnlyEnabled = !showOnlyEnabled;
                    return true;
                }
            }

            // Поле поиска — визуальный фокус, ввод идёт всегда
            if (HoveringUtils.isHovered(mx, my, searchX, searchY, searchW, searchH)) {
                return true;
            }

            // Колонка категорий
            float railX = panelX + 8f;
            float railW = RAIL_W - 18f;
            if (mx >= railX && mx <= railX + railW && my >= railTop
                    && my < railTop + categories.size() * RAIL_ITEM_H) {
                int idx = (int) ((my - railTop) / RAIL_ITEM_H);
                if (idx >= 0 && idx < categories.size()) {
                    activeCategory = categories.get(idx);
                }
                return true;
            }

            // Список модулей
            if (list.hit(mx, my)) {
                if (activeTextSetting != null) activeTextSetting = null;
                list.handleClick(button, mx, my, activeCategory);
                return true;
            }
            return true;
        }

        if (activeTextSetting != null) activeTextSetting = null;
        if (listeningModule != null) listeningModule = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int mx = (int) mouseX;
        if (draggingSlider != null) {
            applySlider(draggingSlider, mx);
            return true;
        }
        if (ThemePanel.isShown()) {
            ThemePanel.mouseDrag(mx, (int) mouseY);
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        ThemePanel.mouseRelease();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (ThemePanel.isShown() && ThemePanel.hit(mx, my)) {
            ThemePanel.mouseScroll((float) verticalAmount, mx, my);
            return true;
        }
        if (list.hit(mx, my)) {
            list.scroll((float) verticalAmount);
            return true;
        }
        return true;
    }

    private void applySlider(FloatSetting num, int mouseX) {
        float frac = MathHelper.clamp((mouseX - dragTrackX) / Math.max(1f, dragTrackW), 0f, 1f);
        float range = num.getMax() - num.getMin();
        float raw = num.getMin() + range * frac;
        float step = num.getStep();
        float snapped = Math.round(raw / step) * step;
        num.setValue(snapped);
    }

    /** Установка клавиши бинда: синхронизирует настройку и module.setKey владельца. */
    private static void applyBind(BindSetting bind, int key) {
        if (bind.getOwner() != null) {
            bind.getOwner().setKey(key);
        } else {
            bind.setKey(key);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningModule != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                listeningModule.setKey(-1);
            } else {
                listeningModule.setKey(keyCode);
            }
            listeningModule = null;
            return true;
        }
        if (listeningBind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listeningBind = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                applyBind(listeningBind, -1);
                listeningBind = null;
                return true;
            }
            applyBind(listeningBind, keyCode);
            listeningBind = null;
            return true;
        }

        if (activeTextSetting != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                activeTextSetting = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !activeTextSetting.get().isEmpty()) {
                String t = activeTextSetting.get();
                activeTextSetting.setText(t.substring(0, t.length() - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                activeTextSetting.setText(activeTextSetting.get() + this.client.keyboard.getClipboard());
                return true;
            }
            return true;
        }

        // Esc: сначала панель тем, потом очистка поиска, затем закрытие
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (ThemePanel.isOpen()) {
                ThemePanel.setOpen(false);
                return true;
            }
            if (!filter.isEmpty()) {
                filter = "";
                return true;
            }
            if (showOnlyEnabled) {
                showOnlyEnabled = false;
                return true;
            }
            requestClose();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            requestClose();
            return true;
        }

        // Backspace — правка поиска
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !filter.isEmpty()) {
            filter = filter.substring(0, filter.length() - 1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (listeningBind != null || listeningModule != null) return true;
        if (activeTextSetting != null) {
            if (chr >= 32 && chr != 127) {
                activeTextSetting.setText(activeTextSetting.get() + chr);
            }
            return true;
        }
        // Ввод всегда идёт в поиск — как в современных GUI
        if (chr >= 32 && chr != 127) {
            filter = filter + chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        listeningBind = null;
        listeningModule = null;
        activeTextSetting = null;
        draggingSlider = null;
        filter = "";
        showOnlyEnabled = false;
        ThemePanel.setOpen(false);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Фон — матовое стекло из render() (блюр + тинт)
    }
}
