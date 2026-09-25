package com.example.computer_store.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Locks the {@code db/migrations/} layout to
 * {@link DatabaseMigrationRunner#discoverMigrations()}: every registered name
 * must resolve as a classpath resource and match the SQL files on disk exactly
 * (no file left unregistered, no name registered without a file). This makes
 * "add a migration" a self-checking step and protects the plain-filename keys
 * that existing databases' {@code schema_migrations} tables rely on.
 */
class DatabaseMigrationRunnerTest {

    private static final Path MIGRATIONS_DIR =
            Paths.get("src", "main", "resources", "db", "migrations");

    @Test
    void everyRegisteredMigrationResolvesAsAClasspathResource() throws IOException {
        for (String name : DatabaseMigrationRunner.discoverMigrations()) {
            String resource = "db/migrations/" + name;
            try (InputStream in = DatabaseMigrationRunner.class.getClassLoader()
                    .getResourceAsStream(resource)) {
                assertNotNull(in, "missing classpath resource: " + resource);
            }
        }
    }

    @Test
    void registeredNamesMatchTheFilesOnDisk() throws IOException {
        Set<String> onDisk;
        try (Stream<Path> paths = Files.list(MIGRATIONS_DIR)) {
            onDisk = paths.map(p -> p.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        Set<String> registered = new TreeSet<>(DatabaseMigrationRunner.discoverMigrations());

        Set<String> missingOnDisk = new TreeSet<>(registered);
        missingOnDisk.removeAll(onDisk);
        Set<String> unregistered = new TreeSet<>(onDisk);
        unregistered.removeAll(registered);

        assertTrue(missingOnDisk.isEmpty(),
                "registered but missing on disk: " + missingOnDisk);
        assertTrue(unregistered.isEmpty(),
                "on disk but not registered (add to discoverMigrations()): " + unregistered);
    }

    @Test
    void namesFollowTheMigrationConvention() throws IOException {
        List<String> names = DatabaseMigrationRunner.discoverMigrations();
        assertTrue(names.stream().allMatch(n -> n.startsWith("migration_") && n.endsWith(".sql")),
                "non-conforming name(s): " + names);
        assertEquals(names.stream().distinct().count(), names.size(),
                "duplicate names in discoverMigrations()");
    }
}