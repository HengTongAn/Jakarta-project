package com.hengtongan.computerstore.core.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditLogRepositoryTest {

    @Test
    void testEscapeLikeEscapesWildcardsAndEscapeChar() {
        // %, _ and backslash must all be escaped so user input is matched
        // literally (the SQL uses ESCAPE '\').
        assertEquals("100\\%\\_off\\\\sale", AuditLogRepository.escapeLike("100%_off\\sale"));
        assertEquals("\\%\\_", AuditLogRepository.escapeLike("%_"));
        assertEquals("plain", AuditLogRepository.escapeLike("plain"));
        assertEquals("", AuditLogRepository.escapeLike(""));
    }
}