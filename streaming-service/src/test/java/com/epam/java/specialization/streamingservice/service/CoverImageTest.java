package com.epam.java.specialization.streamingservice.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverImageTest {

    private static byte[] image(String format, int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void readsPngSizeAndAcceptsSquareWithinLimits() throws Exception {
        CoverImage cover = CoverImage.inspect(image("png", 600, 600)).orElseThrow();
        assertEquals("image/png", cover.contentType());
        assertEquals(600, cover.width());
        assertTrue(cover.isSquareWithinLimits());
    }

    @Test
    void readsJpegAndRejectsNonSquareOrTooSmall() throws Exception {
        CoverImage wide = CoverImage.inspect(image("jpg", 800, 600)).orElseThrow();
        assertEquals("image/jpeg", wide.contentType());
        assertFalse(wide.isSquareWithinLimits());
        assertFalse(CoverImage.inspect(image("png", 400, 400)).orElseThrow().isSquareWithinLimits());
    }

    @Test
    void readsLosslessWebpHeader() {
        byte[] webp = new byte[40];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, webp, 8, 4);
        System.arraycopy("VP8L".getBytes(), 0, webp, 12, 4);
        int bits = 699 | (699 << 14); // 700 x 700
        webp[21] = (byte) bits;
        webp[22] = (byte) (bits >> 8);
        webp[23] = (byte) (bits >> 16);
        webp[24] = (byte) (bits >> 24);
        CoverImage cover = CoverImage.inspect(webp).orElseThrow();
        assertEquals("image/webp", cover.contentType());
        assertEquals(700, cover.width());
        assertTrue(cover.isSquareWithinLimits());
    }

    @Test
    void rejectsOtherFiles() {
        assertTrue(CoverImage.inspect("this is plain text, definitely not an image".getBytes()).isEmpty());
    }
}
