package zone.moddev.mc.skysleylines.api;

import java.util.Objects;

/** Colours are 24-bit RGB. Opacity and scale multiply the selected pattern's base values. */
public record LeyVisionStyle(Pattern pattern, int lineRgb, int nexusRgb, float opacity, float scale) {
    public enum Pattern { RIVER, CRACKLE, SPARKLES, GLOW }

    public LeyVisionStyle {
        Objects.requireNonNull(pattern);
        if ((lineRgb & 0xff000000) != 0 || (nexusRgb & 0xff000000) != 0)
            throw new IllegalArgumentException("Ley vision colours must be 24-bit RGB");
        if (!(opacity >= 0.05f && opacity <= 1.0f && scale >= 0.25f && scale <= 1.5f))
            throw new IllegalArgumentException("Ley vision opacity must be 0.05–1 and scale must be 0.25–1.5");
    }
}
