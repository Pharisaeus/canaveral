package pl.codewise.canaveral.mock.postgresql;

import com.google.common.annotations.VisibleForTesting;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;

class JdbcManager {

    private final JdbcTemplate jdbc;

    @VisibleForTesting
    JdbcManager(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    static JdbcManager create(String dbUrl, String user, String password) {
        return new JdbcManager(createJdbcTemplate(createDataSource(dbUrl, user, password)));
    }

    <T> List<T> select(String sql, RowMapper<T> rowMapper) {
        return jdbc.query(sql, rowMapper);
    }

    int update(String sql) {
        return jdbc.update(sql);
    }

    int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    private static DataSource createDataSource(String dbUrl, String user, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();

        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(dbUrl + "?tcpKeepAlive=true");
        dataSource.setUsername(user);
        dataSource.setPassword(password);

        return dataSource;
    }

    private static JdbcTemplate createJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
