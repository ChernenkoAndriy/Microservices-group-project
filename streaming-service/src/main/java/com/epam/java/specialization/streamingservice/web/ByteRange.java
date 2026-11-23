package com.epam.java.specialization.streamingservice.web;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single HTTP byte range ({@code bytes=start-end}, {@code bytes=start-} or the suffix form {@code bytes=-n}),
 * resolved against the size of the file. Both ends are inclusive.
 */
public record ByteRange(long start, long end) {

    private static final Pattern RANGE = Pattern.compile("^bytes=(\\d*)-(\\d*)$");

    public long length() {
        return end - start + 1;
    }

    public String contentRange(long size) {
        return "bytes " + start + "-" + end + "/" + size;
    }

    /**
     * @return empty when there is no {@code Range} header (the whole file is sent)
     * @throws Unsatisfiable when the range lies outside the file or is malformed
     */
    public static Optional<ByteRange> parse(String header, long size) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        Matcher m = RANGE.matcher(header.trim());
        if (!m.matches() || (m.group(1).isEmpty() && m.group(2).isEmpty()) || size == 0) {
            throw new Unsatisfiable(size);
        }
        try {
            long start;
            long end;
            if (m.group(1).isEmpty()) {
                long suffix = Long.parseLong(m.group(2));
                if (suffix == 0) {
                    throw new Unsatisfiable(size);
                }
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(m.group(1));
                end = m.group(2).isEmpty() ? size - 1 : Math.min(Long.parseLong(m.group(2)), size - 1);
            }
            if (start >= size || start > end) {
                throw new Unsatisfiable(size);
            }
            return Optional.of(new ByteRange(start, end));
        } catch (NumberFormatException e) {
            throw new Unsatisfiable(size);
        }
    }

    /** The range cannot be served: answer 416 with {@code Content-Range: bytes *}{@code /size}. */
    public static class Unsatisfiable extends RuntimeException {

        private final long size;

        public Unsatisfiable(long size) {
            super("The requested range is outside the file");
            this.size = size;
        }

        public long getSize() {
            return size;
        }
    }
}
