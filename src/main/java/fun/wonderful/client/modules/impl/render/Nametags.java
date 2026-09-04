package fun.wonderful.client.modules.impl.render;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.Priority;
import fun.wonderful.api.events.implement.Event3DRender;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.replace.ReplaceUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.api.utils.render.fonts.msdf.Font;
import fun.wonderful.api.utils.render.fonts.msdf.Fonts;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.ui.clickgui.GuiIcons;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class Nametags extends Module {

    public static Nametags INSTANCE = new Nametags();

    private final FloatSetting yOffset = new FloatSetting("Смещение", 0.55f, 0f, 2f, 0.05f);
    private final BooleanSetting health = new BooleanSetting("Здоровье", true);
    private final BooleanSetting ping = new BooleanSetting("Пинг", true);
    private final BooleanSetting showDist = new BooleanSetting("Дистанция", true);
    private final FloatSetting maxDist = new FloatSetting("МаксДистанция", 64f, 8f, 256f, 4f);
    private final BooleanSetting shadowEnabled = new BooleanSetting("Тень", true);

    private final List<NametagData> nametags = new ArrayList<>();

    private static class NametagData {
        float screenX, screenY;
        PlayerListEntry entry;
        String playerName;
        String teamPrefix;
        String teamSuffix;
        int nameColor;
        String formattedName;
        float healthPercent;
        int healthDisplay;
        int ping;
        float distance;
    }

    public Nametags() {
        super("Nametags", "Красивые ники игроков в стиле клик-гуи", ModuleCategory.RENDER);
        addSettings(yOffset, health, ping, showDist, maxDist, shadowEnabled);
    }

    @EventLink(priority = Priority.HIGH)
    public void on3DRender(Event3DRender event) {
        nametags.clear();
        if (!isEnable() || mc.player == null || mc.world == null) return;

        Vec3d camPos = event.getCamera().getPos();
        Matrix4f posMat = event.getPositionMatrix();
        Matrix4f projMat = event.getProjectionMatrix();
        Matrix4f mvp = new Matrix4f(projMat).mul(posMat);

        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float maxDistSq = maxDist.get() * maxDist.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.isDead() || player.getHealth() <= 0) continue;
            if (mc.player.squaredDistanceTo(player) > maxDistSq) continue;

            Vec3d tagPos = player.getPos().add(0, player.getHeight() + yOffset.get(), 0);
            Vec3d rel = tagPos.subtract(camPos);

            Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0f);
            clip.mul(mvp);
            if (clip.w() < 0.01f) continue; // отсекаем только точки за камерой
            float ndcX = clip.x() / clip.w();
            float ndcY = clip.y() / clip.w();

            NametagData data = new NametagData();
            data.screenX = (ndcX * 0.5f + 0.5f) * sw;
            data.screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * sh;
            data.playerName = player.getName().getString();
            data.entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            data.ping = data.entry != null ? data.entry.getLatency() : -1;

            Scoreboard scoreboard = mc.world.getScoreboard();
            Team team = scoreboard.getTeam(player.getName().getString());
            String rawPrefix = team != null ? team.getPrefix().getString() : "";
            String rawSuffix = team != null ? team.getSuffix().getString() : "";
            // Кастомные глифы рангов сервера (ꔗ, ꔀ, ꕗꕘ ...) -> читаемый ранг с §-цветами
            data.teamPrefix = ReplaceUtils.replaceSymbols(rawPrefix);
            data.teamSuffix = ReplaceUtils.replaceSymbols(rawSuffix);
            data.nameColor = 0xFFF2F4FA;

            // Полное название с рангом: "[MODER] Nick"
            StringBuilder fn = new StringBuilder(data.teamPrefix);
            if (fn.length() > 0 && !fn.toString().endsWith(" ")) fn.append(' ');
            fn.append(data.playerName).append(data.teamSuffix);
            data.formattedName = fn.toString();

            float maxHealth = player.getMaxHealth();
            data.healthPercent = maxHealth > 0
                    ? MathHelper.clamp(player.getHealth() / maxHealth, 0f, 1f)
                    : 0f;
            data.healthDisplay = Math.round(player.getHealth());
            data.distance = (float) mc.player.distanceTo(player);
            nametags.add(data);
        }
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (!isEnable() || nametags.isEmpty()) return;

        DrawContext ctx = event.getContext();
        MatrixStack ms = ctx.getMatrices();
        Font nameFont = Fonts.getFont("suisse", 12);
        Font small = Fonts.getFont("suisse", 9);
        if (nameFont == null || small == null) return;

        // Компактная панель; все элементы выровнены по одной вертикальной оси
        float headSize = 12f;
        float padding = 6f;
        float gap = 5f;
        float panelH = 22f;
        float radius = 6f;

        for (NametagData data : nametags) {
            float nameW = nameFont.getWidth(data.formattedName);

            // HP-секция: [сердце] [число]
            boolean showHp = health.isState();
            String hpStr = showHp ? String.valueOf(data.healthDisplay) : "";
            float hpIconSize = 8f;
            float hpIconGap = 3.5f;
            float hpW = showHp ? hpIconSize + hpIconGap + nameFont.getWidth(hpStr) : 0f;

            // Инфо-строка справа: "45ms  12m"
            String infoStr = buildInfo(data);
            float infoW = infoStr.isEmpty() ? 0f : small.getWidth(infoStr);

            // Точная ширина контента: голова + имя + HP + инфо
            float contentW = headSize + gap + nameW;
            if (showHp) contentW += gap + hpW;
            if (infoW > 0f) contentW += gap + infoW;
            float panelW = contentW + padding * 2f;

            // Целые координаты — панель рисуется чётко и не «мылит»
            float panelX = Math.round(data.screenX - panelW / 2f);
            float panelY = Math.round(data.screenY - panelH - 2f);
            // Единая центральная ось для головы, текста и иконок
            float cy = panelY + panelH / 2f;
            // Текст — по целым пикселям: чёткая, ровная строка без дрожания
            float nameTy = Math.round(cy - 12f * 0.4023f / 2f);
            float smallTy = Math.round(cy - 9f * 0.4023f / 2f);

            if (shadowEnabled.isState()) {
                RenderUtils.drawShadow(ms, panelX, panelY, panelW, panelH, radius, 8f,
                        ColorUtils.applyAlpha(0xFF000000, 0.45f));
            }

            // Блюр-подложка + темная панель — стиль клик-ГУИ
            RenderUtils.drawBlur(ms, panelX, panelY, panelW, panelH, radius, 8f,
                    ColorUtils.rgba(7, 11, 21, 180));
            RenderUtils.drawRoundedRect(ms, panelX, panelY, panelW, panelH, radius,
                    ColorUtils.rgba(9, 12, 20, 246));

            int acTop = ThemePanel.accent(panelY);
            int acBot = ThemePanel.accent(panelY + panelH);
            int ar = (acTop >> 16) & 0xFF, ag = (acTop >> 8) & 0xFF, ab = acTop & 0xFF;
            int br = (acBot >> 16) & 0xFF, bg = (acBot >> 8) & 0xFF, bb = acBot & 0xFF;

            // Акцентный контур (градиент как в панелях категорий)
            RenderUtils.drawRoundedRectOutline(ms, panelX, panelY, panelW, panelH, radius, 1f,
                    ColorUtils.rgba(ar, ag, ab, 150), ColorUtils.rgba(ar, ag, ab, 150),
                    ColorUtils.rgba(br, bg, bb, 110), ColorUtils.rgba(br, bg, bb, 110));

            // --- Контент на центральной оси cy ---
            float cursor = panelX + padding;

            // Голова игрока + тонкая обводка в цвет акцента
            if (data.entry != null) {
                float headY = cy - headSize / 2f;
                RenderUtils.drawPlayerHead(ms, data.entry, cursor, headY, headSize, 3f);
                RenderUtils.drawRoundedRectOutline(ms, cursor, headY, headSize, headSize, 3f, 0.75f,
                        ColorUtils.rgba(ar, ag, ab, 140), ColorUtils.rgba(ar, ag, ab, 140),
                        ColorUtils.rgba(br, bg, bb, 100), ColorUtils.rgba(br, bg, bb, 100));
            }
            cursor += headSize + gap;

            // Полное название: ранг сервера (§-цвета префикса) + ник
            nameFont.draw(ms, data.formattedName, cursor, nameTy, data.nameColor);
            cursor += nameW;

            // HP: сердце в цвет темы + число (при HP < 35% — плавный уход в красный)
            if (showHp) {
                cursor += gap;
                int heartAccent = ThemePanel.accent(data.screenY);
                int heartColor = data.healthPercent >= 0.35f
                        ? heartAccent
                        : ColorUtils.interpolateColor(heartAccent, 0xFFFF4553,
                                (0.35f - data.healthPercent) / 0.35f);
                GuiIcons.draw(ms, "heart", cursor, cy - hpIconSize / 2f, hpIconSize, heartColor);
                cursor += hpIconSize + hpIconGap;
                nameFont.draw(ms, hpStr, cursor, nameTy, 0xFFDCE2EE);
                cursor += nameFont.getWidth(hpStr);
            }

            // Пинг/дистанция — прижаты к правому краю с тем же отступом, что и слева
            if (infoW > 0f) {
                small.draw(ms, infoStr, panelX + panelW - padding - infoW, smallTy,
                        ColorUtils.rgba(150, 157, 172, 215));
            }
        }
    }

    private String buildInfo(NametagData data) {
        StringBuilder sb = new StringBuilder();
        if (ping.isState() && data.ping >= 0) {
            sb.append(data.ping).append("ms");
        }
        if (showDist.isState()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(Math.round(data.distance)).append("m");
        }
        return sb.toString();
    }
}
