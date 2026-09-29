package com.hengtongan.computerstore.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** Supplies database connections to services that manage transactions. */
@FunctionalInterface
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}
