#version 150

uniform sampler2D InSampler;
layout(std140) uniform FilterParams {
	float intensity;
	float contrast;
	float darkness;
};
in vec2 texCoord;
out vec4 fragColor;

void main() {
	vec4 color = texture(InSampler, texCoord);
	float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
	float filtered = clamp((luminance - 0.5) * contrast + 0.5 - darkness, 0.0, 1.0);
	fragColor = vec4(mix(color.rgb, vec3(filtered), intensity), color.a);
}