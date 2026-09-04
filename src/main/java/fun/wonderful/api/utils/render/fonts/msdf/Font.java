package fun.wonderful.api.utils.render.fonts.msdf;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import fun.wonderful.api.QClient;
import fun.wonderful.api.utils.render.ShaderUtils;

/**
 * MSDF-шрифт фиксированного размера. Живой API: draw(...) и getWidth(...)
 * (§-форматирование парсится внутри MsdfFont.applyGlyphs).
 */
public class Font implements QClient {
    private static final char FORMATTING_CODE_PREFIX = '§';

    private final MsdfFont font;
    private final float size;

    public Font(MsdfFont font, float size) {
        this.font = font;
        this.size = size;
    }

    public void draw(MatrixStack stack, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;

        float localSize = size * 0.5f;
        if (!hasDrawableGlyphs(text, localSize)) return;
        y -= 1.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.fontsMsdf);
        if (shader == null) return;

        setupShaderUniforms(shader, color);

        RenderSystem.setShaderTexture(0, font.getTextureId());
        font.setFiltered();

        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        font.applyGlyphs(matrix, buffer, localSize, text, 0,
                x, y + font.getBaselineHeight() * localSize, 0,
                255, 255, 255, 255);

        RenderSystem.setShader(ShaderUtils.fontsMsdf);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private boolean hasDrawableGlyphs(String text, float renderSize) {
        return text != null && !text.isEmpty() && font.getWidth(text, renderSize) > 0.0f;
    }

    private void setupShaderUniforms(ShaderProgram shader, int color) {
        GlUniform textureSizeUniform = shader.getUniform("TextureSize");
        GlUniform rangeUniform = shader.getUniform("Range");
        GlUniform thicknessUniform = shader.getUniform("Thickness");
        GlUniform edgeStrengthUniform = shader.getUniform("EdgeStrength");
        GlUniform colorUniform = shader.getUniform("Color");
        GlUniform outlineUniform = shader.getUniform("Outline");
        GlUniform outlineThicknessUniform = shader.getUniform("OutlineThickness");
        GlUniform outlineColorUniform = shader.getUniform("OutlineColor");

        if (textureSizeUniform != null) textureSizeUniform.set(font.getAtlasWidth(), font.getAtlasHeight());
        if (rangeUniform != null) rangeUniform.set(font.getRange());
        if (thicknessUniform != null) thicknessUniform.set(0f);
        if (edgeStrengthUniform != null) edgeStrengthUniform.set(0.5f);
        if (outlineUniform != null) outlineUniform.set(0);
        if (outlineThicknessUniform != null) outlineThicknessUniform.set(0f);
        if (outlineColorUniform != null) outlineColorUniform.set(0f, 0f, 0f, 1f);

        float[] rgba = extractRgba(color);
        if (colorUniform != null) colorUniform.set(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private float[] extractRgba(int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (a == 0) a = 255;
        return new float[]{r / 255f, g / 255f, b / 255f, a / 255f};
    }

    public float getStringWidth(String text) {
        if (text == null) return 0;
        return font.getWidth(stripFormattingCodes(text), size) / 2f;
    }

    public float getWidth(String text) {
        return getStringWidth(text);
    }

    private String stripFormattingCodes(String text) {
        if (text == null || text.indexOf(FORMATTING_CODE_PREFIX) < 0) {
            return text;
        }

        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == FORMATTING_CODE_PREFIX && i + 1 < text.length()) {
                i++;
                continue;
            }
            clean.append(current);
        }
        return clean.toString();
    }
}
