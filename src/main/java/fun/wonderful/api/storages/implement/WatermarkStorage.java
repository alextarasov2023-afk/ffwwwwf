package fun.wonderful.api.storages.implement;

import lombok.Getter;
import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventRender;
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
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
import net.minecraft.client.network.PlayerListEntry;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * Watermark - beautiful functional HUD in the top-left corner.
 * Shows the client name plus live data: FPS, ping, coordinates,
 * server and session time. Fully animated in the click-gui style.
 */
public class WatermarkStorage implements QClient {

    public static WatermarkStorage INSTANCE;

    private static final float BG_PADDING = 10f;
    private static final float BORDER_RADIUS = 7f;
    private static final float TITLE_H = 22f;
    private static final float INFO_H = 15f;
    private static final float CHIP_PAD_X = 7f;
    private static final float CHIP_GAP = 5f;

    private Module watermarkModule;
    private long startNanos = System.nanoTime();

    private final AnimationUtils alphaAnimation = new AnimationUtils(0.0f, 8.5f, Easings.CUBIC_OUT);
    private final AnimationUtils slideAnimation = new AnimationUtils(0.0f, 14f, Easings.BACK_OUT);
    private final AnimationUtils fpsSmooth = new AnimationUtils(0.0f, 12f, Easings.LINEAR);

    public WatermarkStorage() {
        INSTANCE = this;
        EventInvoker.register(this);
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.getClass() == TestModules.Watermark.class) {
                this.watermarkModule = m;
                break;
            }
        }
    }

    // ===== Access to module settings =====

    private boolean isWatermarkEnabled() {
        if (watermarkModule == null || !watermarkModule.isEnable()) return false;
        for (var setting : watermarkModule.getSettings()) {
            if (setting.name().equals("Watermark") && setting instanceof BooleanSetting b) {
                return b.isState();
            }
        }
        return true;
    }

    // ===== Live data =====

    private int fps() {
        return mc.getCurrentFps();
    }

    private int ping() {
        if (mc.getNetworkHandler() == null || mc.player == null) return -1;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() : -1;
    }

    private String server() {
        if (mc.getCurrentServerEntry() != null) {
            String addr = mc.getCurrentServerEntry().address;
            return addr == null || addr.isEmpty() ? "Local" : addr;
        }
        return "Singleplayer";
    }

    private String sessionTime() {
        long secs = (System.nanoTime() - startNanos) / 1_000_000_000L;
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    private String coords() {
        if (mc.player == null) return "0 0 0";
        int x = (int) Math.floor(mc.player.getX());
        int y = (int) Math.floor(mc.player.getY());
        int z = (int) Math.floor(mc.player.getZ());
        return x + " " + y + " " + z;
    }

    // ===== Fonts =====

    private Font titleFont() {
        return Fonts.getFont("suisse", 13);
    }

    private Font chipFont() {
        return Fonts.getFont("suisse", 9);
    }


    // ===== Render =====

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) return;
        if (!isWatermarkEnabled()) return;

        alphaAnimation.update(1.0f);
        float alpha = MathUtils.clamp(alphaAnimation.getValue(), 0.0f, 1.0f);
        if (alpha <= 0.01f) return;
        float slide = slideAnimation.getValue();

        Font title = titleFont();
        Font chip = chipFont();
        if (title == null || chip == null) return;

        float fpsVal = fps();
        fpsSmooth.update(fpsVal);
        float fps = fpsSmooth.getValue();

        float titleW = title.getWidth("Wonderful");
        float w = BG_PADDING + 12f + titleW + BG_PADDING;
        float h = TITLE_H;

        // Collect info chips
        List<String> chips = new ArrayList<>();
        if (watermarkModule instanceof TestModules.Watermark wm) {
            if (wm.showFps()) chips.add("FPS " + Math.round(fps));
            if (wm.showPing() && ping() >= 0) chips.add("Ping " + ping() + "ms");
            if (wm.showCoords()) chips.add("XYZ " + coords());
            if (wm.showServer()) chips.add(server());
            if (wm.showTime()) chips.add(sessionTime());
        }
        // Info chip row below title
        if (!chips.isEmpty()) {
            float chipRowW = 0f;
            for (String c : chips) chipRowW += chip.getWidth(c);
            chipRowW += CHIP_GAP * (chips.size() - 1);
            w = Math.max(w, BG_PADDING * 2 + chipRowW);
            h += INFO_H;
        }

        float x = 8 + w * slide;
        float y = 8;
        var ms = event.getContext().getMatrices();

        // Background + blur
        RenderUtils.drawBlur(ms, x, y, w, h, 12f, 12f, 12f, 12f, 14f,
                ColorUtils.rgba(9, 12, 20, (int) (150 * alpha)));

        // Accent outline
        int accent = ColorUtils.getThemeColor();
        int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
        RenderUtils.drawRoundedRectOutline(ms, x, y, w, h, BORDER_RADIUS, 1.1f,
                ColorUtils.rgba(ar, ag, ab, (int) (140 * alpha)),
                ColorUtils.rgba(ar, ag, ab, (int) (60 * alpha)),
                ColorUtils.rgba(ar, ag, ab, (int) (60 * alpha)),
                ColorUtils.rgba(ar, ag, ab, (int) (60 * alpha)));

        // Gradient accent top line
        int accentLeft = ColorUtils.rgba(ar, ag, ab, (int) (240 * alpha));
        int accentRight = ColorUtils.rgba(
                Math.min(255, ar + 60), Math.max(0, ag - 15), Math.min(255, ab + 50),
                (int) (170 * alpha));
        RenderUtils.drawGradientRect(ms, x, y, w, 1.6f, 0.8f, accentLeft, accentRight);

        // Accent dot marker
        float cy = y + TITLE_H / 2f;
        float dotX = x + BG_PADDING + 2f;
        RenderUtils.drawRoundCircle(ms, dotX, cy, 6f,
                ColorUtils.rgba(ar, ag, ab, (int) (55 * alpha)));
        RenderUtils.drawRoundCircle(ms, dotX, cy, 3.2f,
                ColorUtils.rgba(ar, ag, ab, (int) (255 * alpha)));

        // Client name
        float textX = dotX + 7f;
        title.draw(ms, "Wonderful", textX, cy - 13f * 0.4023f / 2f,
                ColorUtils.rgba(240, 243, 250, (int) (255 * alpha)));

        // Version tag
        float verX = textX + title.getWidth("Wonderful") + 6f;
        chip.draw(ms, "v1.0", verX, cy - 9f * 0.4023f / 2f - 0.5f,
                ColorUtils.rgba(ar, ag, ab, (int) (225 * alpha)));

        // Info chips
        if (!chips.isEmpty()) {
            float chipY = y + TITLE_H;
            float chipX = x + BG_PADDING;
            for (String c : chips) {
                float cw = chip.getWidth(c);
                int color = ColorUtils.rgba(235, 240, 250, (int) (230 * alpha));
                if (c.startsWith("FPS")) {
                    int fpsC = Math.round(fps);
                    color = fpsC >= 120 ? ColorUtils.rgba(70, 220, 130, (int) (255 * alpha))
                            : fpsC >= 60 ? ColorUtils.rgba(245, 200, 70, (int) (255 * alpha))
                            : ColorUtils.rgba(235, 90, 90, (int) (255 * alpha));
                } else if (c.startsWith("Ping")) {
                    int p = ping();
                    color = p <= 60 ? ColorUtils.rgba(90, 220, 150, (int) (255 * alpha))
                            : p <= 140 ? ColorUtils.rgba(245, 200, 70, (int) (255 * alpha))
                            : ColorUtils.rgba(235, 90, 90, (int) (255 * alpha));
                }
                // Chip background pill
                RenderUtils.drawRoundedRect(ms, chipX, chipY + 0.5f,
                        cw + CHIP_PAD_X * 2, INFO_H - 4f,
                        3f, ColorUtils.rgba(255, 255, 255, (int) (24 * alpha)));
                chip.draw(ms, c, chipX + CHIP_PAD_X,
                        chipY + INFO_H - 3f - 9f * 0.4023f, color);
                chipX += cw + CHIP_PAD_X * 2 + CHIP_GAP;
            }
        }
    }

    public HudElement getSelectedElement() {
        if (watermarkModule != null) {
            for (var setting : watermarkModule.getSettings()) {
                if (setting.name().equals("Element") && setting instanceof ModeSetting mode) {
                    try {
                        return HudElement.valueOf(mode.getCurrent());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return HudElement.Name;
    }

    public void setSelectedElement(HudElement element) {
        // Handled by reading from module settings
    }

    public enum HudElement {
        Name("Name"),
        Server("Server");

        @Getter
        private final String displayName;

        HudElement(String displayName) {
            this.displayName = displayName;
        }
    }
}

