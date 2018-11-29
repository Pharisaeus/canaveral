package pl.codewise.canaveral.mock.postgresql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.testcontainers.shaded.com.google.common.collect.ImmutableList.of;

@ExtendWith(MockitoExtension.class)
class JdbcManagerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private RowMapper<String> rowMapper;

    @InjectMocks
    private JdbcManager jdbcManager;

    @Test
    void shouldExecuteSelect() {
        // given
        String query = "SELECT * FROM A";

        doReturn(of("A")).when(jdbcTemplate).query(eq(query), eq(rowMapper));

        // when
        List<String> result = jdbcManager.select(query, rowMapper);

        // then
        assertThat(result).isEqualTo(of("A"));
    }

    @Test
    void shouldExecuteUpdateWithQueryOnly() {
        // given
        String query = "INSERT INTO A (a) VALUES (1)";

        doReturn(2).when(jdbcTemplate).update(eq(query));

        // when
        int result = jdbcManager.update(query);

        // then
        assertThat(result).isEqualTo(2);
    }

    @Test
    void shouldExecuteUpdate() {
        // given
        String query = "INSERT INTO A (a, b) VALUES ( ?, ?)";

        doReturn(2).when(jdbcTemplate).update(eq(query), eq("arg1"), eq("arg2"));

        // when
        int result = jdbcManager.update(query, "arg1", "arg2");

        // then
        assertThat(result).isEqualTo(2);
    }
}