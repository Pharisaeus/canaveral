package pl.codewise.canaveral.core.mock;

import pl.codewise.canaveral.core.bean.inject.InjectMock;
import pl.codewise.canaveral.core.runtime.RunnerContext;

public interface MockProvider {

    int getPort();

    String getHost();

    String getEndpoint();

    String getMockName();

    void start(RunnerContext context) throws Exception;

    void stop() throws Exception;

    /**
     * Provides an object to be registered as a mock with {@link InjectMock} annotation. By default mock provider
     * instance is returned.
     */
    default Object providedMock() {
        return this;
    }
}
