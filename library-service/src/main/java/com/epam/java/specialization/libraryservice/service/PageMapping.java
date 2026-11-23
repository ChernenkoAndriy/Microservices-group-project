package com.epam.java.specialization.libraryservice.service;

import com.epam.java.specialization.libraryservice.api.dto.PageMetadataDto;
import org.springframework.data.domain.Page;

/** Page metadata of the paged lists (api-contracts/README.md, "Paging"). */
final class PageMapping {

    private PageMapping() {
    }

    static PageMetadataDto toMetadata(Page<?> page) {
        return new PageMetadataDto(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
