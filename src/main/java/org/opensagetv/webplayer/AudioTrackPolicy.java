package org.opensagetv.webplayer;

import java.util.List;
import java.util.Locale;

/** Deterministic browser/server audio selection. Explicit track always wins. */
final class AudioTrackPolicy {
    private AudioTrackPolicy() {}

    static MediaProbe.Track choose(List<MediaProbe.Track> tracks, StreamOptions o) {
        if (tracks == null || tracks.isEmpty()) return null;
        if (o.audioTrack >= 0) {
            for (MediaProbe.Track t : tracks) if (t.index == o.audioTrack) return t;
            throw new IllegalArgumentException("Selected audio track is not in this recording. Select Automatic or refresh tracks.");
        }
        String wanted = MediaProbe.languageKey(o.audioLanguage);
        MediaProbe.Track chosen = best(tracks, wanted, !wanted.isEmpty());
        if (chosen != null) return chosen;
        if (!wanted.isEmpty() && "strict".equals(o.audioFallback))
            throw new IllegalArgumentException("Preferred audio language " + o.audioLanguage + " is not available in this recording.");
        if ("first".equals(o.audioFallback)) return tracks.get(0);
        chosen = best(tracks, "", false);
        return chosen == null ? tracks.get(0) : chosen;
    }

    private static MediaProbe.Track best(List<MediaProbe.Track> tracks, String wanted, boolean requireLanguage) {
        MediaProbe.Track best = null; int bestScore = Integer.MIN_VALUE;
        for (MediaProbe.Track t : tracks) {
            String actual = MediaProbe.languageKey(t.language);
            if (requireLanguage && !wanted.equals(actual)) continue;
            int score = 0;
            if (!wanted.isEmpty() && wanted.equals(actual)) score += 1000;
            if (t.defaultTrack) score += 120;
            if (!isSecondary(t)) score += 80; else score -= 250;
            // Stable source order is the final tie breaker.
            score -= Math.max(0, t.index);
            if (score > bestScore) { bestScore = score; best = t; }
        }
        return best;
    }

    static boolean isSecondary(MediaProbe.Track t) {
        if (t == null) return false;
        if (t.visualImpaired) return true;
        String text = ((t.title == null ? "" : t.title) + " " + (t.language == null ? "" : t.language)).toLowerCase(Locale.ROOT);
        return text.contains("commentary") || text.contains("narration") || text.contains("narrative")
                || text.contains("audio description") || text.contains("descriptive") || text.contains("director");
    }
}
