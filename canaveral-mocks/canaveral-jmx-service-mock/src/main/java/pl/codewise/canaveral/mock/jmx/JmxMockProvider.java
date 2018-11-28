package pl.codewise.canaveral.mock.jmx;

import com.google.common.base.Strings;
import pl.codewise.canaveral.core.mock.MockProvider;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import static com.google.common.base.Preconditions.checkArgument;

public class JmxMockProvider implements MockProvider {

    public static final String JMX_ENDPOINT_FORMAT = "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi";
    private final JmxMockConfig jmxMockConfig;
    private final String mockName;

    private int port = 0;
    private JmxMock jmxMockInstance;

    JmxMockProvider(JmxMockConfig jmxMockConfig, String mockName) {
        this.jmxMockConfig = jmxMockConfig;
        this.mockName = mockName;
    }

    public static JmxMockConfig newConfig() {
        return new JmxMockConfig();
    }

    @Override
    public int getPort() {
        checkArgument(port != 0, "Mock is not started yet!");
        return port;
    }

    @Override
    public String getHost() {
        return jmxMockConfig.getHost();
    }

    @Override
    public String getEndpoint() {
        return String.format(JMX_ENDPOINT_FORMAT, getHost(), getPort());
    }

    @Override
    public String getMockName() {
        return mockName;
    }

    @Override
    public void start(RunnerContext context) throws Exception {
        this.port = context.getFreePort();
        this.jmxMockInstance = new JmxMock(jmxMockConfig.getRules());
        jmxMockInstance.start(getEndpoint(), getPort());
        String jmxPortProperty = jmxMockConfig.getJmxPortProperty();
        if (!Strings.isNullOrEmpty(jmxPortProperty)) {
            System.setProperty(jmxPortProperty, Integer.toString(port));
        }
    }

    @Override
    public void stop() throws Exception {
        jmxMockInstance.stop();
    }
}
