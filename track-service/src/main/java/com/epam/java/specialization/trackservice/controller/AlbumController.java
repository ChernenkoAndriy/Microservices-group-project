package com.epam.java.specialization.trackservice.controller;

import com.epam.java.specialization.trackservice.api.AlbumsApi;
import com.epam.java.specialization.trackservice.api.dto.AlbumDto;
import com.epam.java.specialization.trackservice.api.dto.CreateAlbumRequestDto;
import com.epam.java.specialization.trackservice.service.AlbumService;
import com.epam.java.specialization.trackservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AlbumController implements AlbumsApi {

    private final AlbumService albumService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<AlbumDto> createAlbum(CreateAlbumRequestDto request) {
        Long artistId = currentUser.requireRole(CurrentUser.ARTIST);
        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.create(artistId, request));
    }

    @Override
    public ResponseEntity<AlbumDto> getAlbum(Long albumId) {
        Long callerId = currentUser.id().orElse(null);
        return ResponseEntity.ok(albumService.get(albumId, callerId, currentUser.hasRole(CurrentUser.ADMIN)));
    }
}
