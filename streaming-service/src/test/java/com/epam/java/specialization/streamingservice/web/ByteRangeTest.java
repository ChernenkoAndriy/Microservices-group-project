package com.epam.java.specialization.streamingservice.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteRangeTest {

    @Test
    void noHeaderMeansTheWholeFile() {
        assertThat(ByteRange.parse(null, 100)).isEmpty();
        assertThat(ByteRange.parse(" ", 100)).isEmpty();
    }

    @Test
    void parsesTheThreeForms() {
        assertThat(ByteRange.parse("bytes=0-", 100)).contains(new ByteRange(0, 99));
        assertThat(ByteRange.parse("bytes=10-19", 100)).contains(new ByteRange(10, 19));
        assertThat(ByteRange.parse("bytes=-30", 100)).contains(new ByteRange(70, 99));
        assertThat(ByteRange.parse("bytes=-300", 100)).contains(new ByteRange(0, 99));
    }

    @Test
    void endPastTheFileIsCut() {
        ByteRange range = ByteRange.parse("bytes=90-500", 100).orElseThrow();

        assertThat(range).isEqualTo(new ByteRange(90, 99));
        assertThat(range.length()).isEqualTo(10);
        assertThat(range.contentRange(100)).isEqualTo("bytes 90-99/100");
    }

    @Test
    void rangesOutsideTheFileAreUnsatisfiable() {
        for (String header : new String[]{"bytes=100-", "bytes=50-10", "bytes=-0", "bytes=-", "items=0-1"}) {
            assertThatThrownBy(() -> ByteRange.parse(header, 100))
                    .isInstanceOfSatisfying(ByteRange.Unsatisfiable.class, e -> assertThat(e.getSize()).isEqualTo(100));
        }
    }
}
