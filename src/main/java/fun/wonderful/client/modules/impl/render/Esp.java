package fun.wonderful.client.modules.impl.render;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.Priority;
import fun.wonderful.api.events.implement.Event3DRender;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.ui.clickgui.ThemePanel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP — обводка целей: 2D-боксы по проекции хитбокса и трейсеры от прицела.
 * Проекция — как в Nametags (proj * positionMatrix, точка от камеры),
 * отрисовка в HUD-проходе: аккуратные угловые боксы + линии с затуханием
 * по дистанции. Цвет — акцент клиента.
 */
public class Esp extends Module {

    public static Esp INSTANCE = new Esp();

    public final BooleanSetting boxes = new BooleanSetting("Боксы", true);
    public final BooleanSetting tracers = new BooleanSetting("Трейсеры", true);
    public final BooleanSetting players = new BooleanSetting("Игроки", true);
    public final BooleanSetting mobs = new BooleanSetting("Мобы", false);
    public final BooleanSetting animals = new BooleanSetting("Животные", false);
    public final FloatSetting maxDist = new FloatSetting("Дистанция", 64f, 16f, 160f, 4f);

    private static final class Mark {
        float minX, minY, maxX, maxY;
        float cx, cy;
        float dist;
    }

    private final List<Mark> marks = new ArrayList<>();

    public Esp() {
        super("ESP", "Боксы и трейсеры на игроках и мобах", ModuleCategory.RENDER);
        addSettings(boxes, tracers, players, mobs, animals, maxDist);
    }

    /** World-проход: проекция 8 углов хитбокса в экранные координаты. */
    @EventLink(priority = Priority.HIGH)
    public void on3DRender(Event3DRender event) {
        marks.clear();
        if (!isEnable() || mc.player == null || mc.world == null) return;

        Matrix4f mvp = new Matrix4f(event.getProjectionMatrix()).mul(event.getPositionMatrix());
        Vec3d camPos = event.getCamera().getPos();
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float maxSq = maxDist.get() * maxDist.get();

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity living)) continue;
            if (living.isDead() || living.getHealth() <= 0 || living.isInvisible()) continue;
            if (e instanceof ArmorStandEntity) continue;
            if (mc.player.squaredDistanceTo(e) > maxSq) continue;
            if (!isTarget(e)) continue;

            // Интерполированный хитбокс (плавный на движении цели)
            double ix = MathHelper.lerp(event.getTickDelta(), e.prevX, e.getX());
            double iy = MathHelper.lerp(event.getTickDelta(), e.prevY, e.getY());
            double iz = MathHelper.lerp(event.getTickDelta(), e.prevZ, e.getZ());
            Box box = e.getBoundingBox().offset(ix - e.getX(), iy - e.getY(), iz - e.getZ());

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean behind = false;

            for (int i = 0; i < 8 && !behind; i++) {
                double bx = (i & 1) == 0 ? box.minX : box.maxX;
                double by = (i & 2) == 0 ? box.minY : box.maxY;
                double bz = (i & 4) == 0 ? box.minZ : box.maxZ;
                Vector4f clip = new Vector4f(
                        (float) (bx - camPos.x), (float) (by - camPos.y), (float) (bz - camPos.z), 1.0f);
                clip.mul(mvp);
                if (clip.w() < 0.01f) {
                    behind = true;
                    break;
                }
                float sx = (clip.x() / clip.w() * 0.5f + 0.5f) * sw;
                float sy = (1f - (clip.y() / clip.w() * 0.5f + 0.5f)) * sh;
                minX = Math.min(minX, sx);
                minY = Math.min(minY, sy);
                maxX = Math.max(maxX, sx);
                maxY = Math.max(maxY, sy);
            }
            if (behind || maxX <= minX) continue;

            Mark m = new Mark();
            m.minX = minX;
            m.minY = minY;
            m.maxX = maxX;
            m.maxY = maxY;
            m.cx = (minX + maxX) / 2f;
            m.cy = (minY + maxY) / 2f;
            m.dist = mc.player.distanceTo(e);
            marks.add(m);
        }
    }

    /** HUD-проход: угловые боксы и трейсеры от прицела. */
    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (!isEnable() || mc.player == null || marks.isEmpty()) return;
        MatrixStack ms = event.getContext().getMatrices();

        int ac = ThemePanel.accentSolid();
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        float maxD = maxDist.get();

        for (Mark m : marks) {
            // Затухание по дистанции: рядом — ярко, у лимита — тает
            float fade = MathHelper.clamp(1f - (m.dist / maxD) * 0.75f, 0.25f, 1f);
            int col = ColorUtils.applyAlpha(ac, 0.85f * fade);

            if (tracers.isState()) {
                RenderUtils.drawLine(ms, sw / 2f, sh / 2f, m.cx, m.maxY, 1f,
                        ColorUtils.applyAlpha(ac, 0.55f * fade));
            }

            if (boxes.isState()) {
                // Угловая обводка: короткие уголки вместо полного прямоугольника
                float w = m.maxX - m.minX;
                float h = m.maxY - m.minY;
                float corner = Math.min(w, h) * 0.28f;
                float lw = 1.3f;

                RenderUtils.drawLine(ms, m.minX, m.minY, m.minX + corner, m.minY, lw, col);
                RenderUtils.drawLine(ms, m.minX, m.minY, m.minX, m.minY + corner, lw, col);

                RenderUtils.drawLine(ms, m.maxX, m.minY, m.maxX - corner, m.minY, lw, col);
                RenderUtils.drawLine(ms, m.maxX, m.minY, m.maxX, m.minY + corner, lw, col);

                RenderUtils.drawLine(ms, m.minX, m.maxY, m.minX + corner, m.maxY, lw, col);
                RenderUtils.drawLine(ms, m.minX, m.maxY, m.minX, m.maxY - corner, lw, col);

                RenderUtils.drawLine(ms, m.maxX, m.maxY, m.maxX - corner, m.maxY, lw, col);
                RenderUtils.drawLine(ms, m.maxX, m.maxY, m.maxX, m.maxY - corner, lw, col);
            }
        }
    }

    private boolean isTarget(Entity e) {
        if (e instanceof PlayerEntity) return players.isState();
        if (e instanceof HostileEntity) return mobs.isState();
        if (e instanceof AnimalEntity) return animals.isState();
        return false;
    }
}
