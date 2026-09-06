#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 TexCoord;
out vec4 FragColor;

void main() {
    TexCoord = UV0;
    FragColor = Color;
    // Клип-пространство: квад всегда покрывает весь экран,
    // независимо от текущих матриц рендера мира
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
