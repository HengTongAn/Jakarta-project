package com.hengtongan.computerstore.util.sql;

/**
 * Small SQL string helpers shared by the persistence layer.
 */
public final class SqlUtil {

    private SqlUtil() {
    }

    /**
     * Builds the {@code ?,?,?} placeholder sequence for an {@code IN (...)}
     * clause with {@code count} parameters.
     *
     * @param count number of placeholders, must be non-negative
     * @return the comma-separated placeholder string
     */
    public static String placeholders(int count) {
        StringBuilder sb = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }
}