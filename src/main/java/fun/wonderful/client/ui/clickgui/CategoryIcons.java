package fun.wonderful.client.ui.clickgui;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.wonderful.client.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Иконки категорий ClickGUI — гладкие PNG-иконки без пикселей.
 * <p>
 * Загрузка полностью ванильная и потому гарантированно рабочая: {@link NativeImageBackedTexture}
 * (тот же путь, что у всех текстур игры и TTF-шрифтов клиента). Поверх стандартной загрузки включаем сглаживание:
 * полный набор мипмапов + трилинейная фильтрация, поэтому иконка 256×256,
 * отрисованная в 15px, остаётся идеально гладкой — ни одного пикселя.
 * Отрисовка — штатный низкоуровневый путь клиента (POSITION_TEX_COLOR + setShaderTexture),
 * идентичный тому, которым рисуются шрифты.
 */
public final class CategoryIcons {

    private static final String RESOURCE_BASE = "/assets/wonderful/textures/gui/category/";
    private static final Map<Module.ModuleCategory, NativeImageBackedTexture> textures = new EnumMap<>(Module.ModuleCategory.class);
    private static boolean initialized = false;

    private CategoryIcons() {
    }

    private static void init() {
        initialized = true;
        load(Module.ModuleCategory.COMBAT, "combat");
        load(Module.ModuleCategory.MOVEMENT, "movement");
        load(Module.ModuleCategory.PLAYER, "player");
        load(Module.ModuleCategory.MISC, "misc");
        load(Module.ModuleCategory.RENDER, "render");
    }

    private static void load(Module.ModuleCategory cat, String name) {
        try (InputStream in = CategoryIcons.class.getResourceAsStream(RESOURCE_BASE + name + ".png")) {
            if (in == null) {
                System.err.println("[CategoryIcons] Resource not found: " + RESOURCE_BASE + name + ".png");
                return;
            }
            NativeImage image = NativeImage.read(in);

            // Ванильная загрузка текстуры: выделение памяти + заливка пикселей целиком
            // через стандартный NativeImageBackedTexture (как у всех динамических текстур игры).
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            texture.upload(); // гарантирует prepareImage + upload независимо от версии

            // Сглаживание: генерируем полный мип-чейн и включаем трилинейную фильтрацию
            texture.bindTexture();
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            texture.setFilter(true, true);

            MinecraftClient.getInstance().getTextureManager()
                    .registerTexture(Identifier.of("wonderful", "textures/gui/category/" + name + ".png"), texture);

            int glErr = GL11.glGetError();
            if (glErr != GL11.GL_NO_ERROR) {
                System.err.println("[CategoryIcons] GL error 0x" + Integer.toHexString(glErr) + " after " + name);
            } else {
                System.out.println("[CategoryIcons] Loaded " + name
                        + " (" + image.getWidth() + "x" + image.getHeight() + "), glId=" + texture.getGlId());
            }
            textures.put(cat, texture);
        } catch (Exception e) {
            System.err.println("[CategoryIcons] Failed to load " + name + ": " + e);
        }
    }

    /** Гладкая отрисовка иконки: ванильный бинд текстуры + POSITION_TEX_COLOR, как у шрифтов клиента */
    public static void draw(MatrixStack ms, Module.ModuleCategory cat, float x, float y, float size, int color) {
        // alpha==0 внутри шейдера трактуется как 255 — не рисуем, пока окно не начало проявляться
        if ((color >>> 24) == 0) return;
        if (!initialized) init();
        NativeImageBackedTexture texture = textures.get(cat);
        if (texture == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderTexture(0, texture.getGlId());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        int a = (color >> 24) & 0xFF;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float af = a / 255f;

        Matrix4f matrix = ms.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0).texture(0f, 0f).color(r, g, b, af);
        buffer.vertex(matrix, x, y + size, 0).texture(0f, 1f).color(r, g, b, af);
        buffer.vertex(matrix, x + size, y + size, 0).texture(1f, 1f).color(r, g, b, af);
        buffer.vertex(matrix, x + size, y, 0).texture(1f, 0f).color(r, g, b, af);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
    }
}