package com.epam.java.specialization.authservice.dto;

import com.epam.java.specialization.authservice.model.User;
import org.springframework.data.domain.Page;

import java.util.List;

public record UserPage(List<UserResponse> items, PageMetadata page) {

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }

    public static UserPage from(Page<User> page) {
        return new UserPage(
                page.map(UserResponse::from).getContent(),
                new PageMetadata(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}
