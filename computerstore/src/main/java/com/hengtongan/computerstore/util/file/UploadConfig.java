package com.hengtongan.computerstore.util.file;

import java.io.File;

/**
 * Resolves the directory where uploaded product images are stored.
 * <p>
 * Images are kept OUTSIDE the exploded webapp so they survive redeploys and
 * clean rebuilds. The location can be tuned with the {@code COMPUTERSTORE_UPLOAD_DIR}
 * environment variable or the {@code computerstore.upload.dir} system property.
 * Defaults to {@code ~/.computerstore/uploads}.
 */
public final class UploadConfig {

    public static final String UPLOAD_DIR = "product-images";

    private UploadConfig() {
    }

    /**
     * Absolute path of the base upload directory (parent of the
     * {@code product-images} folder).
     */
    public static String getUploadBasePath() {
        String configured = System.getenv("COMPUTERSTORE_UPLOAD_DIR");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getProperty("computerstore.upload.dir");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            return new File(configured.trim()).getAbsolutePath();
        }
        String home = System.getProperty("user.home", ".");
        return new File(home, ".computerstore" + File.separator + "uploads").getAbsolutePath();
    }

    /**
     * Absolute path of the product-images directory, creating it when needed.
     */
    public static String getImageDirPath() {
        File dir = new File(getUploadBasePath(), UPLOAD_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create upload directory " + dir);
        }
        return dir.getAbsolutePath();
    }

    /**
     * Absolute path of the avatars directory, creating it when needed.
     */
    public static String getAvatarDirPath() {
        File dir = new File(getUploadBasePath(), "avatars");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create upload directory " + dir);
        }
        return dir.getAbsolutePath();
    }
}