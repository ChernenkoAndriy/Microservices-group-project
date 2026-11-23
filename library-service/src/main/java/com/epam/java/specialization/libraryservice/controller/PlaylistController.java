package com.epam.java.specialization.libraryservice.controller;

import com.epam.java.specialization.libraryservice.api.PlaylistsApi;
import com.epam.java.specialization.libraryservice.api.dto.CreatePlaylistRequestDto;
import com.epam.java.specialization.libraryservice.api.dto.PlaylistDto;
import com.epam.java.specialization.libraryservice.api.dto.PlaylistSummaryPageDto;
import com.epam.java.specialization.libraryservice.api.dto.UpdatePlaylistRequestDto;
import com.epam.java.specialization.libraryservice.service.PlaylistService;
import com.epam.java.specialization.libraryservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlaylistController implements PlaylistsApi {

    private final PlaylistService playlistService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<PlaylistSummaryPageDto> listMyPlaylists(Integer page, Integer size) {
        return ResponseEntity.ok(playlistService.list(currentUser.requireId(), page, size));
    }

    @Override
    public ResponseEntity<PlaylistDto> createPlaylist(CreatePlaylistRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(playlistService.create(currentUser.requireId(), request));
    }

    @Override
    public ResponseEntity<PlaylistDto> getPlaylist(Long playlistId) {
        return ResponseEntity.ok(playlistService.get(currentUser.requireId(), playlistId));
    }

    @Override
    public ResponseEntity<PlaylistDto> updatePlaylist(Long playlistId, UpdatePlaylistRequestDto request) {
        return ResponseEntity.ok(playlistService.update(currentUser.requireId(), playlistId, request));
    }

    @Override
    public ResponseEntity<Void> deletePlaylist(Long playlistId) {
        playlistService.delete(currentUser.requireId(), playlistId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PlaylistDto> addPlaylistTrack(Long playlistId, Long trackId) {
        return ResponseEntity.ok(playlistService.addTrack(currentUser.requireId(), playlistId, trackId));
    }

    @Override
    public ResponseEntity<Void> removePlaylistTrack(Long playlistId, Long trackId) {
        playlistService.removeTrack(currentUser.requireId(), playlistId, trackId);
        return ResponseEntity.noContent().build();
    }
}
