package com.epam.java.specialization.trackservice.repository;

import com.epam.java.specialization.trackservice.model.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GenreRepository extends JpaRepository<Genre, String> {

    List<Genre> findAllByOrderByNameAsc();
}
