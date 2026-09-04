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
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BindSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.TextSetting;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {

    static BindSetting listeningBind;
    static Module listeningModule;
    static TextSetting activeTextSetting;
    static FloatSetting draggingSlider;
    static float dragTrackX, dragTrackW;
    static String filter = "";
    static boolean filterActive = false;

    private float filterBoxX, filterBoxY, filterBoxW, filterBoxH;

    static final int WIN_W = DropdownWindow.WIN_W;
    static int screenHeightCached;

    private final List<DropdownWindow> windows = new ArrayList<>();
    private boolean positionsInitialized = false;
    private boolean closing = false;
    private long openedAt;
    private long lastFrameNanos;

    private Module tooltipModule;
    private final AnimationUtils tooltipAnim = new AnimationUtils(0f, 12f, Easings.CUBIC_OUT);
    private final AnimationUtils filterAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);

    public ClickGuiScreen() {
        super(Text.literal("wonderful"));
        openedAt = System.nanoTime();
        lastFrameNanos = openedAt;
        int idx = 0;
        for (Module.ModuleCategory c : Module.ModuleCategory.values()) {
            windows.add(new DropdownWindow(c, idx++));
        }
        ThemePanel.init();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    static int accent() {
        return ThemePanel.accentSolid();
    }

    /** Акцент с учётом градиента и позиции по экрану (для использования в разных частях GUI). */
    public static int accentAt(float y) {
        return ThemePanel.accent(y);
    }

    private void initPositions() {
        positionsInitialized = true;
        final float gap = 8f;
        final float marginX = 6f;
        final float leftReserve = ThemePanel.W + 12f; // место слева под панель тем

        int n = windows.size();
        float totalW = n * WIN_W + (n - 1) * gap;
        float rowY = 20f;
        float availW = this.width - marginX * 2 - leftReserve;

        if (totalW <= availW) {
            // Один ровный ряд, выровнен по правой части после панели тем
            float startX = marginX + leftReserve + (availW - totalW) / 2f;
            for (int i = 0; i < n; i++) {
                DropdownWindow w = windows.get(i);
                w.x = startX + i * (WIN_W + gap);
                w.y = rowY;
            }
        } else {
            // Узкий экран: переносим лишние окна на следующий ряд
            int perRow = Math.max(1, (int) ((availW + gap) / (WIN_W + gap)));
            int idx = 0;
            while (idx < n) {
                int inRow = Math.min(perRow, n - idx); // сколько окон реально осталось в этом ряду
                float availBody = Math.max(60f, this.height - 14f - (rowY + DropdownWindow.HEADER_H));
                float rowW = inRow * WIN_W + (inRow - 1) * gap;
                float rowStartX = marginX + leftReserve + Math.max(0, (availW - rowW) / 2f);
                float rowMaxH = 0f;
                for (int k = 0; k < inRow; k++) {
                    DropdownWindow w = windows.get(idx + k);
                    w.x = rowStartX + k * (WIN_W + gap);
                    w.y = rowY;
                    rowMaxH = Math.max(rowMaxH, w.estimatedWindowHeight(availBody));
                }
                rowY += rowMaxH + gap;
                idx += inRow;
            }
        }

        // Каскадное появление окон
        long stagger = 0L;
        for (DropdownWindow w : windows) {
            w.openAtNanos = openedAt + stagger;
            stagger += 70_000_000L;
            w.open = true;
        }
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        listeningBind = null;
        activeTextSetting = null;
        draggingSlider = null;
        ClientSoundPlayer.playSound("closegui.wav", 0.6, 1.0f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack ms = context.getMatrices();

        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000f, 0.0001f, 0.05f);
        lastFrameNanos = now;
        screenHeightCached = this.height;

        if (!positionsInitialized) initPositions();

        float maxP = 0f;
        for (DropdownWindow w : windows) {
            w.updateOpenState(now, closing);
            maxP = Math.max(maxP, MathHelper.clamp(w.openAnim.getValue(), 0f, 1f));
        }

        if (maxP > 0.01f || !closing) {
            RenderUtils.drawRoundedRect(ms, -2, -2, this.width + 4, this.height + 4, 0,
                    ColorUtils.rgba(2, 4, 9, (int) (165 * maxP)));
        }

        for (DropdownWindow w : windows) {
            w.render(ms, this, mouseX, mouseY, dt);
        }

        ThemePanel.render(context, mouseX, mouseY, maxP);

        renderTooltip(ms, windows, mouseX, mouseY);
        renderFilterBar(ms, mouseX, mouseY, maxP);

        if (closing) {
            boolean done = true;
            for (DropdownWindow w : windows) {
                if (w.openAnim.getValue() > 0.015f) {
                    done = false;
                    break;
                }
            }
            if (done) {
                closing = false;
                close();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        if (closing) return true;

        // Клик по полю поиска — активировать поиск (ввод идёт в фильтр)
        float ffp = MathHelper.clamp(filterAnim.getValue(), 0f, 1f);
        float fmx = filterBoxX, fmy = filterBoxY + (1f - ffp) * 8f;
        if (HoveringUtils.isHovered(mx, my, fmx, fmy, filterBoxW, filterBoxH)) {
            filterActive = true;
            return true;
        }

        // Клик по панели тем
        if (ThemePanel.hit(mx, my)) {
            ThemePanel.mouseClick(mx, my, button);
            return true;
        }

        if (listeningBind != null) {
            applyBind(listeningBind, KeyBoardUtils.createMouseBind(button));
            listeningBind = null;
            return true;
        }

        for (int i = windows.size() - 1; i >= 0; i--) {
            DropdownWindow w = windows.get(i);
            if (!w.hitAny(mx, my)) continue;

            bringToFront(w);

            if (w.hitHeader(mx, my)) {
                // Окна закреплены: их нельзя перетаскивать и сворачивать
                return true;
            }

            if (w.hitBody(mx, my)) {
                if (activeTextSetting != null) activeTextSetting = null;
                w.handleClick(button, mx, my);
                return true;
            }

            return true;
        }

        activeTextSetting = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void bringToFront(DropdownWindow w) {
        windows.remove(w);
        windows.add(w);
    }

    private void renderTooltip(MatrixStack ms, List<DropdownWindow> list, int mouseX, int mouseY) {
        // Тултип не мешает во время активных взаимодействий
        boolean blocked = draggingSlider != null || listeningBind != null || activeTextSetting != null;
        Module cand = null;
        if (!blocked) {
            for (int i = list.size() - 1; i >= 0; i--) {
                cand = list.get(i).tooltipCandidate();
                if (cand != null) break;
            }
        }
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
        Font ft = Fonts.getFont("suisse", 11);
        if (ft == null) return;
        float tw = ft.getWidth(desc) + 18;
        float th = 22;

        float tx = MathHelper.clamp(mouseX + 14, 4, this.width - tw - 4);
        float ty = MathHelper.clamp(mouseY + 16, 4, this.height - th - 4);

        RenderUtils.drawShadow(ms, tx, ty, tw, th, 6f, 10f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.45f * al));
        RenderUtils.drawRoundedRect(ms, tx, ty, tw, th, 6f,
                ColorUtils.rgba(8, 11, 19, (int) (240 * al)));
        RenderUtils.drawRoundedRectOutline(ms, tx, ty, tw, th, 6f, 1f,
                ColorUtils.applyAlpha(accentAt(ty + 3f), (int) (120 * al)),
                ColorUtils.applyAlpha(accentAt(ty + 3f), (int) (120 * al)),
                ColorUtils.applyAlpha(accentAt(ty + th - 3f), (int) (80 * al)),
                ColorUtils.applyAlpha(accentAt(ty + th - 3f), (int) (80 * al)));
        RenderUtils.drawRoundedRect(ms, tx + 5, ty + 5.5f, 2.2f, th - 11f, 1.1f,
                ColorUtils.applyAlpha(accentAt(ty + th / 2f), 0.9f * al));
        ft.draw(ms, desc, tx + 13, ty + th / 2f - DropdownWindow.fh(11) / 2f,
                ColorUtils.rgba(233, 237, 245, (int) (245 * al)));
    }

    /** Поле поиска модулей — внизу по центру, над панелью цветов, чтобы не налезать на окна.
     *  Активируется кликом: тогда ввод идёт в фильтр, а чип не виден при неактивном поиске. */
    private void renderFilterBar(MatrixStack ms, int mouseX, int mouseY, float alpha) {
        if (closing || alpha < 0.05f) return;
        float bw = 210f;
        float bh = 26f;
        filterBoxW = bw;
        filterBoxH = bh;
        filterBoxX = (this.width - bw) / 2f;
        filterBoxY = this.height - 58f;

        boolean hov = HoveringUtils.isHovered(mouseX, mouseY, filterBoxX, filterBoxY, bw, bh);
        boolean act = filterActive;
        int out = act ? (int) (125 * alpha) : hov ? (int) (46 * alpha) : (int) (24 * alpha);

        // Рабочая анимация появления и плавного подъёма поля поиска
        filterAnim.update(act || hov ? 1f : 0f);
        float fp = MathHelper.clamp(filterAnim.getValue(), 0f, 1f);
        float fa = fp * fp;
        float fty = filterBoxY + (1f - fp) * 8f;

        RenderUtils.drawShadow(ms, filterBoxX, fty, bw, bh, 8f, 10f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.4f * alpha));
        RenderUtils.drawRoundedRect(ms, filterBoxX, fty, bw, bh, 9f,
                ColorUtils.rgba(8, 11, 19, (int) ((act ? 215 : 132) * alpha)));
        int accentCol = accentAt(fty + bh / 2f);
        RenderUtils.drawRoundedRectOutline(ms, filterBoxX, fty, bw, bh, 9f, 1f,
                ColorUtils.applyAlpha(accentCol, (int) (out * fa)), ColorUtils.applyAlpha(accentCol, (int) (out * fa)),
                ColorUtils.rgba(255, 255, 255, (int) (16 * alpha)), ColorUtils.rgba(255, 255, 255, (int) (16 * alpha)));

        Font ft = Fonts.getFont("suisse", 12);
        if (ft == null) return;
        float ty = fty + bh / 2f - DropdownWindow.fh(12) / 2f;
        if (act) {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            String shown = filter + (blink ? "|" : "");
            ft.draw(ms, shown, filterBoxX + 12, ty, ColorUtils.rgba(236, 240, 248, (int) (240 * alpha)));
        } else {
            ft.draw(ms, "Поиск модулей...", filterBoxX + 12, ty,
                    ColorUtils.rgba(132, 140, 156, (int) (165 * alpha * (0.7f + 0.3f * fa))));
        }
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int mx = (int) mouseX, my = (int) mouseY;

        if (draggingSlider != null) {
            applySlider(draggingSlider, mx);
            return true;
        }

        ThemePanel.mouseDrag(mx, my);
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
        if (ThemePanel.hit(mx, my)) {
            ThemePanel.mouseScroll((float) verticalAmount, mx, my);
            return true;
        }
        for (int i = windows.size() - 1; i >= 0; i--) {
            DropdownWindow w = windows.get(i);
            if (w.hitBody(mx, my)) {
                w.scrollTarget -= (float) verticalAmount * 24f;
                return true;
            }
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
            bind.getOwner().setKey(key); // Module.setKey синкнет и саму настройку
        } else {
            bind.setKey(key);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ввод в hex-поле темы имеет приоритет
        if (ThemePanel.hitHexFocused()) {
            ThemePanel.keyPressed(keyCode);
            return true;
        }
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

        if (filterActive && keyCode == GLFW.GLFW_KEY_BACKSPACE && !filter.isEmpty()) {
            filter = filter.substring(0, filter.length() - 1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (filterActive) {
                filterActive = false;
                filter = "";
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

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (ThemePanel.hitHexFocused()) {
            ThemePanel.charTyped(chr);
            return true;
        }
        if (listeningBind != null) return true;
        if (activeTextSetting != null) {
            if (chr >= 32 && chr != 127) {
                activeTextSetting.setText(activeTextSetting.get() + chr);
            }
            return true;
        }
        // Символы идут в поиск только когда поле поиска активно — иначе не трогаем фильтр
        if (filterActive && chr >= 32 && chr != 127) {
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
        filterActive = false;
    }

        @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Убрана 1-секундная чёрная затычка при первом открытии
        // context.fill(0, 0, this.width, this.height, ColorUtils.rgba(2, 4, 9, 255));
        // Убрали задержку отрисовки, рисуем сразу активный фон
        context.fill(0, 0, this.width, this.height, ColorUtils.rgba(2, 4, 9, 255));
    }
}
