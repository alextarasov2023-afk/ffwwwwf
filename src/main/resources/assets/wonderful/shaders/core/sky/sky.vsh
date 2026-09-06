#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 TexCoord;
out vec4 FragColor;

void main() {
    TexCoord = UV0;
    FragColor = Color;
    // Фуллскрин-квад на дальней плоскости: NDC z = 0.9999 даёт глубину ~1,
    // тест глубины пройдёт только там, где ничего не нарисовано (небо)
    gl_Position = vec4(Position.xy, 0.9999, 1.0);
}
