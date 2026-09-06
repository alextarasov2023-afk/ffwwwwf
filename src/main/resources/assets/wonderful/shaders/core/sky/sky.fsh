#version 150

in vec2 TexCoord;
in vec4 FragColor;

uniform float Time;
uniform float Mode;
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

// ===== Вид 0: Туманность =====

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

vec3 nebula(vec2 uv, float t) {
    vec2 flow = vec2(fbm(uv * 2.2 + vec2(t * 0.045, -t * 0.030)),
                     fbm(uv * 2.2 + vec2(5.2 - t * 0.030, 1.3 + t * 0.050)));
    float neb = fbm(uv * 3.0 + flow * 1.35 + vec2(t * 0.020, 0.0));
    vec3 col = cosmic(neb);
    col += vec3(0.90, 0.93, 1.00) * starLayer(uv + vec2(t * 0.004, 0.0), 26.0, 0.965, 2.2) * 0.85;
    col += vec3(0.75, 0.85, 1.00) * starLayer(uv * 1.6 + 7.3 - vec2(t * 0.006, 0.0), 40.0, 0.978, 3.1) * 0.6;
    return col;
}

// ===== Вид 1: Аврора (северное сияние) =====

vec3 aurora(vec2 uv, float t) {
    vec3 col = mix(vec3(0.012, 0.025, 0.06), vec3(0.03, 0.05, 0.11), uv.y);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float wave = fbm(vec2(uv.x * 2.2 + t * 0.06 + fi * 13.7, fi * 7.7));
        float y = uv.y - (0.28 + 0.16 * fi) - 0.10 * wave;
        float band = exp(-y * y * 34.0);
        vec3 c = i == 0 ? vec3(0.10, 0.90, 0.55)
                : i == 1 ? vec3(0.15, 0.55, 0.95)
                : vec3(0.70, 0.30, 0.90);
        // Занавесь: вертикальные лучи разной яркости
        float rays = 0.55 + 0.45 * sin(uv.x * 55.0 + t * 1.1 + fi * 3.0
                + 6.0 * fbm(vec2(uv.x * 3.0, t * 0.12 + fi)));
        col += c * band * rays * (0.30 + 0.14 * sin(t * 0.7 + fi * 2.1))
                * smoothstep(0.05, 0.45, uv.y);
    }
    col += vec3(0.85, 0.92, 1.0) * starLayer(uv, 30.0, 0.955, 1.8) * 0.55;
    return col;
}

// ===== Вид 2: Звездопад =====

vec3 starfall(vec2 uv, float t) {
    vec3 col = mix(vec3(0.03, 0.02, 0.09), vec3(0.09, 0.05, 0.20), uv.y);
    col += vec3(0.90, 0.93, 1.00) * starLayer(uv, 22.0, 0.945, 2.6) * 0.9;
    col += vec3(0.80, 0.86, 1.00) * starLayer(uv * 1.7 + 3.1, 34.0, 0.960, 3.4) * 0.7;

    // Метеор: один за цикл, случайные origin/направление по seed цикла
    float cyc = t * 0.11;
    float ph = fract(cyc);
    float seed = floor(cyc);
    vec2 orig = vec2(0.15 + 0.7 * hash(vec2(seed, 1.0)), 0.65 + 0.25 * hash(vec2(seed, 7.0)));
    vec2 dir = normalize(vec2(0.82, -0.42) + 0.4 * (vec2(hash(vec2(seed, 3.0)), hash(vec2(seed, 5.0))) - 0.5));
    float prog = ph * 1.7 - 0.35;
    vec2 head = orig + dir * prog;
    vec2 tail = head - dir * 0.16;
    vec2 rel = uv - head;
    float along = clamp(dot(rel, -dir), 0.0, 0.16);
    vec2 closest = head - dir * along;
    float d = length(uv - closest);
    float env = sin(clamp(ph, 0.0, 1.0) * 3.14159);
    float glow = smoothstep(0.028, 0.0, d) * (1.0 - along / 0.16 * 0.7);
    col += vec3(0.95, 0.97, 1.0) * glow * env * 1.4;
    col += vec3(0.6, 0.75, 1.0) * smoothstep(0.06, 0.0, d) * env * 0.4;
    return col;
}

// ===== Вид 3: Галактика =====

vec3 galaxy(vec2 uv, float t) {
    vec2 c = uv - 0.5;
    float ca = cos(0.55), sa = sin(0.55);
    vec2 r = mat2(ca, -sa, sa, ca) * c;

    float wobble = 0.10 * fbm(vec2(r.x * 2.6 + t * 0.03, r.y * 4.0));
    float dist = abs(r.y + wobble);
    float band = exp(-dist * dist * 110.0);

    float bulge = exp(-dot(c, c) * 11.0);

    vec3 col = mix(vec3(0.02, 0.02, 0.07), vec3(0.06, 0.05, 0.16), uv.y);
    // Руки — холодный синий, ядро — тёплое золото
    vec3 arm = mix(vec3(0.20, 0.32, 0.85), vec3(0.55, 0.45, 0.90), fbm(uv * 4.0 + t * 0.02));
    col += arm * band * 0.85;
    col += vec3(0.95, 0.82, 0.55) * bulge * 0.55;
    // Пыль: тёмные прожилки вдоль рукава
    col *= 1.0 - 0.35 * band * smoothstep(0.55, 0.85, fbm(uv * 5.0 - t * 0.015));

    col += vec3(0.90, 0.93, 1.0) * starLayer(uv, 28.0, 0.958, 2.0) * 0.8;
    col += vec3(0.85, 0.9, 1.0) * starLayer(uv * 1.5 + 11.0, 44.0, 0.975, 3.0) * 0.55;
    return col;
}

void main() {
    vec2 uv = TexCoord;
    float t = Time * Speed;

    vec3 col;
    float accentStrength;
    if (Mode < 0.5) {
        col = nebula(uv, t);
        accentStrength = 0.30;
    } else if (Mode < 1.5) {
        col = aurora(uv, t);
        accentStrength = 0.16;
    } else if (Mode < 2.5) {
        col = starfall(uv, t);
        accentStrength = 0.14;
    } else {
        col = galaxy(uv, t);
        accentStrength = 0.22;
    }

    // Переливы акцентным цветом темы
    float wave = fbm(uv * 1.7 - vec2(t * 0.060, t * 0.020));
    col += AccentColor * pow(wave, 2.2) * accentStrength;

    // У горизонта темнее, к зениту насыщеннее
    col *= mix(0.55, 1.15, smoothstep(0.0, 0.9, uv.y));

    col = col * (0.75 + 0.55 * Intensity);
    col = clamp(col, 0.0, 1.0);

    OutColor = vec4(col, 1.0);
}
