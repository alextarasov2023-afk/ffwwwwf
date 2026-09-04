package fun.wonderful.api.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.*;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import fun.wonderful.api.QClient;
import fun.wonderful.api.utils.render.blur.BlurProgram;

import java.util.UUID;

/**
 * Примитивы 2D-рендера на кастомных core-шейдерах: скруглённые панели,
 * обводки, тени, градиенты, kawase-блюр, текстуры и головы игроков.
 */
@UtilityClass
@SuppressWarnings("all")
public class RenderUtils implements QClient {

    private static final UUID DEFAULT_SKIN_UUID = new UUID(0L, 0L);

    // ============================================================
    // Текстуры
    // ============================================================

    public void drawTexture(MatrixStack matrices, Identifier texture, float x, float y, float width, float height, float u1, float v1, float u2, float v2, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int alpha = (color >> 24) & 0xFF;
        if (alpha == 0) alpha = 255;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = alpha / 255f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x, y + height, 0).texture(u1, v2).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y + height, 0).texture(u2, v2).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y, 0).texture(u2, v1).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
    }

    public void drawImage(MatrixStack matrices, Identifier texture, float x, float y, float width, float height, int color) {
        drawTexture(matrices, texture, x, y, width, height, 0.0f, 0.0f, 1.0f, 1.0f, color);
    }

    // ============================================================
    // Головы игроков (веки-шейдер face с UV лица скина)
    // ============================================================

    public void drawPlayerHead(MatrixStack matrices, PlayerListEntry entry, float x, float y, float size, float radius) {
        drawPlayerHead(matrices, entry, x, y, size, radius, 1.0f, 0.0f);
    }

    public void drawPlayerHead(MatrixStack matrices, PlayerListEntry entry, float x, float y, float size, float radius, float alpha, float hurtPercent) {
        if (entry == null) return;
        Identifier skinTexture = entry.getSkinTextures().texture();
        if (skinTexture == null) {
            skinTexture = DefaultSkinHelper.getSkinTextures(entry.getProfile().getId()).texture();
        }
        drawHeadInternal(matrices, skinTexture, x, y, size, radius, alpha, hurtPercent);
    }

    private void drawHeadInternal(MatrixStack matrices, Identifier skinTexture, float x, float y, float size, float radius, float alpha, float hurtPercent) {
        if (skinTexture == null) {
            skinTexture = DefaultSkinHelper.getSkinTextures(DEFAULT_SKIN_UUID).texture();
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, skinTexture);

        drawFaceLayer(matrices, skinTexture, x, y, size, radius, alpha, hurtPercent, 8.0f);
        drawFaceLayer(matrices, skinTexture, x, y, size, radius, alpha, hurtPercent, 40.0f);

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
    }

    /** Слой лица скина: uOffset 8 = лицо, 40 = оверлей (шляпа/волосы). */
    private void drawFaceLayer(MatrixStack matrices, Identifier skinTexture, float x, float y, float size, float radius, float alpha, float hurtPercent, float uOffset) {
        RenderSystem.setShaderTexture(0, skinTexture);

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.face);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        GlUniform locationUniform = shader.getUniform("location");
        GlUniform sizeUniform = shader.getUniform("size");
        GlUniform radiusUniform = shader.getUniform("radius");
        GlUniform alphaUniform = shader.getUniform("alpha");
        GlUniform uUniform = shader.getUniform("u");
        GlUniform vUniform = shader.getUniform("v");
        GlUniform wUniform = shader.getUniform("w");
        GlUniform hUniform = shader.getUniform("h");
        GlUniform hurtPercentUniform = shader.getUniform("hurtPercent");

        if (locationUniform != null) locationUniform.set(x, y);
        if (sizeUniform != null) sizeUniform.set(size, size);
        if (radiusUniform != null) radiusUniform.set(radius);
        if (alphaUniform != null) alphaUniform.set(alpha);
        if (uUniform != null) uUniform.set(uOffset / 64.0f);
        if (vUniform != null) vUniform.set(8.0f / 64.0f);
        if (wUniform != null) wUniform.set(8.0f / 64.0f);
        if (hUniform != null) hUniform.set(8.0f / 64.0f);
        if (hurtPercentUniform != null) hurtPercentUniform.set(hurtPercent);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        buffer.vertex(matrix, x, y, 0).texture(0, 0);
        buffer.vertex(matrix, x, y + size, 0).texture(0, 1);
        buffer.vertex(matrix, x + size, y + size, 0).texture(1, 1);
        buffer.vertex(matrix, x + size, y, 0).texture(1, 0);

        RenderSystem.setShader(ShaderUtils.face);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    // ============================================================
    // Скруглённые панели
    // ============================================================

    public void drawRoundedRect(MatrixStack matrices, float x, float y, float width, float height, float radius, int color) {
        drawRoundedRect(matrices, x, y, width, height, radius, radius, radius, radius, color);
    }

    public void drawRoundedRect(MatrixStack matrices, float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.roundedRect);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        GlUniform sizeUniform = shader.getUniform("Size");
        GlUniform radiusUniform = shader.getUniform("Radius");

        if (sizeUniform != null) sizeUniform.set(width, height);
        if (radiusUniform != null) radiusUniform.set(topLeft, topRight, bottomRight, bottomLeft);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int alpha = (color >> 24) & 0xFF;
        if (alpha == 0) alpha = 255;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = alpha / 255f;

        buffer.vertex(matrix, x, y, 0).color(r, g, b, a);
        buffer.vertex(matrix, x, y + height, 0).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y + height, 0).color(r, g, b, a);
        buffer.vertex(matrix, x + width, y, 0).color(r, g, b, a);

        RenderSystem.setShader(ShaderUtils.roundedRect);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    public void drawRoundCircle(MatrixStack matrices, float x, float y, float radius, int color) {
        drawRoundedRect(matrices, x - (radius / 2), y - (radius / 2), radius, radius, (radius / 2) - 0.5f, color);
    }

    // ============================================================
    // Тени
    // ============================================================

    public void drawShadow(MatrixStack matrices, float x, float y, float width, float height,
                           float radius, float softness,
                           int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.shadowRect);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float extendedWidth = width + softness * 2.0f;
        float extendedHeight = height + softness * 2.0f;
        float drawX = x - softness;
        float drawY = y - softness;

        GlUniform sizeUniform = shader.getUniform("Size");
        GlUniform softnessUniform = shader.getUniform("Softness");
        GlUniform radiusUniform = shader.getUniform("Radius");
        GlUniform topLeftColorUniform = shader.getUniform("TopLeftColor");
        GlUniform topRightColorUniform = shader.getUniform("TopRightColor");
        GlUniform bottomLeftColorUniform = shader.getUniform("BottomLeftColor");
        GlUniform bottomRightColorUniform = shader.getUniform("BottomRightColor");

        if (sizeUniform != null) sizeUniform.set(extendedWidth, extendedHeight);
        if (softnessUniform != null) softnessUniform.set(softness);
        if (radiusUniform != null) radiusUniform.set(radius);

        if (topLeftColorUniform != null) topLeftColorUniform.set(toColorRGBA(topLeftColor));
        if (topRightColorUniform != null) topRightColorUniform.set(toColorRGBA(topRightColor));
        if (bottomLeftColorUniform != null) bottomLeftColorUniform.set(toColorRGBA(bottomLeftColor));
        if (bottomRightColorUniform != null) bottomRightColorUniform.set(toColorRGBA(bottomRightColor));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        buffer.vertex(matrix, drawX, drawY, 0).texture(0, 0);
        buffer.vertex(matrix, drawX, drawY + extendedHeight, 0).texture(0, 1);
        buffer.vertex(matrix, drawX + extendedWidth, drawY + extendedHeight, 0).texture(1, 1);
        buffer.vertex(matrix, drawX + extendedWidth, drawY, 0).texture(1, 0);

        RenderSystem.setShader(ShaderUtils.shadowRect);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    public void drawShadow(MatrixStack matrices, float x, float y, float width, float height,
                           float radius, float softness, int color) {
        drawShadow(matrices, x, y, width, height, radius, softness, color, color, color, color);
    }

    /** argb int -> float[4] rgba (alpha 0 трактуется как 255 — соглашение GUI). */
    private float[] toColorRGBA(int color) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                a / 255f
        };
    }

    // ============================================================
    // Градиенты
    // ============================================================

    public void drawGradientRect(MatrixStack matrices, float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.gradientRect);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        GlUniform sizeUniform = shader.getUniform("Size");
        GlUniform radiusUniform = shader.getUniform("Radius");
        GlUniform smoothnessUniform = shader.getUniform("Smoothness");
        GlUniform colorModulatorUniform = shader.getUniform("ColorModulator");
        GlUniform topLeftColorUniform = shader.getUniform("TopLeftColor");
        GlUniform bottomLeftColorUniform = shader.getUniform("BottomLeftColor");
        GlUniform topRightColorUniform = shader.getUniform("TopRightColor");
        GlUniform bottomRightColorUniform = shader.getUniform("BottomRightColor");

        if (sizeUniform != null) sizeUniform.set(width, height);
        if (radiusUniform != null) radiusUniform.set(topLeft, topRight, bottomRight, bottomLeft);
        if (smoothnessUniform != null) smoothnessUniform.set(1.0f);
        if (colorModulatorUniform != null) colorModulatorUniform.set(1.0f, 1.0f, 1.0f, 1.0f);

        if (topLeftColorUniform != null) topLeftColorUniform.set(toColorRGBA(topLeftColor));
        if (bottomLeftColorUniform != null) bottomLeftColorUniform.set(toColorRGBA(bottomLeftColor));
        if (topRightColorUniform != null) topRightColorUniform.set(toColorRGBA(topRightColor));
        if (bottomRightColorUniform != null) bottomRightColorUniform.set(toColorRGBA(bottomRightColor));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(matrix, x, y, 0).texture(0, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x, y + height, 0).texture(0, 1).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x + width, y + height, 0).texture(1, 1).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x + width, y, 0).texture(1, 0).color(1f, 1f, 1f, 1f);

        RenderSystem.setShader(ShaderUtils.gradientRect);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    public void drawGradientRect(MatrixStack matrices, float x, float y, float width, float height, float radius, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        drawGradientRect(matrices, x, y, width, height, radius, radius, radius, radius, topLeftColor, topRightColor, bottomLeftColor, bottomRightColor);
    }

    public void drawGradientRect(MatrixStack matrices, float x, float y, float width, float height, float radius, int topColor, int bottomColor) {
        drawGradientRect(matrices, x, y, width, height, radius, radius, radius, radius, topColor, topColor, bottomColor, bottomColor);
    }

    public void drawGradientRect(MatrixStack matrices, float x, float y, float width, float height, float radius, int leftColor, int rightColor, boolean horizontal) {
        if (horizontal) {
            drawGradientRect(matrices, x, y, width, height, radius, radius, radius, radius, leftColor, rightColor, leftColor, rightColor);
        } else {
            drawGradientRect(matrices, x, y, width, height, radius, radius, radius, radius, leftColor, leftColor, rightColor, rightColor);
        }
    }

    // ============================================================
    // Обводки
    // ============================================================

    public void drawRoundedRectOutline(MatrixStack matrices, float x, float y, float width, float height,
                                       float radius, float outline, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        drawRoundedRectOutline(matrices, x, y, width, height, radius, radius, radius, radius, outline,
                topLeftColor, topRightColor, bottomLeftColor, bottomRightColor);
    }

    public void drawRoundedRectOutline(MatrixStack matrices, float x, float y, float width, float height,
                                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                                       float outline, int topLeftColor, int topRightColor, int bottomLeftColor, int bottomRightColor) {
        if (outline <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.roundedRectOutline);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        GlUniform sizeUniform = shader.getUniform("Size");
        GlUniform radiusUniform = shader.getUniform("Radius");
        GlUniform smoothnessUniform = shader.getUniform("Smoothness");
        GlUniform colorModulatorUniform = shader.getUniform("ColorModulator");
        GlUniform outlineUniform = shader.getUniform("Outline");
        GlUniform topLeftColorUniform = shader.getUniform("TopLeftColor");
        GlUniform bottomLeftColorUniform = shader.getUniform("BottomLeftColor");
        GlUniform topRightColorUniform = shader.getUniform("TopRightColor");
        GlUniform bottomRightColorUniform = shader.getUniform("BottomRightColor");

        if (sizeUniform != null) sizeUniform.set(width, height);
        if (radiusUniform != null) radiusUniform.set(topLeft, topRight, bottomRight, bottomLeft);
        if (smoothnessUniform != null) smoothnessUniform.set(1.0f);
        if (colorModulatorUniform != null) colorModulatorUniform.set(1.0f, 1.0f, 1.0f, 1.0f);
        if (outlineUniform != null) outlineUniform.set(outline);

        if (topLeftColorUniform != null) topLeftColorUniform.set(toColorRGBA(topLeftColor));
        if (bottomLeftColorUniform != null) bottomLeftColorUniform.set(toColorRGBA(bottomLeftColor));
        if (topRightColorUniform != null) topRightColorUniform.set(toColorRGBA(topRightColor));
        if (bottomRightColorUniform != null) bottomRightColorUniform.set(toColorRGBA(bottomRightColor));

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x, y, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x, y + height, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x + width, y + height, 0).color(1f, 1f, 1f, 1f);
        buffer.vertex(matrix, x + width, y, 0).color(1f, 1f, 1f, 1f);

        RenderSystem.setShader(ShaderUtils.roundedRectOutline);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    // ============================================================
    // Блюр (kawase) поверх содержимого экрана
    // ============================================================

    private void drawBlurQuad(MatrixStack matrices, float x, float y, float width, float height,
                              float topLeft, float topRight, float bottomRight, float bottomLeft, int color) {
        if (BlurProgram.getBuffer2() == null) return;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.roundedTexture);

        GlUniform sizeUniform = shader.getUniform("Size");
        GlUniform radiusUniform = shader.getUniform("Radius");
        GlUniform smoothnessUniform = shader.getUniform("Smoothness");
        GlUniform colorModulatorUniform = shader.getUniform("ColorModulator");

        if (sizeUniform != null) sizeUniform.set(width, height);
        if (radiusUniform != null) radiusUniform.set(topLeft, topRight, bottomRight, bottomLeft);
        if (smoothnessUniform != null) smoothnessUniform.set(0.5f);
        if (colorModulatorUniform != null) colorModulatorUniform.set(1.0f, 1.0f, 1.0f, 1.0f);

        RenderSystem.setShaderTexture(0, BlurProgram.getTexture());
        RenderSystem.setShader(ShaderUtils.roundedTexture);

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float u1 = x / screenWidth;
        float v1 = (screenHeight - y) / screenHeight;
        float u2 = (x + width) / screenWidth;
        float v2 = (screenHeight - y - height) / screenHeight;

        float[] rgba = toColorRGBA(color);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, x, y, 0).texture(u1, v1).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        builder.vertex(matrix, x, y + height, 0).texture(u1, v2).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        builder.vertex(matrix, x + width, y + height, 0).texture(u2, v2).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        builder.vertex(matrix, x + width, y, 0).texture(u2, v1).color(rgba[0], rgba[1], rgba[2], rgba[3]);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height,
                         float topLeft, float topRight, float bottomRight, float bottomLeft, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawBlurQuad(matrices, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height, float radius, int color) {
        drawBlur(matrices, x, y, width, height, radius, radius, radius, radius, color);
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height,
                         float topLeft, float topRight, float bottomRight, float bottomLeft, float blurStrength, int color) {
        BlurProgram.getInstance().request();
        BlurProgram.getInstance().setBlurOffset(blurStrength);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawBlurQuad(matrices, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height, float radius, float blurStrength, int color) {
        drawBlur(matrices, x, y, width, height, radius, radius, radius, radius, blurStrength, color);
    }
}
