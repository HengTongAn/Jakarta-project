package com.example.computer_store.util.file;

import jakarta.servlet.http.Part;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Utility class for handling file uploads with enhanced security.
 * Manages product image storage and URL generation with content validation.
 */
public final class FileUploadUtil {

    private static final String UPLOAD_DIR = "product-images";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MIN_FILE_SIZE = 100; // 100 bytes (prevent tiny files)
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"));
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"));
    private static final int MAX_IMAGE_WIDTH = 4096;
    private static final int MAX_IMAGE_HEIGHT = 4096;

    private FileUploadUtil() {
    }

    /**
     * Gets the upload directory path for product images.
     */
    public static String getUploadDirectoryPath(String basePath) {
        return getUploadDirectoryPath(basePath, UPLOAD_DIR);
    }

    public static String getUploadDirectoryPath(String basePath, String subDir) {
        return basePath + File.separator + subDir;
    }

    /**
     * Ensures the product upload directory exists.
     */
    public static void ensureUploadDirectoryExists(String basePath) throws IOException {
        ensureUploadDirectoryExists(basePath, UPLOAD_DIR);
    }

    public static void ensureUploadDirectoryExists(String basePath, String subDir) throws IOException {
        Path uploadPath = Paths.get(getUploadDirectoryPath(basePath, subDir));
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
    }

    /**
     * Validates and saves an uploaded product image with enhanced security checks.
     * Returns the relative URL path for the saved file.
     */
    public static String saveUploadedFile(Part part, String basePath) throws IOException {
        return saveUploadedFile(part, basePath, UPLOAD_DIR);
    }

    public static String saveUploadedFile(Part part, String basePath, String subDir) throws IOException {
        if (part == null || part.getSize() == 0) {
            return null;
        }

        // Validate file size
        long fileSize = part.getSize();
        if (fileSize > MAX_FILE_SIZE) {
            throw new IOException("File size exceeds maximum limit of 5MB");
        }
        if (fileSize < MIN_FILE_SIZE) {
            throw new IOException("File size is too small (minimum 100 bytes)");
        }

        // Validate content type
        String contentType = normalizeMimeType(part.getContentType());
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IOException("Invalid content type. Only image files are allowed");
        }

        // Get file extension
        String originalFileName = getSubmittedFileName(part);
        String fileExtension = getFileExtension(originalFileName);

        // Validate file extension
        if (!isAllowedExtension(fileExtension)) {
            throw new IOException("Only JPG, JPEG, PNG, GIF, and WEBP files are allowed");
        }

        // Validate that the file is actually an image
        try (InputStream inputStream = part.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Invalid image file. The file is not a valid image");
            }

            // Validate image dimensions
            if (image.getWidth() > MAX_IMAGE_WIDTH || image.getHeight() > MAX_IMAGE_HEIGHT) {
                throw new IOException("Image dimensions too large (maximum 4096x4096 pixels)");
            }
        }

        // Sanitize filename to prevent directory traversal
        String uniqueFileName = generateSafeFileName(fileExtension);

        // Ensure upload directory exists
        ensureUploadDirectoryExists(basePath, subDir);

        // Save file
        Path uploadPath = Paths.get(getUploadDirectoryPath(basePath, subDir));
        Path filePath = uploadPath.resolve(uniqueFileName);
        
        // Use a temp file first, then rename to atomic operation
        Path tempFilePath = uploadPath.resolve("temp_" + uniqueFileName);
        try (InputStream inputStream = part.getInputStream()) {
            Files.copy(inputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Validate the saved file again (defense in depth)
        validateSavedFile(tempFilePath, fileExtension);
        
        // Prefer an atomic rename, but some filesystems do not support it.
        // Falling back keeps uploads working on shared folders and containers.
        try {
            Files.move(tempFilePath, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempFilePath, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Return relative URL path
        return subDir + "/" + uniqueFileName;
    }

    /**
     * Generates a safe filename using UUID.
     */
    private static String generateSafeFileName(String extension) {
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * Validates the saved file on disk (defense in depth).
     */
    private static void validateSavedFile(Path filePath, String expectedExtension) throws IOException {
        if (".webp".equals(expectedExtension)) {
            if (!isWebp(filePath)) {
                Files.deleteIfExists(filePath);
                throw new IOException("File content does not match the declared WEBP format");
            }
            return;
        }

        // Final image validation
        BufferedImage image = ImageIO.read(filePath.toFile());
        if (image == null) {
            Files.deleteIfExists(filePath);
            throw new IOException("Saved file is not a valid image");
        }
    }

    private static String normalizeMimeType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String normalized = contentType.toLowerCase().trim();
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private static boolean isWebp(Path filePath) throws IOException {
        try (InputStream stream = Files.newInputStream(filePath)) {
            byte[] header = stream.readNBytes(12);
            return header.length == 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
        }
    }

    /**
     * Deletes a file from the upload directory.
     */
    public static boolean deleteFile(String fileUrl, String basePath) {
        return deleteFile(fileUrl, basePath, UPLOAD_DIR);
    }

    public static boolean deleteFile(String fileUrl, String basePath, String subDir) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return false;
        }

        try {
            // Sanitize the file path to prevent directory traversal
            String sanitizedUrl = fileUrl.replace("..", "").replace("\\", "/");
            Path filePath = Paths.get(basePath, sanitizedUrl);
            
            // Ensure the file is within the upload directory
            Path uploadPath = Paths.get(getUploadDirectoryPath(basePath, subDir)).toAbsolutePath().normalize();
            Path resolvedPath = filePath.toAbsolutePath().normalize();
            
            if (!resolvedPath.startsWith(uploadPath)) {
                return false; // Attempted directory traversal
            }
            
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets the submitted file name from a Part object.
     */
    private static String getSubmittedFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition == null) {
            return "";
        }
        
        String[] elements = contentDisposition.split(";");
        for (String element : elements) {
            if (element.trim().startsWith("filename")) {
                String filename = element.substring(element.indexOf('=') + 1).trim().replace("\"", "");
                // Sanitize filename to prevent directory traversal
                return filename.replace("..", "").replace("\\", "").replace("/", "");
            }
        }
        return "";
    }

    /**
     * Gets the file extension from a filename.
     */
    private static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex).toLowerCase();
    }

    /**
     * Checks if the file extension is allowed.
     */
    private static boolean isAllowedExtension(String extension) {
        return ALLOWED_EXTENSIONS.contains(extension.toLowerCase());
    }

    /**
     * Gets the full URL for a file (for use in JSP pages).
     */
    public static String getFileUrl(String relativePath, String contextPath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        // Sanitize the path
        String sanitizedPath = relativePath.replace("..", "").replace("\\", "/");
        return contextPath + "/" + sanitizedPath;
    }
}
