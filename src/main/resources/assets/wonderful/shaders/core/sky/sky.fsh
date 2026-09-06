#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform float Time;
uniform float Speed;
uniform float Intensity;
uniform vec3 AccentColor;

out vec4 OutColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.55;
    for (int i = 0; i < 5; i++) {
        v += amp * vnoise(p);
        p = mat2(0.8, -0.6, 0.6, 0.8) * p * 2.03;
        amp *= 0.52;
    }
    return v;
}

// Космическая палитра: глубокий индиго -> фиолетовый -> маджента -> бирюза
vec3 cosmic(float t) {
    vec3 c1 = vec3(0.03, 0.02, 0.10);
    vec3 c2 = vec3(0.22, 0.10, 0.48);
    vec3 c3 = vec3(0.55, 0.22, 0.75);
    vec3 c4 = vec3(0.12, 0.45, 0.70);
    vec3 col = mix(c1, c2, smoothstep(0.0, 0.4, t));
    col = mix(col, c3, smoothstep(0.35, 0.75, t));
    col = mix(col, c4, smoothstep(0.70, 1.0, t));
    return col;
}

// Слой звёзд: редкие мерцающие точки на хэш-сетке
float starLayer(vec2 uv, float scale, float threshold, float twinkleSpeed) {
    vec2 g = uv * scale;
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash(id);
    if (h < threshold) return 0.0;
    vec2 off = vec2(hash(id + 4.7), hash(id + 9.3)) - 0.5;
    float d = length(f - off * 0.75);
    float tw = 0.55 + 0.45 * sin(Time * twinkleSpeed + h * 40.0);
    return smoothstep(0.09, 0.0, d) * tw;
}

void main() {
    vec2 uv = TexCoord;
    float t = Time * Speed;

    // Дышащее поле туманности: домен-варп двумя слоями шума
    vec2 flow = vec2(fbm(uv * 2.2 + vec2(t * 0.045, -t * 0.030)),
                     fbm(uv * 2.2 + vec2(5.2 - t * 0.030, 1.3 + t * 0.050)));
    float neb = fbm(uv * 3.0 + flow * 1.35 + vec2(t * 0.020, 0.0));

    vec3 col = cosmic(neb);

    // Переливы акцентным цветом темы по волнам туманности
    float wave = fbm(uv * 1.7 - vec2(t * 0.060, t * 0.020));
    col += AccentColor * pow(wave, 2.2) * 0.30;

    // Два слоя мерцающих звёзд с лёгким дрейфом
    col += vec3(0.90, 0.93, 1.00) * starLayer(uv + vec2(t * 0.004, 0.0), 26.0, 0.965, 2.2) * 0.85;
    col += vec3(0.75, 0.85, 1.00) * starLayer(uv * 1.6 + 7.3 - vec2(t * 0.006, 0.0), 40.0, 0.978, 3.1) * 0.6;

    // У горизонта темнее, к зениту насыщеннее
    col *= mix(0.55, 1.15, smoothstep(0.0, 0.9, uv.y));

    // Яркость из настройки, мягкий кламп чтобы не слепило
    col = col * (0.75 + 0.55 * Intensity);
    col = clamp(col, 0.0, 1.0);

    OutColor = vec4(col, 1.0);
}
