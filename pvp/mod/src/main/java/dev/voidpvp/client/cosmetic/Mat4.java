package dev.voidpvp.client.cosmetic;

/**
 * Column-major 4x4 helpers mirroring the fixed-function calls the renderer makes, so a bone's
 * transform can be followed in Java without reading GL state back per bone. Only used to place
 * glow anchors in eye space; the geometry itself is transformed by GL.
 */
final class Mat4 {

    private Mat4() {
    }

    static void identity(float[] m) {
        for (int i = 0; i < 16; i++) {
            m[i] = (i % 5 == 0) ? 1 : 0;
        }
    }

    static void copy(float[] src, float[] dst) {
        System.arraycopy(src, 0, dst, 0, 16);
    }

    /** m = m * T(x, y, z). */
    static void translate(float[] m, float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
    }

    static void scale(float[] m, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
    }

    /** m = m * R(angle radians about a unit axis). */
    static void rotate(float[] m, float angle, float ax, float ay, float az) {
        if (angle == 0) {
            return;
        }
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle), t = 1 - c;
        float r00 = t * ax * ax + c, r01 = t * ax * ay - s * az, r02 = t * ax * az + s * ay;
        float r10 = t * ax * ay + s * az, r11 = t * ay * ay + c, r12 = t * ay * az - s * ax;
        float r20 = t * ax * az - s * ay, r21 = t * ay * az + s * ax, r22 = t * az * az + c;
        for (int i = 0; i < 4; i++) {
            float a = m[i], b = m[4 + i], d = m[8 + i];
            m[i] = a * r00 + b * r10 + d * r20;
            m[4 + i] = a * r01 + b * r11 + d * r21;
            m[8 + i] = a * r02 + b * r12 + d * r22;
        }
    }

    /** out = m * (x, y, z, 1), xyz only. */
    static void transform(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    /** Uniform scale of the upper 3x3, from its first column. */
    static float scaleOf(float[] m) {
        return (float) Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
    }

    /** dst = inverse of an affine m (upper 3x3 invertible, last row 0 0 0 1). False if singular. */
    static boolean invertAffine(float[] m, float[] dst) {
        float a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], k = m[10];
        float A = e * k - f * h, B = -(d * k - f * g), C = d * h - e * g;
        float det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12f) {
            return false;
        }
        float id = 1f / det;
        float i00 = A * id, i01 = -(b * k - c * h) * id, i02 = (b * f - c * e) * id;
        float i10 = B * id, i11 = (a * k - c * g) * id, i12 = -(a * f - c * d) * id;
        float i20 = C * id, i21 = -(a * h - b * g) * id, i22 = (a * e - b * d) * id;
        float tx = m[12], ty = m[13], tz = m[14];
        dst[0] = i00;
        dst[1] = i10;
        dst[2] = i20;
        dst[3] = 0;
        dst[4] = i01;
        dst[5] = i11;
        dst[6] = i21;
        dst[7] = 0;
        dst[8] = i02;
        dst[9] = i12;
        dst[10] = i22;
        dst[11] = 0;
        dst[12] = -(i00 * tx + i01 * ty + i02 * tz);
        dst[13] = -(i10 * tx + i11 * ty + i12 * tz);
        dst[14] = -(i20 * tx + i21 * ty + i22 * tz);
        dst[15] = 1;
        return true;
    }
}
