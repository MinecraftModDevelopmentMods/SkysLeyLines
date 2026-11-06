package zone.moddev.mc.skysleylines.api;

import java.util.List;
import java.util.Optional;

/** Reuse a column in generation loops. The primitive strength method allocates nothing and reads no terrain. */
public record LeyColumn(boolean active, int naturalSurfaceY, int verticalReach, double lineStrength,
                        double nexusStrength, Optional<Nexus> nexus, double distanceToLine, double distanceToNexus,
                        List<Influence> authored) {
    /** A projected connector (null nexus) or pin at this column. */
    public record Influence(double anchorY, double strength, double distance, double horizontalReach, Nexus nexus) {}

    public LeyColumn(boolean active, int naturalSurfaceY, int verticalReach, double lineStrength,
                     double nexusStrength, Optional<Nexus> nexus, double distanceToLine, double distanceToNexus) {
        this(active, naturalSurfaceY, verticalReach, lineStrength, nexusStrength, nexus, distanceToLine, distanceToNexus, List.of());
    }

    public LeyColumn {
        authored = List.copyOf(authored);
    }

    private double factor(double anchor, int y) {
        return Math.max(0, 1.0 - Math.abs(y - anchor) / verticalReach);
    }

    @Override public double lineStrength() {
        double result = lineStrength;
        for (int i = 0; i < authored.size(); i++) {
            var entry = authored.get(i);
            if (entry.nexus == null) result = Math.max(result, entry.strength);
        }
        return result;
    }

    @Override public double nexusStrength() {
        double result = nexusStrength;
        for (int i = 0; i < authored.size(); i++) {
            var entry = authored.get(i);
            if (entry.nexus != null) result = Math.max(result, entry.strength);
        }
        return result;
    }

    @Override public Optional<Nexus> nexus() {
        Nexus result = nexus.orElse(null);
        double best = distanceToNexus;
        for (int i = 0; i < authored.size(); i++) {
            var entry = authored.get(i);
            if (entry.nexus != null && entry.distance < best) {
                result = entry.nexus;
                best = entry.distance;
            }
        }
        return Optional.ofNullable(result);
    }

    @Override public double distanceToLine() { return planarDistance(distanceToLine, false); }
    @Override public double distanceToNexus() { return planarDistance(distanceToNexus, true); }

    private double planarDistance(double distance, boolean toNexus) {
        for (int i = 0; i < authored.size(); i++) {
            var entry = authored.get(i);
            if ((entry.nexus != null) == toNexus) distance = Math.min(distance, entry.distance);
        }
        return distance;
    }

    public double verticalFactorAt(int y) {
        if (!active) return 0;
        double result = factor(naturalSurfaceY, y);
        for (int i = 0; i < authored.size(); i++) result = Math.max(result, factor(authored.get(i).anchorY, y));
        return result;
    }

    public double naturalStrengthAt(int y) {
        if (!active) return 0;
        double result = Math.max(lineStrength, nexusStrength) * factor(naturalSurfaceY, y);
        for (int i = 0; i < authored.size(); i++) {
            var entry = authored.get(i);
            result = Math.max(result, entry.strength * factor(entry.anchorY, y));
        }
        return result;
    }

    public LeySample atHeight(int y) {
        double vertical = active ? factor(naturalSurfaceY, y) : 0;
        double line = lineStrength * vertical, node = nexusStrength * vertical;
        if (authored.isEmpty()) return new LeySample(active, Math.max(line, node), line, node, vertical, nexus, distanceToLine, distanceToNexus);
        Nexus selected = vertical > 0 ? nexus.orElse(null) : null;
        double selectedVertical = vertical, selectedDistance = distanceToNexus;
        double score = Double.POSITIVE_INFINITY;
        double bestVertical = vertical;
        for (int i = 0; i < authored.size(); i++) {
            var entry = authored.get(i);
            double local = factor(entry.anchorY, y);
            bestVertical = Math.max(bestVertical, local);
            if (entry.nexus == null) {
                line = Math.max(line, entry.strength * local);
                continue;
            }
            node = Math.max(node, entry.strength * local);
            if (local <= 0) continue;
            if (score == Double.POSITIVE_INFINITY && selected != null)
                score = score(selectedDistance, entry.horizontalReach, selectedVertical);
            double rank = score(entry.distance, entry.horizontalReach, local);
            if (rank < score || (rank == score && (selected == null
                    || (rank == 0 && selected.id().pinId().isEmpty()) || entry.nexus.id().compareTo(selected.id()) < 0))) {
                score = rank;
                selected = entry.nexus;
                selectedVertical = local;
                selectedDistance = entry.distance;
            }
        }
        return new LeySample(active, Math.max(line, node), line, node, selected == null ? bestVertical : selectedVertical,
                Optional.ofNullable(selected), distanceToLine(), selected == null ? distanceToNexus() : selectedDistance);
    }

    private static double score(double distance, double reach, double vertical) {
        double horizontal = distance / reach;
        double height = 1 - vertical;
        return horizontal * horizontal + height * height;
    }

    public static LeyColumn inactive() {
        return new LeyColumn(false, 0, 1, 0, 0, Optional.empty(), Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }
}
