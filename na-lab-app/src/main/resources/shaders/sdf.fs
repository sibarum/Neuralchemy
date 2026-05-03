#version 330

// Signed-distance-field font fragment shader. Pairs with raylib's default vertex shader,
// which forwards fragTexCoord and fragColor and exposes texture0 + colDiffuse uniforms.
//
// raylib bakes the SDF atlas in the alpha channel: 0.5 = glyph edge, > 0.5 = inside,
// < 0.5 = outside. fwidth() lets us pick a screen-space-correct anti-alias width so the
// glyph stays crisp at any rendered size, regardless of how far it is from the atlas baseSize.

in vec2 fragTexCoord;
in vec4 fragColor;

uniform sampler2D texture0;
uniform vec4 colDiffuse;

out vec4 finalColor;

void main()
{
    float distance = texture(texture0, fragTexCoord).a;
    float aa = fwidth(distance) * 0.75;
    float alpha = smoothstep(0.5 - aa, 0.5 + aa, distance);
    finalColor = vec4(fragColor.rgb, fragColor.a * alpha) * colDiffuse;
}
