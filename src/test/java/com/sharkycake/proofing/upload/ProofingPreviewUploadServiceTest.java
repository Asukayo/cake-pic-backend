package com.sharkycake.proofing.upload;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.sharkycake.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProofingPreviewUploadServiceTest {

    private final ProofingPreviewUploadService service =
            new ProofingPreviewUploadService(null, null, null);

    @Test
    void rotatesExifImageAndRemovesOriginalMetadata() throws Exception {
        BufferedImage source = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 60, 60);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(60, 0, 60, 60);
        graphics.dispose();
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", original);

        MockMultipartFile upload = new MockMultipartFile("file", "camera.jpg", "image/jpeg",
                withExifOrientation(original.toByteArray(), 6));
        PreparedPreview result = service.preparePreview(upload);
        try {
            BufferedImage preview = ImageIO.read(result.getFile());
            assertEquals(60, preview.getWidth());
            assertEquals(120, preview.getHeight());
            assertTrue(((preview.getRGB(30, 20) >> 16) & 0xff)
                    > (preview.getRGB(30, 20) & 0xff));
            assertTrue((preview.getRGB(30, 100) & 0xff)
                    > ((preview.getRGB(30, 100) >> 16) & 0xff));
            assertNull(ImageMetadataReader.readMetadata(result.getFile())
                    .getFirstDirectoryOfType(ExifIFD0Directory.class));
            assertEquals("image/jpeg", result.getContentType());
            assertEquals(64, result.getSha256().length());
            assertTrue(result.getSizeBytes() <= 2L * 1024 * 1024);
        } finally {
            Files.deleteIfExists(result.getFile().toPath());
        }
    }

    @Test
    void flattensTransparentPngOntoWhite() throws Exception {
        BufferedImage transparent = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(transparent, "png", original);
        PreparedPreview result = service.preparePreview(new MockMultipartFile(
                "file", "transparent.png", "image/png", original.toByteArray()));
        try {
            BufferedImage preview = ImageIO.read(result.getFile());
            Color pixel = new Color(preview.getRGB(10, 10));
            assertTrue(pixel.getRed() > 240 && pixel.getGreen() > 240 && pixel.getBlue() > 240);
        } finally {
            Files.deleteIfExists(result.getFile().toPath());
        }
    }

    @Test
    void rejectsNonImageEvenIfNameAndContentTypeClaimJpeg() {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "not an image".getBytes());
        assertThrows(BusinessException.class, () -> service.preparePreview(upload));
    }

    private static byte[] withExifOrientation(byte[] jpeg, int orientation) throws Exception {
        // JPEG SOI 后插入一个只含 Orientation 标签的最小 EXIF APP1 段。
        byte[] exif = {
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1, 1, 18, 0, 3, 0, 0, 0, 1,
                0, (byte) orientation, 0, 0, 0, 0, 0, 0
        };
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(jpeg, 0, 2);
        result.write(new byte[]{(byte) 0xff, (byte) 0xe1, 0, 34});
        result.write(exif);
        result.write(jpeg, 2, jpeg.length - 2);
        return result.toByteArray();
    }
}
