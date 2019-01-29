package pl.codewise.canaveral.mock.sqs;

import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.codewise.canaveral.core.mock.MockConfig;
import pl.codewise.canaveral.core.mock.MockProvider;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.System.getProperty;

public class SqsMockProvider implements MockProvider {

    private static final Logger log = LoggerFactory.getLogger(SqsMockProvider.class);

    private final SqsMockConfig mockConfig;
    private final String mockName;
    private int port = 0;
    private SqsMockServer sqsMockServer;

    private SqsMockProvider(SqsMockConfig mockConfig, String mockName) {
        this.mockConfig = mockConfig;
        this.mockName = mockName;
    }

    public static SqsMockConfig newConfig() {
        return new SqsMockConfig();
    }

    @Override
    public int getPort() {
        checkArgument(this.port != 0, "Mock is not started yet!");
        return this.port;
    }

    @Override
    public String getHost() {
        return mockConfig.host;
    }

    @Override
    public String getEndpoint() {
        return "http://" + this.getHost() + ":" + this.getPort();
    }

    @Override
    public String getMockName() {
        return mockName;
    }

    @Override
    public void start(RunnerContext context) {
        this.port = context.getFreePort();
        this.sqsMockServer = new SqsMockServer(port);

        sqsMockServer.start();

        mockConfig.portProperties.forEach(property -> {
            System.setProperty(property, Integer.toString(getPort()));
            log.info("Setting system property {} to {}.", property, getProperty(property));
        });
        mockConfig.endpointProperties.forEach(property -> {
            System.setProperty(property, getEndpoint());
            log.info("Setting system property {} to {}.", property, getProperty(property));
        });
        mockConfig.queueConfigs.forEach(queueConfig -> {
            SqsClient sqsClient = new SqsClient(port);
            SqsQueueClient queueClient = sqsClient.createQueue(queueConfig.getQueueName());
            System.setProperty(queueConfig.getProperty(), queueClient.getQueueUrl());
            log.info("Setting system property {} to {}.", queueConfig.property, getProperty(queueConfig.property));
        });
    }

    @Override
    public void stop() {
        sqsMockServer.stop();
    }

    public SqsMockServer getSqsMockServer() {
        return sqsMockServer;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mockName", mockName)
                .toString();
    }

    public static class SqsMockConfig implements MockConfig<SqsMockProvider> {

        private String host = HOST;
        private Set<String> portProperties = new HashSet<>();
        private Set<String> endpointProperties = new HashSet<>();
        private List<QueueConfig> queueConfigs = new ArrayList<>();

        private SqsMockConfig() {
        }

        @Override
        public SqsMockProvider build(String mockName) {
            return new SqsMockProvider(this, mockName);
        }

        public SqsMockConfig registerPortUnder(String property) {
            checkArgument(!isNullOrEmpty(property));
            portProperties.add(property);
            return this;
        }

        public SqsMockConfig registerEndpointUnder(String property) {
            checkArgument(!isNullOrEmpty(property));
            endpointProperties.add(property);
            return this;
        }

        public SqsMockConfig createQueue(QueueConfig queueConfig) {
            checkNotNull(queueConfig);
            queueConfigs.add(queueConfig);
            return this;
        }
    }

    public static class QueueConfig {

        private final String queueName;
        private final String property;

        private QueueConfig(String queueName, String property) {
            this.queueName = queueName;
            this.property = property;
        }

        public String getQueueName() {
            return queueName;
        }

        public String getProperty() {
            return property;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private String queueName;
            private String property;

            public Builder withQueueName(String queueName) {
                this.queueName = queueName;
                return this;
            }

            public Builder withProperty(String property) {
                this.property = property;
                return this;
            }

            public QueueConfig build() {
                checkNotNull(queueName);
                checkNotNull(property);
                return new QueueConfig(queueName, property);
            }
        }
    }
}

