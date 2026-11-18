#version 150

uniform sampler2D frame;
out vec4 bytes;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec3 pixel(ivec2 size, int x, int y) {
	return texelFetch(frame, ivec2(x, size.y - 1 - y), 0).rgb;
}

void main() {
	ivec2 size = textureSize(frame, 0);
	ivec2 texel = ivec2(gl_FragCoord.xy);
	int x = texel.x * 4;

	if (texel.y < size.y) {
		vec4 luma;
		for (int i = 0; i < 4; i++) luma[i] = 16.0 + 219.0 * dot(pixel(size, x + i, texel.y), LUMA);
		bytes = luma / 255.0;
		return;
	}

	int y = (texel.y - size.y) * 2;
	vec4 uv;
	for (int i = 0; i < 2; i++) {
		vec3 chroma = (pixel(size, x + i * 2, y) + pixel(size, x + i * 2 + 1, y) + pixel(size, x + i * 2, y + 1) + pixel(size, x + i * 2 + 1, y + 1)) * 0.25;
		float luma = dot(chroma, LUMA);
		uv[i * 2] = 128.0 + 224.0 * (chroma.b - luma) / 1.8556;
		uv[i * 2 + 1] = 128.0 + 224.0 * (chroma.r - luma) / 1.5748;
	}
	bytes = uv / 255.0;
}
