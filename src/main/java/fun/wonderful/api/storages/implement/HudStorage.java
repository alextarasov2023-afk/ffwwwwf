package fun.wonderful.api.storages.implement;

import fun.wonderful.api.QClient;
import fun.wonderful.Wonderful;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easing;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.math.MathUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.impl.combat.KillAura;
import fun.wonderful.client.modules.impl.render.Hud;
import fun.wonderful.client.ui.clickgui.GuiIcons;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD-рендер в цвете темы: ватермарка, Target HUD, кейбинды, keystrokes, Staff List.
 * <p>
 * Режим редактирования: открой чат — курсор свободен, видны ВСЕ элементы
 * (даже выключенные, притушённые), у каждого подсветка и уголки:
 * тащить за панель — перемещение, за уголок — изменение размера.
 */
public class HudStorage implements QClient {

    public static HudStorage INSTANCE;

    private static final String[] ELEMENTS =
            {"watermark", "targethud", "keybinds", "stafflist", "keystrokes"};
    private static final String[] ELEMENT_NAMES =
            {"Ватермарка", "Target HUD", "Кейбинды", "Staff List", "Клавиши"};

    private long lastFrameNanos = System.nanoTime();

    /** Пул анимаций появления элементов и клавиш. */
    private final Map<String, AnimationUtils> anims = new HashMap<>();

    /** Раскладка: id -> [x, y, scale]. */
    private final Map<String, float[]> layout = new HashMap<>();
    private boolean layoutInit = false;

    /** Экранные прямоугольники элементов (для подсветки и кликов в редакторе). */
    private final Map<String, float[]> editRects = new HashMap<>();

    // ===== Перетаскивание / ресайз =====
    private String dragId;
    private boolean dragResize;
    private float dragCornerSignX, dragCornerSignY;
    private float startMx, startMy, startX, startY, startScale;
    private boolean prevPressed;

    // ===== Target HUD =====
    private Entity lastTarget;
    private long lastTargetSeen;
    private final AnimationUtils hpAnim = new AnimationUtils(20f, 10f, Easings.CUBIC_OUT);

    // ===== Keystrokes: CPS по фронтам нажатий =====
    private boolean lastLmb, lastRmb;
    private final Deque<Long> lmbCps = new ArrayDeque<>();
    private final Deque<Long> rmbCps = new ArrayDeque<>();

    public HudStorage() {
        INSTANCE = this;
        EventInvoker.register(this);
    }

    private AnimationUtils elem(String key, float speed, Easing ease) {
        return anims.computeIfAbsent(key, k -> new AnimationUtils(0f, speed, ease));
    }

    // ===== Текстовые хелперы (MSDF suisse, размеры >= 8) =====

    private static Font f(int size) {
        return Fonts.getFont("suisse", size);
    }

    private static void text(MatrixStack ms, int size, String s, float px, float py, int color) {
        Font fo = f(size);
        if (fo != null && s != null && !s.isEmpty()) fo.draw(ms, s, px, py + 1.5f - 0.0792f * size, color);
    }

    private static float tw(int size, String s) {
        Font fo = f(size);
        return fo == null ? 0 : fo.getWidth(s);
    }

    private static float fh(int size) {
        return size * 0.4023f;
    }

    private static String fit(String s, int size, float maxW) {
        if (maxW <= 8f) return "...";
        if (tw(size, s) <= maxW) return s;
        String cut = s;
        while (cut.length() > 1 && tw(size, cut + "...") > maxW) {
            cut = cut.substring(0, cut.length() - 2);
        }
        return cut + "...";
    }

    private static int ac() {
        return ThemePanel.accentSolid();
    }

    // ============================================================
    // Главный рендер
    // ============================================================

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - lastFrameNanos) / 1_000_000_000f, 0.0001f, 0.05f);
        lastFrameNanos = now;

        Hud hud = Hud.INSTANCE;
        if (hud == null) return;

        // Редактор: открыт чат — курсор свободен, показываем всё
        boolean edit = mc.currentScreen instanceof ChatScreen;

        if (!edit && !hud.isEnable()) {
            for (AnimationUtils a : anims.values()) a.update(0f);
            return;
        }

        pollCps();

        MatrixStack ms = event.getContext().getMatrices();
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        ensureLayout(sw, sh);

        float mx = mouseX();
        float my = mouseY();
        handleDrag(mx, my, edit);

        boolean[] on = {
                hud.watermark.isState(), hud.targetHud.isState(), hud.keybinds.isState(),
                hud.staffList.isState(), hud.keystrokes.isState()};

        editRects.clear();
        for (int i = 0; i < ELEMENTS.length; i++) {
            String id = ELEMENTS[i];
            boolean visible = on[i] || edit;
            AnimationUtils a = elem("el_" + id, 9f, Easings.CUBIC_OUT);
            a.update(visible ? 1f : 0f);
            float t = MathUtils.clamp(a.getValue(), 0f, 1f);
            // В редакторе выключенные элементы притушены
            float alpha = t * (edit && !on[i] ? 0.4f : 1f);
            if (alpha <= 0.01f) continue;

            float[] L = layout.get(id);
            float[] dims;
            ms.push();
            ms.translate(L[0], L[1], 0f);
            ms.scale(L[2], L[2], 1f);
            switch (id) {
                case "watermark" -> dims = drawWatermark(ms, alpha);
                case "targethud" -> dims = drawTargetHud(ms, alpha, edit);
                case "keybinds" -> dims = drawKeybinds(ms, alpha, edit);
                case "stafflist" -> dims = drawStaffList(ms, alpha, edit);
                default -> dims = drawKeystrokes(ms, alpha);
            }
            ms.pop();

            if (dims != null) {
                editRects.put(id, new float[]{L[0], L[1], dims[0] * L[2], dims[1] * L[2]});
            }
        }

        if (edit) {
            for (int i = 0; i < ELEMENTS.length; i++) {
                float[] r = editRects.get(ELEMENTS[i]);
                if (r != null) drawEditFrame(ms, ELEMENTS[i], ELEMENT_NAMES[i], r, mx, my);
            }
            drawEditHint(ms, sw, sh);
        }
    }

    private void ensureLayout(float sw, float sh) {
        if (layoutInit) return;
        layoutInit = true;
        layout.put("watermark", new float[]{8f, 8f, 1f});
        layout.put("targethud", new float[]{sw / 2f - 85f, 8f, 1f});
        layout.put("keybinds", new float[]{sw - 130f, 8f, 1f});
        layout.put("stafflist", new float[]{sw - 110f, 56f, 1f});
        layout.put("keystrokes", new float[]{10f, sh - 87f, 1f});
    }

    // ============================================================
    // Мышь и перетаскивание (опрос GLFW — работает и при открытом чате)
    // ============================================================

    private float mouseX() {
        return (float) (mc.mouse.getX() * (double) mc.getWindow().getScaledWidth()
                / Math.max(1.0, mc.getWindow().getWidth()));
    }

    private float mouseY() {
        return (float) (mc.mouse.getY() * (double) mc.getWindow().getScaledHeight()
                / Math.max(1.0, mc.getWindow().getHeight()));
    }

    private void handleDrag(float mx, float my, boolean edit) {
        boolean pressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (edit) {
            if (pressed && !prevPressed && dragId == null) {
                // Сначала уголки (ресайз), потом тело (перенос)
                for (String id : ELEMENTS) {
                    float[] r = editRects.get(id);
                    if (r == null) continue;
                    if (nearCorner(mx, my, r, -1, -1)) { startDrag(id, true, mx, my, -1, -1); break; }
                    if (nearCorner(mx, my, r, 1, -1)) { startDrag(id, true, mx, my, 1, -1); break; }
                    if (nearCorner(mx, my, r, -1, 1)) { startDrag(id, true, mx, my, -1, 1); break; }
                    if (nearCorner(mx, my, r, 1, 1)) { startDrag(id, true, mx, my, 1, 1); break; }
                }
                if (dragId == null) {
                    for (int i = ELEMENTS.length - 1; i >= 0; i--) {
                        float[] r = editRects.get(ELEMENTS[i]);
                        if (r != null && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                            startDrag(ELEMENTS[i], false, mx, my, 0, 0);
                            break;
                        }
                    }
                }
            }
            if (dragId != null && pressed) {
                float[] l = layout.get(dragId);
                if (dragResize) {
                    float delta = ((mx - startMx) * dragCornerSignX + (my - startMy) * dragCornerSignY) * 0.5f;
                    l[2] = MathHelper.clamp(startScale + delta * 0.02f, 0.6f, 1.8f);
                } else {
                    l[0] = startX + (mx - startMx);
                    l[1] = startY + (my - startMy);
                }
            } else {
                dragId = null;
            }
        } else {
            dragId = null;
        }
        prevPressed = pressed;
    }

    private void startDrag(String id, boolean resize, float mx, float my, float signX, float signY) {
        float[] l = layout.get(id);
        dragId = id;
        dragResize = resize;
        dragCornerSignX = signX;
        dragCornerSignY = signY;
        startMx = mx;
        startMy = my;
        startX = l[0];
        startY = l[1];
        startScale = l[2];
    }

    private boolean nearCorner(float mx, float my, float[] r, float sx, float sy) {
        float cx = sx < 0 ? r[0] : r[0] + r[2];
        float cy = sy < 0 ? r[1] : r[1] + r[3];
        return Math.abs(mx - cx) <= 6f && Math.abs(my - cy) <= 6f;
    }

    // ============================================================
    // Рамка редактирования: подсветка + уголки + подпись
    // ============================================================

    private void drawEditFrame(MatrixStack ms, String id, String name, float[] r, float mx, float my) {
        int ac = ac();
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 320.0);

        RenderUtils.drawRoundedRectOutline(ms, r[0] - 2f, r[1] - 2f, r[2] + 4f, r[3] + 4f, 6f, 1f,
                ColorUtils.applyAlpha(ac, (int) (140 * pulse)),
                ColorUtils.applyAlpha(ac, (int) (140 * pulse)),
                ColorUtils.applyAlpha(ac, (int) (90 * pulse)),
                ColorUtils.applyAlpha(ac, (int) (90 * pulse)));

        // Уголки: при наведении — крупнее и ярче
        for (int cx = 0; cx < 2; cx++) {
            for (int cy = 0; cy < 2; cy++) {
                float px = cx == 0 ? r[0] : r[0] + r[2];
                float py = cy == 0 ? r[1] : r[1] + r[3];
                float sx = cx == 0 ? -1f : 1f;
                float sy = cy == 0 ? -1f : 1f;
                boolean hot = Math.abs(mx - px) <= 6f && Math.abs(my - py) <= 6f;
                float sz = hot ? 4.6f : 3.2f;
                RenderUtils.drawRoundedRect(ms, px - sz / 2f, py - sz / 2f, sz, sz, 1.2f,
                        hot ? ColorUtils.rgba(245, 248, 252, 235)
                            : ColorUtils.applyAlpha(ac, (int) (235 * pulse)));
            }
        }

        text(ms, 8, name, r[0], r[1] - 11f, ColorUtils.applyAlpha(ac, 175));
    }

    private void drawEditHint(MatrixStack ms, float sw, float sh) {
        String hint = "Худ: тащи элемент · уголки — размер";
        float w = tw(9, hint) + 16f;
        float x = sw / 2f - w / 2f;
        float y = sh - 26f;
        RenderUtils.drawBlur(ms, x, y, w, 15f, 7f, 7f, 7f, 7f, 14f,
                ColorUtils.rgba(7, 10, 18, 170));
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, 15f, 7f, 1f,
                ColorUtils.applyAlpha(ac(), 70), ColorUtils.applyAlpha(ac(), 70),
                ColorUtils.rgba(255, 255, 255, 14), ColorUtils.rgba(255, 255, 255, 14));
        text(ms, 9, hint, x + 8f, y + 7.5f - fh(9) / 2f,
                ColorUtils.rgba(222, 228, 240, 220));
    }

    // ============================================================
    // Ватермарка: компактная пилюля, всё в цвете темы
    // ============================================================

    private float[] drawWatermark(MatrixStack ms, float t) {
        int ac = ac();
        Hud hud = Hud.INSTANCE;
        Font title = f(13);
        Font small = f(9);
        if (title == null || small == null) return null;

        List<String> parts = new ArrayList<>();
        if (hud.showFps.isState()) parts.add(mc.getCurrentFps() + " fps");
        if (hud.showPing.isState()) {
            int ping = ping();
            if (ping >= 0) parts.add(ping + " ms");
        }

        float w = 12f + 5f + 5f + tw(13, "wonderful") + 8f;
        for (String s : parts) w += tw(9, s) + 9f;
        w += 2f;
        float h = 24f;
        float cy = h / 2f;

        RenderUtils.drawShadow(ms, 0, 0, w, h, 8f, 7f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.35f * t));
        RenderUtils.drawBlur(ms, 0, 0, w, h, 11f, 11f, 11f, 11f, 14f,
                ColorUtils.rgba(7, 10, 18, (int) (160 * t)));
        RenderUtils.drawRoundedRectOutline(ms, 0, 0, w, h, 8f, 1f,
                ColorUtils.applyAlpha(ac, (int) (70 * t)),
                ColorUtils.applyAlpha(ac, (int) (70 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (14 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (14 * t)));

        RenderUtils.drawRoundCircle(ms, 14f, cy, 2.4f,
                ColorUtils.applyAlpha(ac, 0.95f * t));
        text(ms, 13, "wonderful", 22f, cy - fh(13) / 2f,
                ColorUtils.rgba(240, 243, 250, (int) (248 * t)));

        float px = 22f + tw(13, "wonderful") + 8f;
        if (!parts.isEmpty()) {
            RenderUtils.drawRoundedRect(ms, px, 6f, 1f, h - 12f, 0.5f,
                    ColorUtils.rgba(255, 255, 255, (int) (20 * t)));
            px += 9f;
        }
        // Значения — в цвете темы, без светофора
        for (String s : parts) {
            text(ms, 9, s, px, cy - fh(9) / 2f,
                    ColorUtils.applyAlpha(ac, 0.92f * t));
            px += tw(9, s) + 9f;
        }
        return new float[]{w, h};
    }

    private int ping() {
        if (mc.getNetworkHandler() == null || mc.player == null) return -1;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() : -1;
    }

    // ============================================================
    // Target HUD: цель KillAura, всё в цвете темы
    // ============================================================

    private float[] drawTargetHud(MatrixStack ms, float apIn, boolean edit) {
        KillAura ka = KillAura.INSTANCE;
        Entity target = null;
        if (ka != null && ka.isEnable() && ka.getLastTarget() != null) {
            Entity c = ka.getLastTarget();
            if (c instanceof LivingEntity le && le.isAlive() && !le.isRemoved()
                    && mc.player.distanceTo(c) < 40f) {
                target = c;
            }
        }
        if (target != null) {
            lastTarget = target;
            lastTargetSeen = System.currentTimeMillis();
        }
        boolean hold = edit || target != null
                || (lastTarget != null && System.currentTimeMillis() - lastTargetSeen < 650f);

        // Собственный фейд панели цели (плавное исчезновение после потери цели)
        AnimationUtils fade = elem("th_fade", 9f, Easings.CUBIC_OUT);
        fade.update(hold ? 1f : 0f);
        float fp = MathUtils.clamp(fade.getValue(), 0f, 1f);
        float ap = apIn * fp;
        if (ap <= 0.01f) return null;

        Entity shown = target != null ? target : lastTarget;
        boolean fake = shown == null;
        String name = fake ? "Цель"
                : fit(((LivingEntity) shown).getName().getString(), 12, 108f);

        float max = fake ? 20f : Math.max(1f, ((LivingEntity) shown).getMaxHealth());
        float hp = fake ? 20f : MathHelper.clamp(((LivingEntity) shown).getHealth(), 0f, max);
        hpAnim.update(hp);
        float hpShown = MathHelper.clamp(hpAnim.getValue(), 0f, max);
        float pct = hpShown / max;

        int ac = ac();
        String dist = fake ? "0.0 m"
                : String.format(java.util.Locale.ROOT, "%.1f m", mc.player.distanceTo(shown));
        String hpText = String.format(java.util.Locale.ROOT, "%.1f", hpShown);

        float w = 170f;
        float h = 42f;

        RenderUtils.drawShadow(ms, 0, 0, w, h, 8f, 7f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.35f * ap));
        RenderUtils.drawBlur(ms, 0, 0, w, h, 10f, 10f, 10f, 10f, 14f,
                ColorUtils.rgba(7, 10, 18, (int) (165 * ap)));
        RenderUtils.drawRoundedRectOutline(ms, 0, 0, w, h, 8f, 1f,
                ColorUtils.applyAlpha(ac, (int) (80 * ap)),
                ColorUtils.applyAlpha(ac, (int) (80 * ap)),
                ColorUtils.rgba(255, 255, 255, (int) (12 * ap)),
                ColorUtils.rgba(255, 255, 255, (int) (12 * ap)));

        GuiIcons.draw(ms, "heart", 11f, 9f, 12f, ColorUtils.applyAlpha(ac, 0.95f * ap));
        text(ms, 12, name, 29f, 11f, ColorUtils.rgba(240, 243, 250, (int) (246 * ap)));
        text(ms, 9, dist, w - 10f - tw(9, dist), 12f,
                ColorUtils.rgba(170, 177, 192, (int) (205 * ap)));

        float barX = 11f;
        float barY = h - 13f;
        float barW = w - 22f;
        float barH = 4.5f;
        RenderUtils.drawRoundedRect(ms, barX, barY, barW, barH, barH / 2f,
                ColorUtils.rgba(255, 255, 255, (int) (24 * ap)));
        float fw = barW * pct;
        if (fw > 0.5f) {
            RenderUtils.drawGradientRect(ms, barX, barY, fw, barH, barH / 2f,
                    ColorUtils.applyAlpha(ac, 0.75f * ap),
                    ColorUtils.applyAlpha(ColorUtils.interpolateColor(ac, 0xFFFFFFFF, 0.25f), 0.95f * ap),
                    true);
        }
        text(ms, 9, hpText, barX + barW - tw(9, hpText), barY - fh(9) - 3f,
                ColorUtils.applyAlpha(ac, 0.95f * ap));
        return new float[]{w, h};
    }

    // ============================================================
    // Кейбинды: включённые модули с биндами
    // ============================================================

    private float[] drawKeybinds(MatrixStack ms, float t, boolean edit) {
        List<Module> bound = new ArrayList<>();
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.isEnable() && m.getKey() != -1) bound.add(m);
        }
        if (bound.isEmpty() && !edit) return null;
        if (f(11) == null || f(9) == null) return null;

        boolean placeholder = bound.isEmpty();
        float rowH = 14f;
        int rows = Math.max(1, bound.size());

        // Ширина самой широкой строки
        float panelW = 0f;
        for (Module m : bound) {
            panelW = Math.max(panelW, tw(11, m.getName()) + 6f + tw(9,
                    fun.wonderful.api.utils.input.KeyBoardUtils.getBindName(m.getKey())) + 10f);
        }
        if (placeholder) panelW = tw(11, "Модуль") + 6f + tw(9, "X") + 10f;

        for (int i = 0; i < rows; i++) {
            float rt = MathUtils.clamp(t * 1.8f - i * 0.08f, 0f, 1f);
            float y = i * rowH + rowH / 2f;

            String name = placeholder ? "Модуль" : bound.get(i).getName();
            String key = placeholder ? "X"
                    : fun.wonderful.api.utils.input.KeyBoardUtils.getBindName(bound.get(i).getKey());
            float nameW = tw(11, name);
            float keyW = tw(9, key) + 10f;

            float rightX = panelW;
            RenderUtils.drawRoundedRect(ms, rightX - keyW, y - 6.5f, keyW, 13f, 6.5f,
                    ColorUtils.applyAlpha(ac(), 0.22f * rt));
            text(ms, 9, key, rightX - keyW + 5f, y - fh(9) / 2f,
                    ColorUtils.applyAlpha(ac(), 0.95f * rt));
            text(ms, 11, name, rightX - keyW - 6f - nameW, y - fh(11) / 2f,
                    ColorUtils.rgba(228, 233, 243, (int) (230 * rt)));
        }
        return new float[]{panelW, rows * rowH};
    }

    // ============================================================
    // Staff List: онлайн-стафф
    // ============================================================

    private float[] drawStaffList(MatrixStack ms, float t, boolean edit) {
        List<String> online = new ArrayList<>();
        if (mc.getNetworkHandler() != null && Wonderful.INSTANCE.staffStorage != null) {
            for (PlayerListEntry en : mc.getNetworkHandler().getPlayerList()) {
                String n = en.getProfile().getName();
                if (Wonderful.INSTANCE.staffStorage.isStaff(n)) online.add(n);
            }
        }
        if (online.isEmpty() && !edit) return null;
        boolean placeholder = online.isEmpty();
        if (placeholder) online.add("staff");

        Font head = f(8);
        Font body = f(10);
        if (head == null || body == null) return null;

        float w = tw(8, "STAFF") + 16f;
        for (String n : online) w = Math.max(w, tw(10, n) + 26f);
        float rowH = 13f;
        float h = 17f + online.size() * rowH;

        RenderUtils.drawShadow(ms, 0, 0, w, h, 7f, 6f,
                ColorUtils.applyAlpha(ColorUtils.rgba(0, 0, 0, 255), 0.35f * t));
        RenderUtils.drawBlur(ms, 0, 0, w, h, 9f, 9f, 9f, 9f, 14f,
                ColorUtils.rgba(7, 10, 18, (int) (150 * t)));
        RenderUtils.drawRoundedRectOutline(ms, 0, 0, w, h, 7f, 1f,
                ColorUtils.rgba(255, 255, 255, (int) (16 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (16 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (10 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (10 * t)));

        text(ms, 8, "STAFF", 9f, 5f, ColorUtils.rgba(158, 165, 180, (int) (200 * t)));
        RenderUtils.drawRoundedRect(ms, 8f, 15f, w - 16f, 1f, 0.5f,
                ColorUtils.rgba(255, 255, 255, (int) (14 * t)));

        for (int i = 0; i < online.size(); i++) {
            float rt = MathUtils.clamp(t * 1.8f - i * 0.08f, 0f, 1f);
            float ry = 17f + i * rowH + rowH / 2f;
            RenderUtils.drawRoundCircle(ms, 12f, ry, 1.9f,
                    ColorUtils.applyAlpha(ac(), 0.9f * rt));
            text(ms, 10, online.get(i), 19f, ry - fh(10) / 2f,
                    ColorUtils.rgba(235, 238, 246, (int) (235 * rt)));
        }
        return new float[]{w, h};
    }

    // ============================================================
    // Keystrokes: WASD + ЛКМ/ПКМ (CPS) + пробел
    // ============================================================

    private void pollCps() {
        boolean l = mc.options.attackKey.isPressed();
        boolean r = mc.options.useKey.isPressed();
        long now = System.currentTimeMillis();
        if (l && !lastLmb) lmbCps.addLast(now);
        if (r && !lastRmb) rmbCps.addLast(now);
        lastLmb = l;
        lastRmb = r;
        while (!lmbCps.isEmpty() && now - lmbCps.peekFirst() > 1000L) lmbCps.pollFirst();
        while (!rmbCps.isEmpty() && now - rmbCps.peekFirst() > 1000L) rmbCps.pollFirst();
    }

    private float[] drawKeystrokes(MatrixStack ms, float ap) {
        int ac = ac();
        float key = 20f;
        float gap = 3f;
        float half = (key * 3f + gap * 2f - gap) / 2f;
        float spaceH = 8f;

        boolean w = mc.options.forwardKey.isPressed();
        boolean aK = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();
        boolean sp = mc.options.jumpKey.isPressed();
        boolean lmb = mc.options.attackKey.isPressed();
        boolean rmb = mc.options.useKey.isPressed();

        // Ряд 1: W
        drawCap(ms, key + gap, 0f, key, key, "W", "W", 11, w, ap, ac);
        // Ряд 2: A S D
        float y2 = key + gap;
        drawCap(ms, 0f, y2, key, key, "A", "A", 11, aK, ap, ac);
        drawCap(ms, key + gap, y2, key, key, "S", "S", 11, s, ap, ac);
        drawCap(ms, (key + gap) * 2f, y2, key, key, "D", "D", 11, d, ap, ac);
        // Ряд 3: ЛКМ / ПКМ с CPS
        float y3 = y2 + key + gap;
        drawCap(ms, 0f, y3, half, key, "ЛКМ " + lmbCps.size(), "lmb", 9, lmb, ap, ac);
        drawCap(ms, half + gap, y3, half, key, "ПКМ " + rmbCps.size(), "rmb", 9, rmb, ap, ac);
        // Ряд 4: пробел
        float y4 = y3 + key + gap;
        drawCapSpace(ms, 0f, y4, key * 3f + gap * 2f, spaceH, sp, ap, ac);

        return new float[]{key * 3f + gap * 2f, y4 + spaceH};
    }

    private void drawCap(MatrixStack ms, float x, float y, float w, float h, String label,
                         String animKey, int fsz, boolean pressed, float ap, int ac) {
        AnimationUtils a = elem("key_" + animKey, 16f, Easings.CUBIC_OUT);
        a.update(pressed ? 1f : 0f);
        float k = MathUtils.clamp(a.getValue(), 0f, 1f);

        int idleBg = ColorUtils.rgba(9, 12, 20, (int) (165 * ap));
        int pressBg = ColorUtils.applyAlpha(ac, 0.38f * ap);
        RenderUtils.drawRoundedRect(ms, x, y, w, h, 5f,
                ColorUtils.interpolateColor(idleBg, pressBg, k));

        float cy = y + h / 2f;
        text(ms, fsz, label, x + w / 2f - tw(fsz, label) / 2f, cy - fh(fsz) / 2f,
                ColorUtils.rgba((int) (222 + 33 * k), (int) (228 + 27 * k),
                        (int) (240 + 15 * k), (int) ((225 + 30 * k) * ap)));
    }

    private void drawCapSpace(MatrixStack ms, float x, float y, float w, float h,
                              boolean pressed, float ap, int ac) {
        AnimationUtils a = elem("key_space", 16f, Easings.CUBIC_OUT);
        a.update(pressed ? 1f : 0f);
        float k = MathUtils.clamp(a.getValue(), 0f, 1f);

        int idleBg = ColorUtils.rgba(9, 12, 20, (int) (165 * ap));
        int pressBg = ColorUtils.applyAlpha(ac, 0.38f * ap);
        RenderUtils.drawRoundedRect(ms, x, y, w, h, h / 2f,
                ColorUtils.interpolateColor(idleBg, pressBg, k));
        float bw = 14f + 8f * k;
        RenderUtils.drawRoundedRect(ms, x + w / 2f - bw / 2f, y + h / 2f - 1f, bw, 2f, 1f,
                ColorUtils.rgba(222, 228, 240, (int) ((215 + 40 * k) * ap)));
    }
}
