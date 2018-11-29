package pl.codewise.canaveral.core.mock;

import pl.codewise.canaveral.core.runtime.RunnerContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class MockProviderAdapter<MockType>
        implements MockProvider {

    private final Map<String, Function<MockType, String>> propertiesSetters = new LinkedHashMap<>();
    private final String name;
    private MockType mock;
    private MockFactory<MockType> mockFactory;
    protected int port;

    public MockProviderAdapter(String name, MockType mock) {
        this(name, runnerContext -> mock);
    }

    public MockProviderAdapter(String name, MockFactory<MockType> mockFactory) {
        this.mockFactory = mockFactory;
        this.name = name;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getHost() {
        return "localhost";
    }

    @Override
    public String getEndpoint() {
        return "http://" + getHost() + ":" + getPort();
    }

    @Override
    public String getMockName() {
        return name;
    }

    @Override
    final public void start(RunnerContext context) throws Exception {
        mock = mockFactory.create(context);
        port = initialize(context);
        propertiesSetters.forEach((name, consumer) -> {
            String value = consumer.apply(mock);
            System.setProperty(name, value);
        });
    }

    @Override
    public MockType providedMock() {
        return mock;
    }

    @SuppressWarnings("WeakerAccess")
    public MockProviderAdapter<MockType> withProperty(String propertyName,
            Function<MockType, String> propertyValueProvider) {
        propertiesSetters.put(propertyName, propertyValueProvider);
        return this;
    }

    public MockProviderAdapter<MockType> withProperty(String propertyName, String propertyValue) {
        return withProperty(propertyName, mock -> propertyValue);
    }

    /**
     * Initializes mock and returns port number assigned to mock
     */
    protected abstract int initialize(RunnerContext context) throws Exception;
}