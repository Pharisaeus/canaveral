package pl.codewise.canaveral.mock.redis;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import pl.codewise.canaveral.core.mock.MockConfig;
import pl.codewise.canaveral.core.mock.MockProvider;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import java.time.Duration;

import static com.google.common.base.Preconditions.checkArgument;

public class RedisMockProvider implements MockProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisMockProvider.class);

    private final RedisMockConfig mockConfig;
    private final String mockName;
    private int port;
    private String host;
    private GenericContainer server;

    private RedisMockProvider(RedisMockConfig mockConfig, String mockName) {
        this.mockConfig = mockConfig;
        this.mockName = mockName;
    }

    public static RedisMockConfig newConfig() {
        return new RedisMockConfig();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getEndpoint() {
        return "http://" + getHost() + ":" + this.getPort();
    }

    @Override
    public String getMockName() {
        return mockName;
    }

    @Override
    public void start(RunnerContext context) {
        log.info("Starting redis mock from {}.", mockConfig.image);

        server = new GenericContainer(mockConfig.image)
                .withExposedPorts(6379)
                .withStartupTimeout(Duration.ofSeconds(5));
        server.start();

        this.port = server.getFirstMappedPort();
        this.host = server.getContainerIpAddress();

        setSystemProperty(mockConfig.portProperty, Integer.toString(port));
        setSystemProperty(mockConfig.hostProperty, getHost());
    }

    private void setSystemProperty(String key, String value) {
        System.setProperty(key, value);
        log.info("Setting system property {} to {}.", key, System.getProperty(key));
    }

    @Override
    public void stop() {
        server.stop();
    }

    public RedisMockProvider reset() {
        if (server.isRunning()) {
            server.stop();
        }
        server.start();
        return this;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mockName", mockName)
                .toString();
    }

    public static class RedisMockConfig implements MockConfig<RedisMockProvider> {

        private String hostProperty;
        private String portProperty;
        private String image;

        private RedisMockConfig() {
        }

        @Override
        public RedisMockProvider build(String mockName) {
            return new RedisMockProvider(this, mockName);
        }

        public RedisMockConfig registerHostUnder(String key) {
            hostProperty = key;
            return this;
        }

        public RedisMockConfig registerPortUnder(String key) {
            portProperty = key;
            return this;
        }

        public RedisMockConfig overrideRedisVersion(String image) {
            checkArgument(image.startsWith("redis:"), "Redis docker image should be referenced as 'redis:version'.");
            this.image = image;
            return this;
        }
    }
}
