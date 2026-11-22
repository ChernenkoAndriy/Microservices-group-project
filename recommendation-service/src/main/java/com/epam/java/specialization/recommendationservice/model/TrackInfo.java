package com.epam.java.specialization.recommendationservice.model;

import java.time.LocalDate;
import java.util.Set;

/** The local copy of a catalog track, built from {@code TrackSnapshot} events. */
public record TrackInfo(long trackId, long artistId, String title, Set<String> genres, String status,
                        LocalDate releaseDate) {
}
