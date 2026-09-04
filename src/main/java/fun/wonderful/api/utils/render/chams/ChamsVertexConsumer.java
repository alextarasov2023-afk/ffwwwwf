package fun.wonderful.api.utils.render.chams;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Прокси-вершинный потребитель для Chams.
 * <p>
 * Всё делегируется в оригинал, но метод {@code color(...)} перехватывается
 * и подменяется на целевой цвет (r, g, b, a) модуля. Так как рендер модели
 * сущности задаёт цвет вершин прямо перед записью вершины, подмена цвета
 * здесь перекрашивает всё тело в выбранный оттенок, сохраняя анимации
 * и текстуру модели.
 */
public class ChamsVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    public ChamsVertexConsumer(VertexConsumer delegate, float red, float green, float blue, float alpha) {
        this.delegate = delegate;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(
                (int) (this.red * 255f),
                (int) (this.green * 255f),
                (int) (this.blue * 255f),
                (int) (this.alpha * 255f));
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        delegate.color(this.red, this.green, this.blue, this.alpha);
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
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer vertex(MatrixStack.Entry matrix, org.joml.Vector3f pos) {
        delegate.vertex(matrix, pos);
        return this;
    }

    @Override
    public VertexConsumer vertex(MatrixStack.Entry matrix, float x, float y, float z) {
        delegate.vertex(matrix, x, y, z);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        int c = ((int) (this.alpha * 255f) << 24)
                | ((int) (this.red * 255f) << 16)
                | ((int) (this.green * 255f) << 8)
                | (int) (this.blue * 255f);
        delegate.vertex(x, y, z, c, u, v, overlay, light, nx, ny, nz);
    }
}
