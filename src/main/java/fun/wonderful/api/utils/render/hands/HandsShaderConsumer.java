package fun.wonderful.api.utils.render.hands;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

/**
 * Прокси-вершинный потребитель Custom Hands: анимированная космическая
 * заливка руки/предмета «шейдером» — цвет каждой вершины считается
 * процедурно (по позиции вершины и времени), с сохранением объёма
 * (по яркости исходного цвета) — рука остаётся читаемой формой.
 */
public class HandsShaderConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final int mode;        // 0 Туманность, 1 Аврора, 2 Галактика, 3 Звёздная пыль
    private final float mix;       // 0..1 сила заливки
    private final boolean glow;    // полное свечение (без затемнения в пещерах)

    private float lastX, lastY, lastZ;
    private float time;

    public HandsShaderConsumer(VertexConsumer delegate, int mode, float mix, boolean glow, float time) {
        this.delegate = delegate;
        this.mode = mode;
        this.mix = mix;
        this.glow = glow;
        this.time = time;
    }

    // ===== Тематические «шейдеры» (по позиции вершины + времени) =====

    private static float hash(float x, float y, float z) {
        float h = (float) (Math.sin(x * 127.1 + y * 311.7 + z * 74.7) * 43758.5453);
        return h - (float) Math.floor(h);
    }

    /** Космическая палитра: индиго -> фиолет -> маджента -> бирюза. */
    private static float[] cosmic(float t) {
        if (t < 0.4f) {
            float k = t / 0.4f;
            return new float[]{0.22f + 0.33f * k, 0.10f + 0.12f * k, 0.48f + 0.27f * k};
        }
        if (t < 0.75f) {
            float k = (t - 0.4f) / 0.35f;
            return new float[]{0.55f - 0.43f * k, 0.22f + 0.23f * k, 0.75f - 0.05f * k};
        }
        float k = (t - 0.75f) / 0.25f;
        return new float[]{0.12f, 0.45f, 0.70f + 0.20f * k};
    }

    private float[] themed() {
        float x = lastX, y = lastY, z = lastZ;
        switch (mode) {
            case 1 -> {
                // Аврора: волны зелёного/голубого/фиолета вдоль руки
                float w = (float) Math.sin(y * 9.0 + Math.sin(x * 4.0 + time * 1.1) * 1.6 + time * 1.7);
                float k = (w + 1f) * 0.5f;
                return new float[]{0.10f + 0.05f * k, 0.55f + 0.35f * k, 0.45f + 0.45f * k};
            }
            case 2 -> {
                // Галактика: синий рукав + золотое ядро + искры
                float core = Math.max(0f, 1f - (x * x + y * y + z * z) * 2.2f);
                float sp = hash(x * 18f, y * 18f, z * 18f + (float) Math.floor(time * 3f));
                if (sp > 0.965f) {
                    return new float[]{0.95f, 0.85f, 0.55f};
                }
                return new float[]{0.10f + 0.55f * core, 0.16f + 0.55f * core, 0.50f + 0.30f * core};
            }
            case 3 -> {
                // Звёздная пыль: тёмный индиго + россыпь мерцающих точек
                float st = hash((float) Math.floor(x * 26f), (float) Math.floor(y * 26f),
                        (float) Math.floor(z * 26f) + (float) Math.floor(time * 2f));
                if (st > 0.93f) {
                    float tw = 0.6f + 0.4f * (float) Math.sin(time * 5f + st * 40f);
                    return new float[]{0.85f * tw + 0.1f, 0.88f * tw + 0.1f, 1.0f * tw + 0.1f};
                }
                return new float[]{0.08f, 0.06f, 0.22f};
            }
            default -> {
                // Туманность: переливающее поле
                float f = (float) (Math.sin(x * 6.0 + time * 0.9)
                        * Math.cos(y * 5.0 - time * 0.7)
                        * Math.sin(z * 4.0 + time * 0.5));
                return cosmic((f + 1f) * 0.5f);
            }
        }
    }

    /** Итоговый цвет: тема * форма (яркость исходника), микс с оригиналом. */
    private int apply(int r, int g, int b, int a) {
        if (mix <= 0.001f) {
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        float lr = r / 255f, lg = g / 255f, lb = b / 255f;
        float lum = 0.299f * lr + 0.587f * lg + 0.114f * lb;
        float form = 0.30f + 0.95f * lum;
        float[] t = themed();
        float nr = lr + (t[0] * form - lr) * mix;
        float ng = lg + (t[1] * form - lg) * mix;
        float nb = lb + (t[2] * form - lb) * mix;
        return (a << 24)
                | (Math.max(0, Math.min(255, (int) (nr * 255f))) << 16)
                | (Math.max(0, Math.min(255, (int) (ng * 255f))) << 8)
                | Math.max(0, Math.min(255, (int) (nb * 255f)));
    }

    // ===== Делегирование с перехватом цвета =====

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        lastX = x;
        lastY = y;
        lastZ = z;
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        int c = apply(red, green, blue, alpha);
        delegate.color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        int c = apply((int) (red * 255f), (int) (green * 255f), (int) (blue * 255f), (int) (alpha * 255f));
        delegate.color(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, ((c >>> 24) & 0xFF) / 255f);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        // Свечение: полный свет, рука не темнеет в пещерах
        delegate.light(glow ? 240 : u, glow ? 240 : v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer vertex(MatrixStack.Entry matrix, Vector3f pos) {
        lastX = pos.x;
        lastY = pos.y;
        lastZ = pos.z;
        delegate.vertex(matrix, pos);
        return this;
    }

    @Override
    public VertexConsumer vertex(MatrixStack.Entry matrix, float x, float y, float z) {
        lastX = x;
        lastY = y;
        lastZ = z;
        delegate.vertex(matrix, x, y, z);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        lastX = x;
        lastY = y;
        lastZ = z;
        int c = apply((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF);
        delegate.vertex(x, y, z, c, u, v, overlay, glow ? 0xF000F0 : light, nx, ny, nz);
    }
}
