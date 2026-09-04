package fun.wonderful.client.ui.clickgui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Одиночные PNG-иконки ClickGUI общего назначения (иконка палитры тем и т.п.).
 * Использует ту же ванильную загрузку, что и {@link CategoryIcons}: NativeImageBackedTexture
 * с полным мип-чейном и трилинейной фильтрацией, поэтому иконка остается гладкой на любом размере.
 */
public final class GuiIcons {

    private static final String RESOURCE_BASE = "/assets/wonderful/textures/gui/";
    private static final Map<String, NativeImageBackedTexture> textures = new HashMap<>();
    private static final Map<String, Identifier> ids = new HashMap();
    private static boolean initialized = false;

    private GuiIcons() {
    }

    private static void init() {
        initialized = true;
        load("theme_palette");
        load("heart");
    }

    private static void load(String name) {
        try (InputStream in = GuiIcons.class.getResourceAsStream(RESOURCE_BASE + name + ".png")) {
            if (in == null) {
                System.err.println("[GuiIcons] Resource not found: " + RESOURCE_BASE + name + ".png");
                return;
            }
            NativeImage image = NativeImage.read(in);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            texture.upload();
            texture.bindTexture();
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            texture.setFilter(true, true);

            Identifier id = Identifier.of("wonderful", "textures/gui/" + name + ".png");
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            textures.put(name, texture);
            ids.put(name, id);
            System.out.println("[GuiIcons] Loaded " + name
                    + " (" + image.getWidth() + "x" + image.getHeight() + "), glId=" + texture.getGlId());
        } catch (Exception e) {
            System.err.println("[GuiIcons] Failed to load " + name + ": " + e);
        }
    }

    /** Гладкая отрисовка одиночной иконки (тtингуется в color). */
    public static void draw(MatrixStack ms, String name, float x, float y, float size, int color) {
        if (!initialized) init();
        NativeImageBackedTexture texture = textures.get(name);
        if (texture == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture.getGlId());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        Matrix4f matrix = ms.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0).texture(0f, 0f).color(r, g, b, a);
        buffer.vertex(matrix, x, y + size, 0).texture(0f, 1f).color(r, g, b, a);
        buffer.vertex(matrix, x + size, y + size, 0).texture(1f, 1f).color(r, g, b, a);
        buffer.vertex(matrix, x + size, y, 0).texture(1f, 0f).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
    }
}
