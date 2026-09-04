package fun.wonderful.api.utils.notification;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;

/**
 * Рендер уведомлений (NotificationManager) — стопка панелей в правом верхнем углу.
 * Стиль клик-гуи: блюр-подложка + тёмная панель + акцентный контур + левая акцентная
 * полоса. Появление — плавный выезд справа с ease-out, исчезновение — затухание
 * в конце жизни уведомления. Время жизни управляется NotificationManager.DURATION_MS.
 */
public class NotificationRenderer implements QClient {

    /** Максимум одновременно видимых панелей — старые лишние просто не рисуем. */
    private static final int MAX_VISIBLE = 5;
    private static final float APPEAR_MS = 220f;
    private static final float FADE_MS = 320f;

    public NotificationRenderer() {
        EventInvoker.register(this);
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) return;

        List<NotificationManager.Entry> entries = NotificationManager.getActive();
        if (entries.isEmpty()) return;

        Font titleFont = Fonts.getFont("suisse", 12);
        Font small = Fonts.getFont("suisse", 9);
        if (titleFont == null || small == null) return;

        MatrixStack ms = event.getContext().getMatrices();
        float sw = mc.getWindow().getScaledWidth();

        float h = 26f;
        float gap = 6f;
        float padX = 9f;
        float y = 10f;
        long now = System.currentTimeMillis();
        int drawn = 0;

        for (NotificationManager.Entry entry : entries) {
            if (drawn >= MAX_VISIBLE) break;

            long t = now - entry.startTime;
            float appear = easeOutCubic(clamp01(t / APPEAR_MS));
            float fade = clamp01((NotificationManager.DURATION_MS - t) / FADE_MS);
            float alpha = appear * fade;
            if (alpha <= 0.02f) continue;

            String title = entry.isCustom() ? entry.customText : entry.moduleName;
            boolean showStatus = !entry.isCustom();
            String status = showStatus ? (entry.enabled ? "вкл" : "выкл") : "";

            float titleW = titleFont.getWidth(title);
            float statusW = status.isEmpty() ? 0f : small.getWidth(status) + 8f;
            float w = padX * 2f + titleW + statusW + 5f;

            // Выезд справа при появлении, лёгкий сдвиг вправо при затухании
            float x = sw - w - 10f + (1f - appear) * (w * 0.45f) + (1f - fade) * 16f;

            drawPanel(ms, x, y, w, h, alpha);
            drawContent(ms, x, y, w, h, padX, title, status, entry.enabled, alpha, titleFont, small);

            y += h + gap;
            drawn++;
        }
    }

    private void drawPanel(MatrixStack ms, float x, float y, float w, float h, float alpha) {
        int acTop = ThemePanel.accent(y + 2f);
        int acBot = ThemePanel.accent(y + h - 2f);
        int ar = (acTop >> 16) & 0xFF, ag = (acTop >> 8) & 0xFF, ab = acTop & 0xFF;
        int br = (acBot >> 16) & 0xFF, bg = (acBot >> 8) & 0xFF, bb = acBot & 0xFF;

        RenderUtils.drawShadow(ms, x, y, w, h, 7f, 8f,
                ColorUtils.applyAlpha(0xFF000000, 0.4f * alpha));
        RenderUtils.drawBlur(ms, x, y, w, h, 7f, 7f,
                ColorUtils.rgba(7, 11, 21, (int) (165 * alpha)));
        RenderUtils.drawRoundedRect(ms, x, y, w, h, 7f,
                ColorUtils.rgba(9, 12, 20, (int) (240 * alpha)));
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 7f, 1f,
                ColorUtils.rgba(ar, ag, ab, (int) (140 * alpha)),
                ColorUtils.rgba(ar, ag, ab, (int) (140 * alpha)),
                ColorUtils.rgba(br, bg, bb, (int) (100 * alpha)),
                ColorUtils.rgba(br, bg, bb, (int) (100 * alpha)));

        // Левая акцентная полоса — как у тултипов клик-гуи
        RenderUtils.drawRoundedRect(ms, x + 4.5f, y + 5f, 2f, h - 10f, 1f,
                ColorUtils.applyAlpha(ThemePanel.accent(y + h / 2f), 0.9f * alpha));
    }

    private void drawContent(MatrixStack ms, float x, float y, float w, float h, float padX,
                             String title, String status, boolean enabled, float alpha,
                             Font titleFont, Font small) {
        float textX = x + padX + 3f;
        float cy = y + h / 2f;

        titleFont.draw(ms, title, textX, cy - 12f * 0.4023f / 2f,
                ColorUtils.rgba(238, 242, 250, (int) (242 * alpha)));

        if (!status.isEmpty()) {
            int statusColor = enabled
                    ? ColorUtils.applyAlpha(ThemePanel.accent(cy), 0.95f * alpha)
                    : ColorUtils.rgba(140, 148, 164, (int) (200 * alpha));
            small.draw(ms, status, x + w - padX - small.getWidth(status),
                    cy - 9f * 0.4023f / 2f, statusColor);
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }
}
