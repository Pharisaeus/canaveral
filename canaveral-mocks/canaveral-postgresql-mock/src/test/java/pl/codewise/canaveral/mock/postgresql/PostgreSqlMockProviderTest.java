package pl.codewise.canaveral.mock.postgresql;



import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class PostgreSqlMockProviderTest {

    private PostgreSqlMockProvider provider;

    @Mock
    private RunnerContext runnerContext;

    @AfterEach
    public void tearDown() {
        if (provider != null) {
            provider.stop();
        }
    }

    // Requires running docker daemon. It doesn't work on CI. Can be used to check changes locally.
    @Disabled
    @Test
    public void shouldStartContainer() {
        // given
        provider = PostgreSqlMockProvider.newConfig()
                .registerEndpointUnderProperty("test.endpoint")
                .registerUsernameUnder("test.user")
                .registerPasswordUnder("test.pass")
                .withDataBaseName("a-test-db")
                .build("psql-mock");

        // when
        provider.start(runnerContext);

        // then
        assertThat(provider.getEndpoint())
                .isEqualTo("jdbc:postgresql://localhost:" + provider.getPort() + "/a-test-db");
        assertThat(System.getProperty("test.endpoint")).isEqualTo(provider.getEndpoint());

        assertThat(provider.getUser())
                .isNotNull()
                .isNotBlank();
        assertThat(System.getProperty("test.user")).isEqualTo(provider.getUser());

        assertThat(provider.getPassword())
                .isNotNull()
                .isNotBlank();
        assertThat(System.getProperty("test.pass")).isEqualTo(provider.getPassword());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(createDataSource(provider));
        jdbcTemplate.execute("CREATE TABLE TEST_TABLE(id uuid PRIMARY KEY)");

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT  INTO TEST_TABLE (id) VALUES (?::uuid)", id);
        List<UUID> ids = jdbcTemplate.query("SELECT * FROM TEST_TABLE", (rs, i) -> UUID.fromString(rs.getString(1)));

        assertThat(ids).hasSize(1)
                .containsOnly(id);

        jdbcTemplate.execute("DROP TABLE TEST_TABLE");
    }

    private DataSource createDataSource(PostgreSqlMockProvider provider) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(provider.getEndpoint() + "?tcpKeepAlive=true");
        dataSource.setUsername(provider.getUser());
        dataSource.setPassword(provider.getPassword());
        return dataSource;
    }
}
