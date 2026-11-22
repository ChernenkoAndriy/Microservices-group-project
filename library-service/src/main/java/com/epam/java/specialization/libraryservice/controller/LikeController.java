package com.epam.java.specialization.libraryservice.controller;

import com.epam.java.specialization.libraryservice.api.LikesApi;
import com.epam.java.specialization.libraryservice.api.dto.LikeDto;
import com.epam.java.specialization.libraryservice.api.dto.LikePageDto;
import com.epam.java.specialization.libraryservice.service.LikeService;
import com.epam.java.specialization.libraryservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
public class LikeController implements LikesApi {

    private final LikeService likeService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<LikePageDto> listMyLikes(Set<Long> trackIds, Integer page, Integer size) {
        return ResponseEntity.ok(likeService.list(currentUser.requireId(), trackIds, page, size));
    }

    @Override
    public ResponseEntity<LikeDto> likeTrack(Long trackId) {
        LikeService.LikeResult result = likeService.like(currentUser.requireId(), trackId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.like());
    }

    @Override
    public ResponseEntity<Void> unlikeTrack(Long trackId) {
        likeService.unlike(currentUser.requireId(), trackId);
        return ResponseEntity.noContent().build();
    }
}
