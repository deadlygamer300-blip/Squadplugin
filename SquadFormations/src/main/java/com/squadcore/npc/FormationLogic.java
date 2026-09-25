package com.squadcore.npc;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure math: given a formation shape, a member count and a spacing/radius
 * parameter, produces a list of commander-local offset vectors.
 * <p>
 * Local space convention: X = to the commander's right, Z = behind the
 * commander (positive Z = further back), Y is always 0 (flattened to ground
 * by the caller). These vectors are later rotated into world space by the
 * commander's yaw in {@link FollowRunnable}.
 */
public final class FormationLogic {

    public enum Shape {
        CIRCLE,
        SQUARE,
        GRID,
        TRIANGLE;

        public static Shape fromString(String s) {
            return switch (s.toLowerCase()) {
                case "circle" -> CIRCLE;
                case "square" -> SQUARE;
                case "grid" -> GRID;
                case "triangle" -> TRIANGLE;
                default -> null;
            };
        }
    }

    private FormationLogic() {
    }

    public static List<Vector> computeOffsets(Shape shape, int count, double param) {
        return switch (shape) {
            case CIRCLE -> circle(count, param);
            case SQUARE, GRID -> grid(count, param);
            case TRIANGLE -> triangle(count, param);
        };
    }

    /**
     * Evenly distributes {@code count} NPCs around a ring of the given radius,
     * centered on the commander. angle = 2*PI*i/count ; x = r*cos, z = r*sin.
     */
    private static List<Vector> circle(int count, double radius) {
        List<Vector> offsets = new ArrayList<>(count);
        if (count <= 0) {
            return offsets;
        }
        double step = (2 * Math.PI) / count;
        for (int i = 0; i < count; i++) {
            double angle = step * i;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            offsets.add(new Vector(x, 0, z));
        }
        return offsets;
    }

    /**
     * Arranges NPCs into an N x M grid behind the commander, centered on the
     * commander's left/right axis, with {@code spacing} blocks between members.
     * The grid starts 2 blocks behind the commander so nobody spawns on top
     * of them.
     */
    private static List<Vector> grid(int count, double spacing) {
        List<Vector> offsets = new ArrayList<>(count);
        if (count <= 0) {
            return offsets;
        }
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil((double) count / columns);

        double halfWidth = (columns - 1) / 2.0;

        int placed = 0;
        for (int row = 0; row < rows && placed < count; row++) {
            for (int col = 0; col < columns && placed < count; col++) {
                double x = (col - halfWidth) * spacing;
                double z = 2.0 + row * spacing; // 2.0 block buffer behind commander
                offsets.add(new Vector(x, 0, z));
                placed++;
            }
        }
        return offsets;
    }

    /**
     * Wedge / V-formation pointing behind the commander. Row 0 is the tip
     * (closest to the commander, 1 member), each subsequent row gains 2
     * members (one on each wing), fanning outward and backward.
     */
    private static List<Vector> triangle(int count, double spacing) {
        List<Vector> offsets = new ArrayList<>(count);
        if (count <= 0) {
            return offsets;
        }

        int placed = 0;
        int row = 0;
        while (placed < count) {
            double z = 2.0 + row * spacing;
            if (row == 0) {
                offsets.add(new Vector(0, 0, z));
                placed++;
            } else {
                // right wing member
                offsets.add(new Vector(row * (spacing * 0.75), 0, z));
                placed++;
                if (placed < count) {
                    // left wing member
                    offsets.add(new Vector(-row * (spacing * 0.75), 0, z));
                    placed++;
                }
            }
            row++;
        }
        return offsets;
    }

    /**
     * Rotates a commander-local offset (X = right, Z = behind) into world
     * space using the commander's yaw, in Minecraft's yaw convention
     * (0 = south/+Z, 90 = west/-X, increasing clockwise when viewed from above).
     */
    public static Vector rotateToWorld(Vector localOffset, float commanderYawDegrees) {
        double yawRad = Math.toRadians(commanderYawDegrees);

        // Forward vector in world space for a given Minecraft yaw.
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        // Right vector is forward rotated -90 degrees.
        double rightX = -Math.sin(yawRad + Math.PI / 2);
        double rightZ = Math.cos(yawRad + Math.PI / 2);

        double worldX = rightX * localOffset.getX() + forwardX * localOffset.getZ();
        double worldZ = rightZ * localOffset.getX() + forwardZ * localOffset.getZ();

        return new Vector(worldX, localOffset.getY(), worldZ);
    }
}
