package com.epam.java.specialization.streamingservice.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

/** Detects the type and size of a cover image from its bytes (JPEG, PNG, WebP). */
public record CoverImage(String contentType, int width, int height) {

    public static final int MIN_SIDE = 500;
    public static final int MAX_SIDE = 5000;

    /** @return empty when the bytes are not a JPEG, PNG or WebP image */
    public static Optional<CoverImage> inspect(byte[] b) {
        if (b.length < 30) {
            return Optional.empty();
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return decode(b, "image/jpeg");
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Optional.of(new CoverImage("image/png", be32(b, 16), be32(b, 20)));
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
                && b[10] == 'B' && b[11] == 'P') {
            return webp(b);
        }
        return Optional.empty();
    }

    public boolean isSquareWithinLimits() {
        return width == height && width >= MIN_SIDE && width <= MAX_SIDE;
    }

    private static Optional<CoverImage> decode(byte[] b, String type) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(b));
            return image == null ? Optional.empty()
                    : Optional.of(new CoverImage(type, image.getWidth(), image.getHeight()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<CoverImage> webp(byte[] b) {
        String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8X":
                return Optional.of(new CoverImage("image/webp", le24(b, 24) + 1, le24(b, 27) + 1));
            case "VP8L": {
                int bits = (b[21] & 0xFF) | (b[22] & 0xFF) << 8 | (b[23] & 0xFF) << 16 | (b[24] & 0xFF) << 24;
                return Optional.of(new CoverImage("image/webp", (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1));
            }
            case "VP8 ":
                return Optional.of(new CoverImage("image/webp",
                        ((b[26] & 0xFF) | (b[27] & 0xFF) << 8) & 0x3FFF,
                        ((b[28] & 0xFF) | (b[29] & 0xFF) << 8) & 0x3FFF));
            default:
                return Optional.empty();
        }
    }

    private static int be32(byte[] b, int o) {
        return (b[o] & 0xFF) << 24 | (b[o + 1] & 0xFF) << 16 | (b[o + 2] & 0xFF) << 8 | (b[o + 3] & 0xFF);
    }

    private static int le24(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16;
    }
}
