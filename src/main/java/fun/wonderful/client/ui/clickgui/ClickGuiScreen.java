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
import java.util.List;

/**
 * ClickGUI — одна панель по центру: шапка с поиском и кнопкой тем,
 * вкладки категорий сверху, список модулей с настройками, подвал с подсказкой.
 * Стиль: тёмная панель + один акцентный цвет, аккуратные плавные анимации.
 */
public class ClickGuiScreen extends Screen {

    // ===== Общее состояние ввода (используется ModuleList/ThemePanel) =====
    static BindSetting listeningBind;
    static Module listeningModule;
    static TextSetting activeTextSetting;
    static FloatSetting draggingSlider;
    static float dragTrackX, dragTrackW;
    static String filter = "";

    static int screenHeightCached;

    // ===== Геометрия панели =====
    private static final float HEADER_H = 38f;
    private static final float TABS_H = 32f;
    private static final float FOOTER_H = 24f;
    private static final float MIN_W = 300f;
    private static final float MIN_H = 220f;

    private final List<Module.ModuleCategory> categories = new ArrayList<>();
    private final ModuleList list = new ModuleList();

    private Module.ModuleCategory activeCategory;

    private boolean closing = false;
    private long lastFrameNanos;

    // Анимации
    private final AnimationUtils openAnim = new AnimationUtils(0f, 9f, Easings.CUBIC_OUT);
    private final AnimationUtils tooltipAnim = new AnimationUtils(0f, 12f, Easings.CUBIC_OUT);
    private final AnimationUtils searchAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);
    private final AnimationUtils paletteAnim = new AnimationUtils(0f, 11f, Easings.CUBIC_OUT);

    /** Пилюля активной вкладки: текущие X/W плывут к целевым. */
    private float pillX, pillW = -1f;

    private Module tooltipModule;

    // Кэш координат для кликов
    private float panelX, panelY, panelW, panelH;
    private float searchX, searchY, searchW, searchH = 22f;
    private float paletteX, paletteY, paletteSize = 20f;

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

        panelW = Math.min(Math.max(MIN_W, 440f), this.width - 20f);
        panelH = Math.min(Math.max(MIN_H, 310f), this.height - 24f);
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

        renderHeader(ms, mouseX, mouseY, py, p, ac);
        renderTabs(ms, mouseX, mouseY, py, dt, p, ac);

        // Контент
        float contentY = py + HEADER_H + TABS_H;
        float contentH = panelH - HEADER_H - TABS_H - FOOTER_H;
        list.bounds(panelX + 2f, contentY, panelW - 4f, contentH);
        list.render(ms, mouseX, mouseY, dt, p, activeCategory, ac);

        renderFooter(ms, py, p, ac);

        ms.pop();

        // Панель тем — отдельным окном рядом (открывается кнопкой-палитрой)
        renderThemePanel(context, mouseX, mouseY, p);

        renderTooltip(ms, mouseX, mouseY, p, ac);
    }

    /** Шапка: точка + название, поиск, кнопка палитры. */
    private void renderHeader(MatrixStack ms, int mouseX, int mouseY, float py, float p, int ac) {
        float hcy = py + HEADER_H / 2f;

        // Точка-акцент + название
        RenderUtils.drawRoundCircle(ms, panelX + 16f, hcy, 2.6f,
                ColorUtils.applyAlpha(ac, 0.9f * p));
        ModuleList.text(ms, 15, "wonderful", panelX + 26f, hcy - ModuleList.fh(15) / 2f,
                ColorUtils.rgba(242, 244, 250, (int) (250 * p)));

        // Кнопка палитры (темы) справа
        paletteX = panelX + panelW - 14f - paletteSize;
        paletteY = hcy - paletteSize / 2f;
        boolean palHover = HoveringUtils.isHovered(mouseX, mouseY, paletteX - 3f, paletteY - 3f,
                paletteSize + 6f, paletteSize + 6f);
        paletteAnim.update(ThemePanel.isOpen() ? 1f : palHover ? 0.6f : 0f);
        float palP = MathHelper.clamp(paletteAnim.getValue(), 0f, 1f);
        if (palP > 0.03f) {
            RenderUtils.drawRoundedRect(ms, paletteX - 3f, paletteY - 3f,
                    paletteSize + 6f, paletteSize + 6f, (paletteSize + 6f) / 2f,
                    ColorUtils.rgba(255, 255, 255, (int) (26 * palP * p)));
        }
        GuiIcons.draw(ms, "theme_palette", paletteX + 1.5f, paletteY + 1.5f, paletteSize - 3f,
                ColorUtils.applyAlpha(
                        ThemePanel.isOpen() ? ac : ColorUtils.rgba(178, 185, 200, 255),
                        (ThemePanel.isOpen() ? 0.95f : 0.85f) * p));

        // Поле поиска: расширяется в фокусе (есть текст)
        boolean focused = !filter.isEmpty();
        searchAnim.update(focused ? 1f : 0f);
        float sp = MathHelper.clamp(searchAnim.getValue(), 0f, 1f);
        float baseW = 118f;
        searchW = baseW + 46f * sp;
        searchX = paletteX - 10f - searchW;
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
            String shown = ModuleList.filter;
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

    /** Вкладки категорий: иконка + название, пилюля активной плавает между ними. */
    private void renderTabs(MatrixStack ms, int mouseX, int mouseY, float py, float dt, float p, int ac) {
        float tabY = py + HEADER_H;
        float pad = 10f;

        // Раскладка вкладок подряд от левого края
        float tx = panelX + pad;
        float targetX = 0f, targetW = 0f;
        List<float[]> rects = new ArrayList<>();
        for (Module.ModuleCategory c : categories) {
            float iconS = 13f;
            float labelW = ModuleList.tw(12, c.getName());
            float w = iconS + 5f + labelW + 16f;
            rects.add(new float[]{tx, w});
            if (c == activeCategory) {
                targetX = tx;
                targetW = w;
            }
            tx += w + 4f;
        }

        // Пилюля: X/W плывут к активной вкладке
        if (pillW < 0f) {
            pillX = targetX;
            pillW = targetW;
        }
        float k = 1f - (float) Math.exp(-dt * 15.0);
        pillX += (targetX - pillX) * k;
        pillW += (targetW - pillW) * k;

        // Пилюля активной вкладки + акцентная линия снизу
        RenderUtils.drawRoundedRect(ms, pillX, tabY + 4f, pillW, TABS_H - 9f, 7f,
                ColorUtils.applyAlpha(ac, 0.16f * p));
        RenderUtils.drawRoundedRect(ms, pillX + 6f, tabY + TABS_H - 5.2f, pillW - 12f, 1.6f, 0.8f,
                ColorUtils.applyAlpha(ac, 0.85f * p));

        // Содержимое вкладок
        for (int i = 0; i < categories.size(); i++) {
            Module.ModuleCategory c = categories.get(i);
            float rx = rects.get(i)[0];
            float rw = rects.get(i)[1];
            boolean active = c == activeCategory;
            boolean hov = !active && HoveringUtils.isHovered(mouseX, mouseY, rx, tabY + 3f, rw, TABS_H - 6f);

            if (hov) {
                RenderUtils.drawRoundedRect(ms, rx, tabY + 4f, rw, TABS_H - 9f, 7f,
                        ColorUtils.rgba(255, 255, 255, (int) (12 * p)));
            }

            float iconS = 13f;
            float cy = tabY + TABS_H / 2f;
            int iconCol = active
                    ? ColorUtils.applyAlpha(ac, 0.95f * p)
                    : ColorUtils.rgba(150, 157, 172, (int) (200 * p));
            CategoryIcons.draw(ms, c, rx + 9f, cy - iconS / 2f, iconS, iconCol);

            ModuleList.text(ms, 12, c.getName(), rx + 9f + iconS + 5f, cy - ModuleList.fh(12) / 2f,
                    active
                            ? ColorUtils.rgba(240, 243, 250, (int) (248 * p))
                            : ColorUtils.rgba(176, 183, 197, (int) (205 * p)));
        }

        RenderUtils.drawRoundedRect(ms, panelX + 10f, tabY + TABS_H - 1f, panelW - 20f, 1f, 0.5f,
                ColorUtils.rgba(255, 255, 255, (int) (18 * p)));
    }

    /** Подвал: счётчики слева, подсказка управления справа. */
    private void renderFooter(MatrixStack ms, float py, float p, int ac) {
        float fy = py + panelH - FOOTER_H / 2f;

        int total = list.modules(activeCategory).size();
        int enabled = list.enabledCount(activeCategory);
        String counts = enabled + " / " + total + " вкл";
        float dotR = 2f;
        RenderUtils.drawRoundCircle(ms, panelX + 16f, fy, dotR,
                ColorUtils.applyAlpha(enabled > 0 ? ac : ColorUtils.rgba(120, 127, 142, 255), 0.8f * p));
        ModuleList.text(ms, 10, counts, panelX + 24f, fy - ModuleList.fh(10) / 2f,
                ColorUtils.rgba(170, 177, 192, (int) (215 * p)));

        String hint = "ЛКМ — вкл · ПКМ — настройки · СКМ — бинд";
        ModuleList.text(ms, 10, hint, panelX + panelW - 14f - ModuleList.tw(10, hint),
                fy - ModuleList.fh(10) / 2f, ColorUtils.rgba(120, 127, 142, (int) (185 * p)));
    }

    /** Панель тем поверх, рядом с главной панелью. */
    private void renderThemePanel(DrawContext context, int mouseX, int mouseY, float p) {
        ThemePanel.updateOpen();
        if (!ThemePanel.isShown()) return;

        float ax = panelX + panelW + 10f;
        if (ax + ThemePanel.W > this.width - 4f) {
            ax = Math.max(4f, panelX - ThemePanel.W - 10f);
        }
        ThemePanel.anchor(ax, panelY + HEADER_H);
        ThemePanel.render(context, mouseX, mouseY, p);
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
            // Кнопка палитры
            if (HoveringUtils.isHovered(mx, my, paletteX - 3f, paletteY - 3f, paletteSize + 6f, paletteSize + 6f)) {
                if (button == 0) ThemePanel.setOpen(!ThemePanel.isOpen());
                return true;
            }
            // Поле поиска — просто визуальный фокус, ввод идёт всегда
            if (HoveringUtils.isHovered(mx, my, searchX, searchY, searchW, searchH)) {
                return true;
            }
            // Вкладки
            float tabY = panelY + HEADER_H;
            if (my >= tabY && my <= tabY + TABS_H) {
                float tx = panelX + 10f;
                for (Module.ModuleCategory c : categories) {
                    float iconS = 13f;
                    float w = iconS + 5f + ModuleList.tw(12, c.getName()) + 16f;
                    if (mx >= tx && mx <= tx + w) {
                        if (activeCategory != c) {
                            activeCategory = c;
                        }
                        return true;
                    }
                    tx += w + 4f;
                }
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
        ThemePanel.setOpen(false);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Фон — матовое стекло из render() (блюр + тинт)
    }
}
