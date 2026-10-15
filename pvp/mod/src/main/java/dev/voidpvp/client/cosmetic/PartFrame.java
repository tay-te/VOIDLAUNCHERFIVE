package dev.voidpvp.client.cosmetic;

/**
 * One model part's transform, read off a {@code ModelPart} after {@code setAngles}: pivot in model
 * pixels and the Z-then-Y-then-X rotation {@code ModelPart.render} applies. Converts between the
 * part's local space and model space, which is what the cape collider needs to test a particle
 * against a tilted torso or a swinging leg.
 */
final class PartFrame {

    double px, py, pz;
    // Rows of R = Rz * Ry * Rx.
    double m00, m01, m02, m10, m11, m12, m20, m21, m22;

    void set(double pivotX, double pivotY, double pivotZ, double rx, double ry, double rz) {
        px = pivotX;
        py = pivotY;
        pz = pivotZ;
        double cx = Math.cos(rx), sx = Math.sin(rx);
        double cy = Math.cos(ry), sy = Math.sin(ry);
        double cz = Math.cos(rz), sz = Math.sin(rz);
        // Rz * Ry * Rx
        m00 = cz * cy;
        m01 = cz * sy * sx - sz * cx;
        m02 = cz * sy * cx + sz * sx;
        m10 = sz * cy;
        m11 = sz * sy * sx + cz * cx;
        m12 = sz * sy * cx - cz * sx;
        m20 = -sy;
        m21 = cy * sx;
        m22 = cy * cx;
    }

    void toModel(double x, double y, double z, double[] out) {
        out[0] = px + m00 * x + m01 * y + m02 * z;
        out[1] = py + m10 * x + m11 * y + m12 * z;
        out[2] = pz + m20 * x + m21 * y + m22 * z;
    }

    void toLocal(double x, double y, double z, double[] out) {
        double dx = x - px, dy = y - py, dz = z - pz;
        // R is orthonormal: inverse is the transpose.
        out[0] = m00 * dx + m10 * dy + m20 * dz;
        out[1] = m01 * dx + m11 * dy + m21 * dz;
        out[2] = m02 * dx + m12 * dy + m22 * dz;
    }
}
