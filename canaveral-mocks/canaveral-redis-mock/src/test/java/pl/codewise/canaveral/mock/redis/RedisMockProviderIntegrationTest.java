package pl.codewise.canaveral.mock.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pl.codewise.canaveral.core.runtime.RunnerContext;
import redis.clients.jedis.Jedis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RedisMockProviderIntegrationTest {

    @Mock
    private RunnerContext runnerContext;

    private RedisMockProvider redisMockProvider;
    private Jedis testClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(runnerContext.getFreePort()).thenCallRealMethod();

        redisMockProvider = RedisMockProvider.newConfig()
                .withRedisDockerImage("redis:3.0.6")
                .registerHostUnder("my-property.redis.host")
                .registerPortUnder("my-property.redis.port")
                .build("redis-mock");
        redisMockProvider.start(runnerContext);

        assertThat(System.getProperty("my-property.redis.host")).isEqualTo(redisMockProvider.getHost());
        assertThat(System.getProperty("my-property.redis.port")).isEqualTo(redisMockProvider.getPort() + "");

        testClient = new Jedis(redisMockProvider.getHost(), redisMockProvider.getPort(), false);
    }

    @Test
    void shouldReadPersistedData() {
        // when
        testClient.sadd("aKey", "Value01", "Value02");

        // then
        assertThat(testClient.smembers("aKey")).containsOnly("Value01", "Value02");

        // when
        testClient.srem("aKey", "Value02");
        testClient.sadd("aKey", "Value03");

        // then
        assertThat(testClient.smembers("aKey")).containsOnly("Value01", "Value03");
    }
}