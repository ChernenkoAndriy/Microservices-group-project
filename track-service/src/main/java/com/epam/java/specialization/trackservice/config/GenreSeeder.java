package com.epam.java.specialization.trackservice.config;

import com.epam.java.specialization.trackservice.model.Genre;
import com.epam.java.specialization.trackservice.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenreSeeder implements ApplicationRunner {

    static final List<Genre> DEFAULT_GENRES = List.of(
            new Genre("pop", "Pop"),
            new Genre("rock", "Rock"),
            new Genre("indie-rock", "Indie Rock"),
            new Genre("hip-hop", "Hip-Hop"),
            new Genre("electronic", "Electronic"),
            new Genre("jazz", "Jazz"),
            new Genre("classical", "Classical"),
            new Genre("metal", "Metal"),
            new Genre("folk", "Folk"),
            new Genre("ambient", "Ambient"));

    private final GenreRepository genreRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (genreRepository.count() == 0) {
            genreRepository.saveAll(DEFAULT_GENRES);
            log.info("Seeded {} genres", DEFAULT_GENRES.size());
        }
    }
}
