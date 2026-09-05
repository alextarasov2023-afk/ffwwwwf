package fun.wonderful.api.storages.implement;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Хит-эффекты: при ударе KillAura/Triggerbot вокруг прицела вспыхивают
 * молнии (midpoint-displacement, форма меняется каждые 60 мс — живое
 * мерцание), радиальный glow и расширяющееся кольцо.
 */
public class HitFxStorage implements QClient {

    public static HitFxStorage INSTANCE;

    private static final long DURATION_NANOS = 260_000_000L;

    private long lastAttackNanos;
    private boolean active;

    public HitFxStorage() {
        INSTANCE = this;
        EventInvoker.register(this);
    }

    /** Вызывается атакующими модулями в момент удара. */
    public static void onAttack() {
        if (INSTANCE != null) {
            INSTANCE.lastAttackNanos = System.nanoTime();
            INSTANCE.active = true;
        }
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (!active || mc.player == null) return;
        long age = System.nanoTime() - lastAttackNanos;
        if (age > DURATION_NANOS) {
            active = false;
            return;
        }

        float t = age / (float) DURATION_NANOS; // 0..1
        float fade = 1f - t;
        float cx = mc.getWindow().getScaledWidth() / 2f;
        float cy = mc.getWindow().getScaledHeight() / 2f;

        MatrixStack ms = event.getContext().getMatrices();
        int ac = ThemePanel.accentSolid();

        // Вспышка и расширяющееся кольцо в центре
        RenderUtils.drawGlow(ms, cx, cy, 16f + 30f * t,
                ColorUtils.applyAlpha(ac, 0.38f * fade));
        float ring = 10f + 46f * t;
        int ringCol = ColorUtils.applyAlpha(ac, 0.55f * fade);
        RenderUtils.drawRoundedRectOutline(ms, cx - ring, cy - ring, ring * 2f, ring * 2f, ring, 1.2f,
                ringCol, ringCol, ringCol, ringCol);

        // Молнии: форма меняется каждые 60 мс — живое мерцание
        Random random = new Random(age / 60_000_000L);
        for (int i = 0; i < 5; i++) {
            float ang = random.nextFloat() * (float) (Math.PI * 2);
            float len = 24f + 58f * t + random.nextFloat() * 26f;
            float ex = cx + MathHelper.cos(ang) * len;
            float ey = cy + MathHelper.sin(ang) * len;
            RenderUtils.drawLightning(ms, cx, cy, ex, ey, 1.1f, 8f * (1f - 0.5f * t),
                    ac, random.nextLong());
        }
    }
}
