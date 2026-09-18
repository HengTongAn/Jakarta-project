package com.example.computer_store;

import com.example.computer_store.util.FileUploadUtil;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileUploadUtilTest {

    @TempDir
    Path uploadBase;

    @Test
    void savesAndDeletesValidatedAvatarImage() throws Exception {
        byte[] png = createPng();
        Part part = mock(Part.class);
        when(part.getSize()).thenReturn((long) png.length);
        when(part.getContentType()).thenReturn("image/png");
        when(part.getHeader("content-disposition"))
                .thenReturn("form-data; name=\"avatarFile\"; filename=\"photo.png\"");
        when(part.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(png));

        String avatarUrl = FileUploadUtil.saveUploadedFile(part, uploadBase.toString(), "avatars");
        Path savedFile = uploadBase.resolve(avatarUrl);

        assertTrue(avatarUrl.matches("avatars/[0-9a-f-]+\\.png"));
        assertTrue(Files.isRegularFile(savedFile));
        assertTrue(FileUploadUtil.deleteFile(avatarUrl, uploadBase.toString(), "avatars"));
        assertFalse(Files.exists(savedFile));
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? Color.BLUE.getRGB() : Color.WHITE.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
