#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MultiSample.glsllib"

uniform COLORTEXTURE m_Texture;
uniform DEPTHTEXTURE m_DepthTexture;
varying vec2 texCoord;

uniform vec4 m_FogColor;
uniform float m_FogDensity;
uniform vec2 m_NearFar;
uniform float m_SkyHaze;

// Exponentieller Distanznebel, der den Himmel (Tiefe = 1) nur leicht eintrübt.
void main() {
    vec4 texVal = getColor(m_Texture, texCoord);
    float z = getDepth(m_DepthTexture, texCoord).r;
    float n = m_NearFar.x;
    float f = m_NearFar.y;
    float ndc = z * 2.0 - 1.0;
    float dist = (2.0 * n * f) / (f + n - ndc * (f - n));
    float fogAmount = 1.0 - exp(-pow(dist * m_FogDensity, 1.6));
    if (z >= 0.99999) {
        fogAmount = m_SkyHaze;
    }
    fogAmount = clamp(fogAmount, 0.0, 1.0);
    gl_FragColor = vec4(mix(texVal.rgb, m_FogColor.rgb, fogAmount), texVal.a);
}
