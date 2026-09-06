package fun.wonderful.api.utils.render;

import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import fun.wonderful.api.QClient;

/**
 * Ключи кастомных core-шейдеров клиента. Оставлены только используемые:
 * примитивы GUI, kawase-блюр, градиенты, MSDF-шрифты и головы игроков.
 */
@UtilityClass
public class ShaderUtils implements QClient {

    public final ShaderProgramKey roundedRect = register("rect", "rounded_rect", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey roundedRectOutline = register("rect", "rounded_rect_outline", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey roundedTexture = register("texture", "texture_rect", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey kawaseDown = register("kawase_down", "kawase_down", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey kawaseUp = register("kawase_up", "kawase_up", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey gradientRect = register("gradient_rect", "gradient", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey shadowRect = register("shadow_rect", "shadow", VertexFormats.POSITION_COLOR);
    public final ShaderProgramKey fontsMsdf = register("fonts", "fonts", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey face = register("face", "face", VertexFormats.POSITION_TEXTURE_COLOR);
    public final ShaderProgramKey cosmicSky = register("sky", "sky", VertexFormats.POSITION_TEXTURE_COLOR);

    private ShaderProgramKey register(String shaderNamePackage, String shaderName, VertexFormat vertexFormat) {
        return new ShaderProgramKey(Identifier.of("wonderful", "core/" + shaderNamePackage + "/" + shaderName), vertexFormat, Defines.EMPTY);
    }
}
