#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
varying vec2 texCoord;

uniform float m_Saturation;
uniform float m_Contrast;
uniform float m_Vignette;
uniform vec3 m_ShadowTint;
uniform vec3 m_HighTint;
uniform float m_Exposure;

// Farbstimmung wie in einer Märchen-Illustration: weiche Lichter, warme Höhen, magische Schatten.
void main() {
    vec4 tex = getColor(m_Texture, texCoord);
    vec3 c = tex.rgb * m_Exposure;
    // sanfte Schulter statt hartem Ausbrennen
    c = c / (1.0 + max(c - 0.75, 0.0) * 0.9);
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    // Sättigung
    c = mix(vec3(l), c, m_Saturation);
    // Teilton: Schatten getönt, Lichter golden
    float sh = (1.0 - l) * (1.0 - l);
    float hi = l * l;
    c += m_ShadowTint * sh + m_HighTint * hi;
    // weicher S-Kontrast
    c = mix(c, c * c * (3.0 - 2.0 * c), m_Contrast);
    // Vignette
    vec2 d = texCoord - vec2(0.5);
    float v = smoothstep(0.85, 0.25, length(d * vec2(1.15, 1.0)));
    c *= mix(1.0, v, m_Vignette);
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), tex.a);
}
