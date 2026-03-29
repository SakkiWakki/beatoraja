package bms.player.beatoraja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.commons.dbutils.QueryRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import bms.player.beatoraja.song.FolderData;

class SQLiteDatabaseAccessorTest {

    @TempDir
    Path tempDir;

    @Test
    void validateCreatesTablesAddsColumnsAndInsertWorksWithAndWithoutConnection() throws Exception {
        Path dbPath = tempDir.resolve("accessor.db");

        TestAccessor initial = new TestAccessor(dbPath, false);
        initial.validate(initial.queryRunner());
            initial.insertStandalone(folder("alpha", "root/", "x"));

        TestAccessor migrated = new TestAccessor(dbPath, true);
        migrated.validate(migrated.queryRunner());

        try (Connection conn = migrated.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            migrated.insertWithConnection(conn, folder("beta", "root/", "x"));
            conn.commit();
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {
            try (ResultSet count = stmt.executeQuery("SELECT COUNT(*) FROM sample")) {
                assertTrue(count.next());
                assertEquals(2, count.getInt(1));
            }
            try (ResultSet columns = stmt.executeQuery("PRAGMA table_info('sample')")) {
                boolean sawAddedColumn = false;
                while (columns.next()) {
                    if ("banner".equals(columns.getString("name"))) {
                        sawAddedColumn = true;
                        break;
                    }
                }
                assertTrue(sawAddedColumn);
            }
        }
    }

    private static final class TestAccessor extends SQLiteDatabaseAccessor {
        private final SQLiteDataSource ds;
        private final QueryRunner qr;

        private TestAccessor(Path dbPath, boolean includeExtraColumn) throws ClassNotFoundException {
            super(includeExtraColumn
                    ? new Table("sample",
                            new Column("path", "TEXT", 1, 1),
                            new Column("title", "TEXT"),
                            new Column("parent", "TEXT"),
                            new Column("banner", "TEXT", 0, 0, "'x'"))
                    : new Table("sample",
                            new Column("path", "TEXT", 1, 1),
                            new Column("title", "TEXT"),
                            new Column("parent", "TEXT")));
            Class.forName("org.sqlite.JDBC");
            SQLiteConfig config = new SQLiteConfig();
            ds = new SQLiteDataSource(config);
            ds.setUrl("jdbc:sqlite:" + dbPath);
            qr = new QueryRunner(ds);
        }

        private QueryRunner queryRunner() {
            return qr;
        }

        private SQLiteDataSource dataSource() {
            return ds;
        }

        private void insertStandalone(FolderData entity) throws Exception {
            insert(qr, "sample", entity);
        }

        private void insertWithConnection(Connection conn, FolderData entity) throws Exception {
            insert(qr, conn, "sample", entity);
        }
    }

    private FolderData folder(String path, String parent, String banner) {
        FolderData folder = new FolderData();
        folder.setPath(path);
        folder.setParent(parent);
        folder.setTitle(path);
        folder.setBanner(banner);
        return folder;
    }
}
