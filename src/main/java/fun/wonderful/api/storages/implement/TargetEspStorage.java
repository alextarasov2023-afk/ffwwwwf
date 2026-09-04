package fun.wonderful.api.storages.implement;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.Event3DRender;
import fun.wonderful.api.events.implement.EventRender;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.api.utils.color.ColorUtils;
import fun.wonderful.api.utils.render.RenderUtils;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.impl.TestModules;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Target ESP — метка над игроком, на которого наведён прицел.
 * При наведении курсора на другого игрока над его головой рисуется
 * пульсирующий светящийся ромб-кристалл (2D-HUD метка, спроецированная
 * из 3D-позиции цели через матрицу камеры).
 */
public class TargetEspStorage implements QClient {

    public static TargetEspStorage INSTANCE;

    private static final long HOVER_LEAVE_NANOS = 250_000_000L; // сколько держать метку после увода взгляда

    private LivingEntity target;
    private long lastHoverNanos;
    private Module targetEspModule;
    private float screenX = Float.NaN;
    private float screenY = Float.NaN;

    public TargetEspStorage() {
        INSTANCE = this;
        EventInvoker.register(this);
        for (Module m : ModuleClass.INSTANCE.getObject()) {
            if (m.getClass() == TestModules.TargetEsp.class) {
                this.targetEspModule = m;
                break;
            }
        }
    }

    /** Каждый тик: определяем цель по наведению прицела на другого игрока. */
    @EventLink
    public void onUpdate(EventUpdate event) {
        if (targetEspModule == null || !targetEspModule.isEnable()) return;
        if (mc.player == null || mc.world == null) return;

        LivingEntity hover = null;
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le) {
            if (le != mc.player && le instanceof PlayerEntity) {
                hover = le;
            }
        }

        if (hover != null) {
            target = hover;
            lastHoverNanos = System.nanoTime();
        }
    }

    /**
     * World-render: проецируем точку над головой цели в экранные координаты
     * (Event3DRender — фактические position/projection матрицы кадра, та же
     * математика, что в Nametags). Сама метка рисуется позже в 2D (HUD),
     * чтобы рендер был гарантированно виден.
     */
    @EventLink
    public void onRender3D(Event3DRender event) {
        if (targetEspModule == null || !targetEspModule.isEnable()) return;
        if (mc.player == null || mc.world == null) return;

        boolean inWorld = target != null
                && target.isAlive()
                && !target.isRemoved()
                && mc.world.getEntityById(target.getId()) != null;

        // Метка стоит, пока наведён (постоянно) либо недавно уведён взгляд
        boolean holdHover = System.nanoTime() - lastHoverNanos < HOVER_LEAVE_NANOS;
        if (!inWorld || !holdHover) {
            target = null;
            screenX = Float.NaN;
            screenY = Float.NaN;
            return;
        }

        Vec3d basePos = interpolate(target, event.getTickDelta());
        Vec3d anchor = basePos.add(0.0, target.getHeight() + 0.35, 0.0);

        // MVP-проекция: proj * position, точка относительно позиции камеры
        Matrix4f mvp = new Matrix4f(event.getProjectionMatrix()).mul(event.getPositionMatrix());
        Vec3d rel = anchor.subtract(event.getCamera().getPos());
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0f);
        clip.mul(mvp);
        if (clip.w() < 0.01f) {
            // За камерой — не рисуем
            screenX = Float.NaN;
            screenY = Float.NaN;
            return;
        }

        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();

        // NDC [-1..1] -> пиксели масштабированного экрана
        float sw = mc.getWindow().getScaledWidth();
        float sh = mc.getWindow().getScaledHeight();
        screenX = (ndcX * 0.5f + 0.5f) * sw;
        screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * sh;
    }

    /** HUD-рендер: рисуем саму метку по сохранённым экранным координатам. */
    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (targetEspModule == null || !targetEspModule.isEnable()) return;
        if (mc.player == null || mc.world == null) return;
        if (Float.isNaN(screenX) || Float.isNaN(screenY)) return;

        float x = screenX;
        float y = screenY;

        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0);
        float size = 4.0f + 1.2f * (float) pulse;

        int accent = ColorUtils.getThemeColor();
        int shadow = ColorUtils.applyAlpha(0xFF000000, 0.55f);
        int glow = ColorUtils.applyAlpha(accent, 0.28f + 0.22f * (float) pulse);

        MatrixStack ms = event.getContext().getMatrices();

        // Ромб (квадрат, повёрнутый на 45°) за счёт матрицы
        ms.push();
        ms.translate(x, y, 0.0f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0f));

        RenderUtils.drawRoundedRect(ms, -size * 3.2f, -size * 3.2f, size * 6.4f, size * 6.4f,
                2.5f, 2.5f, 2.5f, 2.5f, glow);
        RenderUtils.drawRoundedRect(ms, -size, -size, size * 2.0f, size * 2.0f,
                1.2f, 1.2f, 1.2f, 1.2f, shadow);
        RenderUtils.drawRoundedRect(ms, -size * 0.85f, -size * 0.85f, size * 1.7f, size * 1.7f,
                1.0f, 1.0f, 1.0f, 1.0f, accent);

        ms.pop();

        // Центральная точка
        float dot = Math.max(1.0f, size * 0.5f);
        RenderUtils.drawRoundCircle(ms, x - dot * 0.3f, y - dot * 0.3f, dot * 0.6f, 0xFFFFFFFF);
    }

    /** Интерполяция позиции сущности между тиками (плавная метка). */
    private Vec3d interpolate(LivingEntity entity, float partialTicks) {
        double x = entity.prevX + (entity.getX() - entity.prevX) * partialTicks;
        double y = entity.prevY + (entity.getY() - entity.prevY) * partialTicks;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * partialTicks;
        return new Vec3d(x, y, z);
    }
}