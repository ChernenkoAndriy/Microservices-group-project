package com.epam.java.specialization.authservice.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokensTest {

    @ParameterizedTest
    @ValueSource(strings = {"Bearer abc.def.ghi", "bearer abc.def.ghi", "BEARER   abc.def.ghi  ", "abc.def.ghi"})
    void extractsTokenWithOrWithoutPrefix(String header) {
        assertThat(BearerTokens.extract(header)).isEqualTo("abc.def.ghi");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void returnsNullForMissingHeader(String header) {
        assertThat(BearerTokens.extract(header)).isNull();
    }

    @Test
    void returnsEmptyStringForPrefixWithoutToken() {
        assertThat(BearerTokens.extract("Bearer ")).isEmpty();
    }
}
