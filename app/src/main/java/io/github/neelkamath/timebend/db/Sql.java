package io.github.neelkamath.timebend.db;

/**
 * {@link ActivityDao} is an {@code interface} that requires private constants. Since this is not
 * possible in {@code interface}s, this class provides the needed constants.
 */
final class Sql {
    /**
     * SQL for {@link ActivityDao#getAll()} and {@link ActivityDao#getAllLive()}.
     */
    static final String GET = "SELECT * FROM activities ORDER BY position";

    private Sql() {
    }
}
