package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.api.GenresApi;
import com.epam.java.specialization.trackservice.api.dto.GenreDto;
import com.epam.java.specialization.trackservice.mapper.TrackMapper;
import com.epam.java.specialization.trackservice.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GenreController implements GenresApi {

    private final GenreRepository genreRepository;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<GenreDto>> listGenres() {
        return ResponseEntity.ok(genreRepository.findAllByOrderByNameAsc().stream().map(TrackMapper::toDto).toList());
    }
}
