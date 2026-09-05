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
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD-рендер: ватермарка (FPS/пинг), Target HUD (цель KillAura),
 * кейбинды (включённые модули с биндами), keystrokes (WASD/ЛКМ/ПКМ/пробел + CPS)
 * и список стаффа онлайн. Единый стиль клик-гуи: матовые панели, один акцент.
 */
public class HudStorage implements QClient {

    public static HudStorage INSTANCE;

    private long lastFrameNanos = System.nanoTime();

    /** Пул анимаций появления элементов и клавиш. */
    private final Map<String, AnimationUtils> anims = new HashMap<>();

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

    /** Обрезает текст с многоточием под ширину. */
    private static String fit(String s, int size, float maxW) {
        if (maxW <= 8f) return "...";
        if (tw(size, s) <= maxW) return s;
        String cut = s;
        while (cut.length() > 1 && tw(size, cut + "...") > maxW) {
            cut = cut.substring(0, cut.length() - 2);
        }
        return cut + "...";
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
        if (hud == null || !hud.isEnable()) {
            for (AnimationUtils a : anims.values()) a.update(0f);
            return;
        }

        pollCps();

        MatrixStack ms = event.getContext().getMatrices();
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();

        if (hud.watermark.isState()) drawWatermark(ms);
        float rightOffset = 0f;
        if (hud.keybinds.isState()) rightOffset = drawKeybinds(ms, sw) + 6f;
        if (hud.staffList.isState()) drawStaffList(ms, sw, rightOffset);
        if (hud.targetHud.isState()) drawTargetHud(ms, sw);
        if (hud.keystrokes.isState()) drawKeystrokes(ms, sh);
    }

    // ============================================================
    // Ватермарка: компактная пилюля слева сверху
    // ============================================================

    private void drawWatermark(MatrixStack ms) {
        AnimationUtils a = elem("wm", 9f, Easings.BACK_OUT);
        a.update(1f);
        float t = MathUtils.clamp(a.getValue(), 0f, 1f);
        if (t <= 0.01f) return;

        int ac = ThemePanel.accentSolid();
        Hud hud = Hud.INSTANCE;
        Font title = f(13);
        Font small = f(9);
        if (title == null || small == null) return;

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
        float x = 8f - (1f - t) * 16f;
        float y = 8f;
        float cy = y + h / 2f;

        RenderUtils.drawBlur(ms, x, y, w, h, 11f, 11f, 11f, 11f, 14f,
                ColorUtils.rgba(7, 10, 18, (int) (160 * t)));
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 8f, 1f,
                ColorUtils.applyAlpha(ac, (int) (70 * t)),
                ColorUtils.applyAlpha(ac, (int) (70 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (14 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (14 * t)));

        RenderUtils.drawRoundCircle(ms, x + 14f, cy, 2.4f,
                ColorUtils.applyAlpha(ac, 0.95f * t));
        text(ms, 13, "wonderful", x + 22f, cy - fh(13) / 2f,
                ColorUtils.rgba(240, 243, 250, (int) (248 * t)));

        float px = x + 22f + tw(13, "wonderful") + 8f;
        if (!parts.isEmpty()) {
            RenderUtils.drawRoundedRect(ms, px, y + 6f, 1f, h - 12f, 0.5f,
                    ColorUtils.rgba(255, 255, 255, (int) (20 * t)));
            px += 9f;
        }
        for (String s : parts) {
            int col = ColorUtils.rgba(214, 220, 232, (int) (225 * t));
            if (s.endsWith("fps")) {
                int v = mc.getCurrentFps();
                col = v >= 120 ? ColorUtils.rgba(96, 220, 140, (int) (235 * t))
                        : v >= 60 ? ColorUtils.rgba(240, 200, 90, (int) (235 * t))
                        : ColorUtils.rgba(235, 95, 95, (int) (235 * t));
            } else if (s.endsWith("ms")) {
                int v = ping();
                col = v <= 60 ? ColorUtils.rgba(96, 220, 140, (int) (235 * t))
                        : v <= 140 ? ColorUtils.rgba(240, 200, 90, (int) (235 * t))
                        : ColorUtils.rgba(235, 95, 95, (int) (235 * t));
            }
            text(ms, 9, s, px, cy - fh(9) / 2f, col);
            px += tw(9, s) + 9f;
        }
    }

    private int ping() {
        if (mc.getNetworkHandler() == null || mc.player == null) return -1;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() : -1;
    }

    // ============================================================
    // Кейбинды: включённые модули с биндами, справа сверху
    // ============================================================

    /** Рисует список, возвращает его высоту (для Staff List ниже). */
    private float drawKeybinds(MatrixStack ms, float sw) {
        List<Module> bound = new ArrayList<>();
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.isEnable() && m.getKey() != -1) bound.add(m);
        }

        AnimationUtils a = elem("kb", 10f, Easings.CUBIC_OUT);
        a.update(bound.isEmpty() ? 0f : 1f);
        float t = MathUtils.clamp(a.getValue(), 0f, 1f);
        if (t <= 0.01f || bound.isEmpty()) return 0f;

        float rowH = 14f;
        for (int i = 0; i < bound.size(); i++) {
            Module m = bound.get(i);
            float rt = MathUtils.clamp(t * 1.8f - i * 0.08f, 0f, 1f);
            float y = 8f + i * rowH;
            float slide = (1f - rt) * 10f;

            String name = m.getName();
            String key = fun.wonderful.api.utils.input.KeyBoardUtils.getBindName(m.getKey());
            float nameW = tw(11, name);
            float keyW = tw(9, key) + 10f;

            float rightX = sw - 10f + slide;
            // ключ-чип справа, имя левее
            RenderUtils.drawRoundedRect(ms, rightX - keyW, y + rowH / 2f - 7f, keyW, 13f, 6.5f,
                    ColorUtils.applyAlpha(ThemePanel.accentSolid(), 0.22f * rt));
            text(ms, 9, key, rightX - keyW + 5f, y + rowH / 2f - fh(9) / 2f,
                    ColorUtils.applyAlpha(ThemePanel.accentSolid(), 0.95f * rt));
            text(ms, 11, name, rightX - keyW - 6f - nameW, y + rowH / 2f - fh(11) / 2f,
                    ColorUtils.rgba(228, 233, 243, (int) (230 * rt)));
        }
        return bound.size() * rowH;
    }

    // ============================================================
    // Staff List: онлайн-стафф справа (под кейбиндами)
    // ============================================================

    private void drawStaffList(MatrixStack ms, float sw, float topOffset) {
        if (mc.getNetworkHandler() == null) return;
        StaffStorage staff = Wonderful.INSTANCE.staffStorage;
        if (staff == null || staff.isEmpty()) return;

        List<String> online = new ArrayList<>();
        for (PlayerListEntry en : mc.getNetworkHandler().getPlayerList()) {
            String n = en.getProfile().getName();
            if (staff.isStaff(n)) online.add(n);
        }
        if (online.isEmpty()) return;

        AnimationUtils a = elem("staff", 9f, Easings.CUBIC_OUT);
        a.update(1f);
        float t = MathUtils.clamp(a.getValue(), 0f, 1f);
        if (t <= 0.01f) return;

        Font head = f(8);
        Font body = f(10);
        if (head == null || body == null) return;

        float w = tw(8, "STAFF") + 16f;
        for (String n : online) w = Math.max(w, tw(10, n) + 26f);
        float rowH = 13f;
        float h = 17f + online.size() * rowH;
        float x = sw - 10f - w + (1f - t) * 10f;
        float y = 8f + topOffset;

        RenderUtils.drawBlur(ms, x, y, w, h, 9f, 9f, 9f, 9f, 14f,
                ColorUtils.rgba(7, 10, 18, (int) (150 * t)));
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 7f, 1f,
                ColorUtils.rgba(255, 255, 255, (int) (16 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (16 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (10 * t)),
                ColorUtils.rgba(255, 255, 255, (int) (10 * t)));

        text(ms, 8, "STAFF", x + 9f, y + 5f,
                ColorUtils.rgba(158, 165, 180, (int) (200 * t)));
        RenderUtils.drawRoundedRect(ms, x + 8f, y + 15f, w - 16f, 1f, 0.5f,
                ColorUtils.rgba(255, 255, 255, (int) (14 * t)));

        for (int i = 0; i < online.size(); i++) {
            float rt = MathUtils.clamp(t * 1.8f - i * 0.08f, 0f, 1f);
            float ry = y + 17f + i * rowH + rowH / 2f;
            RenderUtils.drawRoundCircle(ms, x + 12f, ry, 1.9f,
                    ColorUtils.rgba(240, 170, 80, (int) (235 * rt)));
            text(ms, 10, online.get(i), x + 19f, ry - fh(10) / 2f,
                    ColorUtils.rgba(235, 238, 246, (int) (235 * rt)));
        }
    }

    // ============================================================
    // Target HUD: цель KillAura, сверху по центру
    // ============================================================

    private void drawTargetHud(MatrixStack ms, float sw) {
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
        boolean hold = target != null
                || (lastTarget != null && System.currentTimeMillis() - lastTargetSeen < 650f);

        AnimationUtils a = elem("th", 9f, Easings.CUBIC_OUT);
        a.update(hold ? 1f : 0f);
        float ap = MathUtils.clamp(a.getValue(), 0f, 1f);
        if (ap <= 0.01f) return;
        if (!(lastTarget instanceof LivingEntity le)) return;

        float max = Math.max(1f, le.getMaxHealth());
        float hp = MathHelper.clamp(le.getHealth(), 0f, max);
        hpAnim.update(hp);
        float hpShown = MathHelper.clamp(hpAnim.getValue(), 0f, max);
        float pct = hpShown / max;

        int hpCol = ColorUtils.interpolateColor(
                ColorUtils.rgba(84, 214, 132, 255),
                ColorUtils.rgba(240, 84, 84, 255),
                1f - pct);

        String name = fit(le.getName().getString(), 12, 108f);
        String dist = String.format(java.util.Locale.ROOT, "%.1f m", mc.player.distanceTo(le));
        String hpText = String.format(java.util.Locale.ROOT, "%.1f", hpShown);

        float w = 170f;
        float h = 42f;
        float x = sw / 2f - w / 2f;
        float y = 8f - (1f - ap) * 12f;

        RenderUtils.drawBlur(ms, x, y, w, h, 10f, 10f, 10f, 10f, 14f,
                ColorUtils.rgba(7, 10, 18, (int) (165 * ap)));
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 8f, 1f,
                ColorUtils.applyAlpha(hpCol, (int) (80 * ap)),
                ColorUtils.applyAlpha(hpCol, (int) (80 * ap)),
                ColorUtils.rgba(255, 255, 255, (int) (12 * ap)),
                ColorUtils.rgba(255, 255, 255, (int) (12 * ap)));

        // Сердце + имя + дистанция
        GuiIcons.draw(ms, "heart", x + 11f, y + 9f, 12f,
                ColorUtils.applyAlpha(hpCol, 0.95f * ap));
        text(ms, 12, name, x + 29f, y + 11f,
                ColorUtils.rgba(240, 243, 250, (int) (246 * ap)));
        text(ms, 9, dist, x + w - 10f - tw(9, dist), y + 12f,
                ColorUtils.rgba(170, 177, 192, (int) (205 * ap)));

        // Полоса здоровья
        float barX = x + 11f;
        float barY = y + h - 13f;
        float barW = w - 22f;
        float barH = 4.5f;
        RenderUtils.drawRoundedRect(ms, barX, barY, barW, barH, barH / 2f,
                ColorUtils.rgba(255, 255, 255, (int) (24 * ap)));
        float fw = barW * pct;
        if (fw > 0.5f) {
            RenderUtils.drawGradientRect(ms, barX, barY, fw, barH, barH / 2f,
                    ColorUtils.applyAlpha(hpCol, 0.75f * ap),
                    ColorUtils.applyAlpha(ColorUtils.interpolateColor(hpCol, 0xFFFFFFFF, 0.25f), 0.95f * ap),
                    true);
        }
        text(ms, 9, hpText, barX + barW - tw(9, hpText), barY - fh(9) - 3f,
                ColorUtils.applyAlpha(hpCol, 0.95f * ap));
    }

    // ============================================================
    // Keystrokes: WASD + ЛКМ/ПКМ (CPS) + пробел, слева внизу
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

    private void drawKeystrokes(MatrixStack ms, float sh) {
        AnimationUtils a = elem("ks", 9f, Easings.CUBIC_OUT);
        a.update(1f);
        float ap = MathUtils.clamp(a.getValue(), 0f, 1f);
        if (ap <= 0.01f) return;

        int ac = ThemePanel.accentSolid();
        float key = 20f;
        float gap = 3f;
        float half = (key * 3f + gap * 2f - gap) / 2f;
        float spaceH = 8f;
        float ph = key * 3f + gap * 3f + spaceH;
        float px = 10f;
        float py = sh - ph - 10f + (1f - ap) * 12f;

        boolean w = mc.options.forwardKey.isPressed();
        boolean aK = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();
        boolean sp = mc.options.jumpKey.isPressed();
        boolean lmb = mc.options.attackKey.isPressed();
        boolean rmb = mc.options.useKey.isPressed();

        // Ряд 1: W
        drawCap(ms, px + key + gap, py, key, key, "W", "W", 11, w, ap, ac);
        // Ряд 2: A S D
        float y2 = py + key + gap;
        drawCap(ms, px, y2, key, key, "A", "A", 11, aK, ap, ac);
        drawCap(ms, px + key + gap, y2, key, key, "S", "S", 11, s, ap, ac);
        drawCap(ms, px + (key + gap) * 2f, y2, key, key, "D", "D", 11, d, ap, ac);
        // Ряд 3: ЛКМ / ПКМ с CPS
        float y3 = y2 + key + gap;
        drawCap(ms, px, y3, half, key, "ЛКМ " + lmbCps.size(), "lmb", 9, lmb, ap, ac);
        drawCap(ms, px + half + gap, y3, half, key, "ПКМ " + rmbCps.size(), "rmb", 9, rmb, ap, ac);
        // Ряд 4: пробел
        float y4 = y3 + key + gap;
        drawCapSpace(ms, px, y4, key * 3f + gap * 2f, spaceH, sp, ap, ac);
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
        // Светлая полоска по центру, ширина дышит при нажатии
        float bw = 14f + 8f * k;
        RenderUtils.drawRoundedRect(ms, x + w / 2f - bw / 2f, y + h / 2f - 1f, bw, 2f, 1f,
                ColorUtils.rgba(222, 228, 240, (int) ((215 + 40 * k) * ap)));
    }
}
