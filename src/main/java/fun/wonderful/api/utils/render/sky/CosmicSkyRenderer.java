package fun.wonderful.api.utils.render.sky;

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
import fun.wonderful.client.ui.clickgui.ThemePanel;

/**
 * Отрисовка космического неба: фуллскрин-квад в клип-пространстве
 * с кастомным core-шейдером (туманность + звёзды + акцент темы).
 * Вызывается из SkyRenderingMixin вместо ванильного купола —
 * рельеф рисуется позже и перекрывает квад, поэтому шейдер виден
 * только там, где небо.
 */
public final class CosmicSkyRenderer implements QClient {

    private CosmicSkyRenderer() {
    }

    public static void render(int mode, float speed, float intensity) {
        ShaderProgram program = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.cosmicSky);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderUtils.cosmicSky);

        // Время держим небольшим (30-минутный цикл) — не теряем точность float в шуме
        GlUniform u;
        if ((u = program.getUniform("Time")) != null) {
            u.set((System.currentTimeMillis() / 1000f) % 1800f);
        }
        if ((u = program.getUniform("Mode")) != null) u.set((float) mode);
        if ((u = program.getUniform("Speed")) != null) u.set(speed);
        if ((u = program.getUniform("Intensity")) != null) u.set(intensity);

        int accent = ThemePanel.accentSolid();
        if ((u = program.getUniform("AccentColor")) != null) {
            u.set(((accent >> 16) & 0xFF) / 255f,
                    ((accent >> 8) & 0xFF) / 255f,
                    (accent & 0xFF) / 255f);
        }

        Matrix4f matrix = new MatrixStack().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, -1f, -1f, 0f).texture(0f, 0f).color(1f, 1f, 1f, 1f);
        builder.vertex(matrix, -1f, 1f, 0f).texture(0f, 1f).color(1f, 1f, 1f, 1f);
        builder.vertex(matrix, 1f, 1f, 0f).texture(1f, 1f).color(1f, 1f, 1f, 1f);
        builder.vertex(matrix, 1f, -1f, 0f).texture(1f, 0f).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
