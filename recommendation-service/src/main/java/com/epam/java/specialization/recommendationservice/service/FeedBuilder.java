package com.epam.java.specialization.recommendationservice.service;

import com.epam.java.specialization.recommendationservice.api.dto.RecommendationReasonDto;
import com.epam.java.specialization.recommendationservice.model.TrackInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranks published tracks for one user. Pure function of its inputs, so it is unit tested without ClickHouse.
 * <ul>
 *   <li>Taste profile: the artists and genres of the liked tracks (weight 3 and 2) and the genres of the played
 *       tracks (weight 1 per genre).</li>
 *   <li>Liked tracks are not recommended again.</li>
 *   <li>Tracks shown recently (impressions) get a penalty per showing, so the next feed brings other tracks first;
 *       they are not removed, because a small catalog would otherwise run out of tracks.</li>
 *   <li>No likes and no plays: {@code TRENDING}, ranked by popularity.</li>
 * </ul>
 */
public final class FeedBuilder {

    static final int NEW_RELEASE_DAYS = 30;

    private FeedBuilder() {
    }

    public record Input(List<TrackInfo> published, Set<Long> liked, Map<Long, Long> userPlays,
                        Map<Long, Long> popularity, Map<Long, Integer> recentImpressions, String genre,
                        LocalDate today, int limit) {
    }

    public record Pick(long trackId, RecommendationReasonDto reason) {
    }

    public static List<Pick> build(Input in) {
        Map<Long, TrackInfo> byId = new HashMap<>();
        in.published().forEach(t -> byId.put(t.trackId(), t));

        Map<Long, Integer> likedArtists = new HashMap<>();
        Map<String, Integer> likedGenres = new HashMap<>();
        for (Long id : in.liked()) {
            TrackInfo t = byId.get(id);
            if (t != null) {
                likedArtists.merge(t.artistId(), 1, Integer::sum);
                t.genres().forEach(g -> likedGenres.merge(g, 1, Integer::sum));
            }
        }
        Map<String, Long> playedGenres = new HashMap<>();
        in.userPlays().forEach((id, plays) -> {
            TrackInfo t = byId.get(id);
            if (t != null) {
                t.genres().forEach(g -> playedGenres.merge(g, plays, Long::sum));
            }
        });
        boolean hasProfile = !likedArtists.isEmpty() || !playedGenres.isEmpty();
        long maxPopularity = in.popularity().values().stream().mapToLong(Long::longValue).max().orElse(0);

        List<Scored> scored = new ArrayList<>();
        for (TrackInfo t : in.published()) {
            if (in.liked().contains(t.trackId())) {
                continue;
            }
            if (in.genre() != null && !t.genres().contains(in.genre())) {
                continue;
            }
            double artist = 3.0 * Math.min(likedArtists.getOrDefault(t.artistId(), 0), 2);
            double likedGenre = 2.0 * t.genres().stream().mapToInt(g -> Math.min(likedGenres.getOrDefault(g, 0), 2))
                    .sum();
            double playedGenre = t.genres().stream()
                    .mapToDouble(g -> Math.min(Math.log1p(playedGenres.getOrDefault(g, 0L)), 2.0)).sum();
            boolean isNew = t.releaseDate() != null
                    && !t.releaseDate().isBefore(in.today().minusDays(NEW_RELEASE_DAYS))
                    && !t.releaseDate().isAfter(in.today());
            double popularity = maxPopularity == 0 ? 0
                    : (double) in.popularity().getOrDefault(t.trackId(), 0L) / maxPopularity;
            double penalty = 1.5 * in.recentImpressions().getOrDefault(t.trackId(), 0);

            double score = artist + likedGenre + playedGenre + (isNew ? 1.0 : 0) + popularity - penalty;
            RecommendationReasonDto reason;
            if (!hasProfile) {
                reason = RecommendationReasonDto.TRENDING;
            } else if (artist + likedGenre > 0) {
                reason = RecommendationReasonDto.SIMILAR_TO_LIKED;
            } else if (playedGenre > 0) {
                reason = RecommendationReasonDto.POPULAR_IN_GENRE;
            } else if (isNew) {
                reason = RecommendationReasonDto.NEW_RELEASE;
            } else {
                reason = RecommendationReasonDto.TRENDING;
            }
            scored.add(new Scored(t.trackId(), score, reason));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed().thenComparingLong(Scored::trackId))
                .limit(in.limit())
                .map(s -> new Pick(s.trackId(), s.reason()))
                .toList();
    }

    private record Scored(long trackId, double score, RecommendationReasonDto reason) {
    }
}
