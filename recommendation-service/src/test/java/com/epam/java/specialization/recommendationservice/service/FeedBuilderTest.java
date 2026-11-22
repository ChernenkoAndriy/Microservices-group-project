package com.epam.java.specialization.recommendationservice.service;

import com.epam.java.specialization.recommendationservice.api.dto.RecommendationReasonDto;
import com.epam.java.specialization.recommendationservice.model.TrackInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeedBuilderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private static final List<TrackInfo> TRACKS = List.of(
            new TrackInfo(1, 10, "Lukewarm Forever", Set.of("soft-rock"), "PUBLISHED", LocalDate.of(2020, 1, 1)),
            new TrackInfo(2, 10, "Mildly Upset", Set.of("soft-rock"), "PUBLISHED", LocalDate.of(2020, 1, 1)),
            new TrackInfo(3, 20, "Accordion Crimes", Set.of("polka"), "PUBLISHED", LocalDate.of(2020, 1, 1)),
            new TrackInfo(4, 30, "Dial-Up Love Song", Set.of("synthwave"), "PUBLISHED", TODAY.minusDays(3)),
            new TrackInfo(5, 40, "Elevator to Nowhere", Set.of("muzak"), "PUBLISHED", LocalDate.of(2020, 1, 1)));

    private static FeedBuilder.Input input(Set<Long> liked, Map<Long, Long> userPlays, Map<Long, Long> popularity,
                                           Map<Long, Integer> impressions, String genre, int limit) {
        return new FeedBuilder.Input(TRACKS, liked, userPlays, popularity, impressions, genre, TODAY, limit);
    }

    @Test
    void withoutProfileTheFeedIsTrendingByPopularity() {
        List<FeedBuilder.Pick> feed = FeedBuilder.build(
                input(Set.of(), Map.of(), Map.of(3L, 10L, 5L, 4L), Map.of(), null, 3));

        assertThat(feed).extracting(FeedBuilder.Pick::trackId).startsWith(3L);
        assertThat(feed).allMatch(p -> p.reason() == RecommendationReasonDto.TRENDING);
        assertThat(feed).hasSize(3);
    }

    @Test
    void sameArtistAsALikedTrackComesFirstAndLikedTracksAreLeftOut() {
        List<FeedBuilder.Pick> feed = FeedBuilder.build(
                input(Set.of(1L), Map.of(), Map.of(3L, 100L), Map.of(), null, 10));

        assertThat(feed.getFirst()).isEqualTo(new FeedBuilder.Pick(2L, RecommendationReasonDto.SIMILAR_TO_LIKED));
        assertThat(feed).extracting(FeedBuilder.Pick::trackId).doesNotContain(1L);
    }

    @Test
    void playedGenresGivePopularInGenre() {
        List<FeedBuilder.Pick> feed = FeedBuilder.build(
                input(Set.of(), Map.of(3L, 5L), Map.of(), Map.of(), null, 10));

        assertThat(feed.getFirst()).isEqualTo(new FeedBuilder.Pick(3L, RecommendationReasonDto.POPULAR_IN_GENRE));
    }

    @Test
    void recentlyShownTracksMoveBack() {
        List<FeedBuilder.Pick> first = FeedBuilder.build(
                input(Set.of(1L), Map.of(), Map.of(), Map.of(), null, 10));
        List<FeedBuilder.Pick> next = FeedBuilder.build(
                input(Set.of(1L), Map.of(), Map.of(), Map.of(2L, 3), null, 10));

        assertThat(first.getFirst().trackId()).isEqualTo(2L);
        assertThat(next.getFirst().trackId()).isNotEqualTo(2L);
        assertThat(next).extracting(FeedBuilder.Pick::trackId).contains(2L);
    }

    @Test
    void genreFilterAndNewRelease() {
        List<FeedBuilder.Pick> feed = FeedBuilder.build(
                input(Set.of(3L), Map.of(), Map.of(), Map.of(), "synthwave", 10));

        assertThat(feed).containsExactly(new FeedBuilder.Pick(4L, RecommendationReasonDto.NEW_RELEASE));
    }
}
