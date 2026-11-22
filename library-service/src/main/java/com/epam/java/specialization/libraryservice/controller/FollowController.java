package com.epam.java.specialization.libraryservice.controller;

import com.epam.java.specialization.libraryservice.api.FollowsApi;
import com.epam.java.specialization.libraryservice.api.dto.FollowDto;
import com.epam.java.specialization.libraryservice.api.dto.FollowPageDto;
import com.epam.java.specialization.libraryservice.service.FollowService;
import com.epam.java.specialization.libraryservice.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
public class FollowController implements FollowsApi {

    private final FollowService followService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<FollowPageDto> listMyFollows(Set<Long> artistIds, Integer page, Integer size) {
        return ResponseEntity.ok(followService.list(currentUser.requireId(), artistIds, page, size));
    }

    @Override
    public ResponseEntity<FollowDto> followArtist(Long artistId) {
        FollowService.FollowResult result = followService.follow(currentUser.requireId(), artistId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.follow());
    }

    @Override
    public ResponseEntity<Void> unfollowArtist(Long artistId) {
        followService.unfollow(currentUser.requireId(), artistId);
        return ResponseEntity.noContent().build();
    }
}
