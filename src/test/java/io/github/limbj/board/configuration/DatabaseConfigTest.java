package io.github.limbj.board.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.apache.ibatis.type.JdbcType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void defaultFileProfileCreatesSchemaAndPreservesDataAcrossRestart() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("springboard.dataDir", temporary.newFolder("database").toString());
        try (HikariDataSource source = source(environment)) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO tbl_board (bno,title,content,writer) VALUES (seq_board.nextval,?,?,?)",
                    "persistent title", "content", "tester");
        }
        try (HikariDataSource restarted = source(environment)) {
            JdbcTemplate jdbc = new JdbcTemplate(restarted);
            assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT COUNT(*) FROM tbl_board", Integer.class));
            assertEquals("persistent title", jdbc.queryForObject("SELECT title FROM tbl_board", String.class));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM tbl_reply", Integer.class));
            assertEquals(Integer.valueOf(0), jdbc.queryForObject("SELECT COUNT(*) FROM tbl_attach", Integer.class));
        }
    }

    @Test
    public void attachmentFlagsUseCanonicalCharEncoding() throws Exception {
        try (HikariDataSource source = source(memory())) {
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO tbl_board (bno,title,content,writer) VALUES (1,'title','content','tester')");
            CharBooleanTypeHandler handler = new CharBooleanTypeHandler();
            try (Connection connection = source.getConnection()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO tbl_attach (uuid,uploadpath,filename,filetype,bno) VALUES (?,'2026/01/01','file.txt',?,1)")) {
                    insert.setString(1, "text");
                    handler.setNonNullParameter(insert, 2, false, JdbcType.CHAR);
                    insert.executeUpdate();
                    insert.setString(1, "image");
                    handler.setNonNullParameter(insert, 2, true, JdbcType.CHAR);
                    insert.executeUpdate();
                }
                try (PreparedStatement select = connection.prepareStatement("SELECT filetype FROM tbl_attach WHERE uuid=?")) {
                    select.setString(1, "text");
                    try (ResultSet result = select.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("0", result.getString(1));
                        assertFalse(handler.getNullableResult(result, 1));
                    }
                    select.setString(1, "image");
                    try (ResultSet result = select.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("1", result.getString(1));
                        assertTrue(handler.getNullableResult(result, "filetype"));
                    }
                }
            }
        }
    }

    @Test
    public void oracleProfileFailsBeforeConnectingWhenCredentialsAreMissing() throws Exception {
        assertRejected(new MockEnvironment().withProperty("springboard.db.profile", "oracle"));
    }

    @Test
    public void unexpectedProfileOrDatabaseModeIsRejected() throws Exception {
        assertRejected(new MockEnvironment().withProperty("springboard.db.profile", "unknown"));
        assertRejected(new MockEnvironment().withProperty("springboard.db.url", "jdbc:h2:mem:wrong;MODE=PostgreSQL"));
    }

    private static MockEnvironment memory() {
        return new MockEnvironment().withProperty("springboard.db.profile", "h2")
                .withProperty("springboard.db.url", "jdbc:h2:mem:config-" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1");
    }
    private static HikariDataSource source(MockEnvironment environment) throws Exception {
        return (HikariDataSource) new DatabaseConfig().dataSource(environment);
    }
    private static void assertRejected(MockEnvironment environment) throws Exception {
        try (HikariDataSource ignored = source(environment)) {
            fail("Expected invalid database configuration to be rejected");
        } catch (IllegalArgumentException expected) {
            // Configuration validation must not require a real Oracle connection.
        }
    }
}
