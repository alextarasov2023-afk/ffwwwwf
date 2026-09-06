package fun.wonderful.api.utils.render.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import fun.wonderful.api.QClient;
import fun.wonderful.api.utils.render.ShaderUtils;
import fun.wonderful.client.ui.clickgui.ThemePanel;

/**
 * Космическое небо: фуллскрин-квад на ДАЛЬНЕЙ плоскости глубины,
 * рисуется ПОСЛЕ рендера мира (Event3DRender). Тест глубины LEQUAL
 * пропускает квад только там, где глубина == 1 — то есть там, где небо;
 * рельеф, вода и сущности остаются поверх шейдера.
 */
public final class CosmicSkyRenderer implements QClient {

    private static boolean announced = false;

    private CosmicSkyRenderer() {
    }

    public static void render(int mode, float speed, float intensity) {
        ShaderProgram program = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.cosmicSky);
        if (!announced) {
            announced = true;
            System.out.println("[ShaderSky] cosmic shader active (mode=" + mode + ")");
        }

        // Квад проходит тест глубины только на «чистом» небе (depth ~1)
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.setShader(ShaderUtils.cosmicSky);

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

        Matrix4f matrix = new org.joml.Matrix4f().identity();
        BufferBuilder builder = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix, -1f, -1f, 0f).texture(0f, 0f).color(1f, 1f, 1f, 1f);
        builder.vertex(matrix, -1f, 1f, 0f).texture(0f, 1f).color(1f, 1f, 1f, 1f);
        builder.vertex(matrix, 1f, 1f, 0f).texture(1f, 1f).color(1f, 1f, 1f, 1f);
        builder.vertex(matrix, 1f, -1f, 0f).texture(1f, 0f).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
