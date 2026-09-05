package fun.wonderful.api.storages.implement;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.api.utils.animation.AnimationUtils;
import fun.wonderful.api.utils.animation.Easings;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.math.MathUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.impl.TestModules;
import fun.wonderful.client.modules.impl.combat.KillAura;
import fun.wonderful.client.modules.impl.combat.Triggerbot;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Target HUD — панель текущей цели KillAura / Triggerbot справа по центру экрана.
 * Стиль клик-гуи: блюр-подложка, акцентный контур, голова цели, имя и
 * анимированная полоса здоровья с уходом в красный при низком HP.
 * После потери цели панель плавно затухает (снапшот данных остаётся для анимации).
 */
public class TargetHudStorage implements QClient {

    public static TargetHudStorage instance;

    /** Сколько держим панель после потери цели (мс -> наносекунды). */
    private static final long HOLD_NANOS = 600_000_000L;
    /** Дальше этой дистанции цель считается потерянной. */
    private static final float MAX_TRACK_DISTANCE = 12f;

    private Module targetHudModule;
    private LivingEntity target;
    private long lastSeenNanos;

    // Снапшот для рендера: цель может исчезнуть из мира, а панель ещё затухает
    private String targetName = "";
    private PlayerListEntry targetEntry;
    private float healthPercent = 1f;
    private float healthDisplay;
    private float distance;

    private final AnimationUtils appearAnim = new AnimationUtils(0f, 10f, Easings.CUBIC_OUT);
    private final AnimationUtils healthAnim = new AnimationUtils(1f, 8f, Easings.CUBIC_OUT);

    public TargetHudStorage() {
        instance = this;
        EventInvoker.register(this);
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.getClass() == TestModules.TargetHud.class) {
                this.targetHudModule = m;
                break;
            }
        }
    }

    /** Каждый тик: цель KillAura -> цель Triggerbot -> держим недавнюю цель. */
    @EventLink
    public void onUpdate(EventUpdate event) {
        if (targetHudModule == null || !targetHudModule.isEnable()) {
            target = null;
            return;
        }
        if (mc.player == null || mc.world == null) {
            target = null;
            return;
        }

        LivingEntity found = null;

        Entity kaTarget = KillAura.INSTANCE.getLastTarget();
        if (kaTarget instanceof LivingEntity living && living.isAlive()
                && mc.player.distanceTo(living) < MAX_TRACK_DISTANCE) {
            found = living;
        }

        if (found == null) {
            Entity tbTarget = Triggerbot.INSTANCE.getLastTarget();
            if (tbTarget instanceof LivingEntity living && living.isAlive()
                    && mc.player.distanceTo(living) < MAX_TRACK_DISTANCE) {
                found = living;
            }
        }

        if (found != null) {
            target = found;
            lastSeenNanos = System.nanoTime();
            snapshot(found);
        } else if (target != null && System.nanoTime() - lastSeenNanos > HOLD_NANOS) {
            target = null;
        }
    }

    private void snapshot(LivingEntity living) {
        targetName = living.getName().getString();
        targetEntry = mc.getNetworkHandler() != null
                ? mc.getNetworkHandler().getPlayerListEntry(living.getUuid())
                : null;
        float maxHealth = living.getMaxHealth();
        healthPercent = maxHealth > 0f ? MathHelper.clamp(living.getHealth() / maxHealth, 0f, 1f) : 0f;
        healthDisplay = Math.round(living.getHealth());
        distance = mc.player != null ? mc.player.distanceTo(living) : 0f;
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (targetHudModule == null || !targetHudModule.isEnable()) return;
        if (mc.player == null || mc.world == null) return;

        appearAnim.update(target != null ? 1f : 0f);
        float alpha = MathUtils.clamp(appearAnim.getValue(), 0f, 1f);
        if (alpha <= 0.02f) return;

        healthAnim.update(healthPercent);
        float shownHealth = MathUtils.clamp(healthAnim.getValue(), 0f, 1f);

        Font nameFont = Fonts.getFont("suisse", 13);
        Font small = Fonts.getFont("suisse", 9);
        if (nameFont == null || small == null) return;

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();

        float w = 140f;
        float h = 40f;
        float x = sw - w - 10f + (1f - alpha) * 26f;
        float y = sh * 0.35f + (1f - alpha) * 6f;

        MatrixStack ms = event.getContext().getMatrices();

        int acTop = ThemePanel.accentSolid();
        int acBot = ThemePanel.accentSolid();
        int ar = (acTop >> 16) & 0xFF, ag = (acTop >> 8) & 0xFF, ab = acTop & 0xFF;
        int br = (acBot >> 16) & 0xFF, bg = (acBot >> 8) & 0xFF, bb = acBot & 0xFF;

        // Тень + блюр + тёмная подложка + акцентный контур — стиль клик-гуи
        RenderUtils.drawShadow(ms, x, y, w, h, 8f, 10f,
                ColorUtils.applyAlpha(0xFF000000, 0.45f * alpha));
        RenderUtils.drawBlur(ms, x, y, w, h, 8f, 8f,
                ColorUtils.rgba(7, 11, 21, (int) (170 * alpha)));
        RenderUtils.drawRoundedRect(ms, x, y, w, h, 8f,
                ColorUtils.rgba(9, 12, 20, (int) (242 * alpha)));
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, 8f, 1f,
                ColorUtils.rgba(ar, ag, ab, (int) (150 * alpha)),
                ColorUtils.rgba(ar, ag, ab, (int) (150 * alpha)),
                ColorUtils.rgba(br, bg, bb, (int) (110 * alpha)),
                ColorUtils.rgba(br, bg, bb, (int) (110 * alpha)));

        // Голова цели + акцентное кольцо
        float headSize = 26f;
        float headX = x + 8f;
        float headY = y + (h - headSize) / 2f;
        if (targetEntry != null) {
            RenderUtils.drawPlayerHead(ms, targetEntry, headX, headY, headSize, 5f);
        }
        RenderUtils.drawRoundedRectOutline(ms, headX, headY, headSize, headSize, 5f, 0.9f,
                ColorUtils.rgba(ar, ag, ab, (int) (140 * alpha)),
                ColorUtils.rgba(ar, ag, ab, (int) (140 * alpha)),
                ColorUtils.rgba(br, bg, bb, (int) (100 * alpha)),
                ColorUtils.rgba(br, bg, bb, (int) (100 * alpha)));

        // Имя цели + HP справа
        float textX = headX + headSize + 9f;
        nameFont.draw(ms, targetName, textX, y + 9f,
                ColorUtils.rgba(240, 243, 250, (int) (245 * alpha)));

        String hpStr = ((int) healthDisplay) + " HP";
        small.draw(ms, hpStr, x + w - 10f - small.getWidth(hpStr), y + 10f,
                ColorUtils.rgba(214, 220, 233, (int) (225 * alpha)));

        // Полоса здоровья: акцент, при HP < 35% плавный уход в красный (как сердце в Nametags)
        float barY = y + 24f;
        float barW = w - (textX - x) - 10f;
        float barH = 5.5f;
        int heartAccent = ThemePanel.accentSolid();
        int barColor = shownHealth >= 0.35f
                ? heartAccent
                : ColorUtils.interpolateColor(heartAccent, 0xFFFF4553,
                        (0.35f - shownHealth) / 0.35f);

        RenderUtils.drawRoundedRect(ms, textX, barY, barW, barH, barH / 2f,
                ColorUtils.rgba(255, 255, 255, (int) (22 * alpha)));
        float fillW = barW * shownHealth;
        if (fillW > 0.5f) {
            RenderUtils.drawRoundedRect(ms, textX, barY, fillW, barH, barH / 2f,
                    ColorUtils.applyAlpha(barColor, 0.92f * alpha));
        }

        // Дистанция до цели — под полосой, мелким шрифтом
        String info = Math.round(distance) + "m";
        small.draw(ms, info, x + w - 10f - small.getWidth(info), barY + barH / 2f - 2f,
                ColorUtils.rgba(150, 157, 172, (int) (215 * alpha)));
    }
}
