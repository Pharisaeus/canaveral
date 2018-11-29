package pl.codewise.canaveral.core.runtime;

import pl.codewise.canaveral.core.ApplicationProvider;
import pl.codewise.canaveral.core.mock.MockProvider;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Stream;

public interface RunnerContext {

    RunnerConfiguration getConfiguration();

    boolean isNotInitialized();

    boolean hasInitializationAlreadyFailed();

    boolean hasApplicationProvider();

    ApplicationProvider getApplicationProvider();

    boolean hasTestConfigurationProvider();

    /**
     * It is sometimes necessary to access bean from application and share it with test. ex object mapper.
     * Please use it wisely so your test stay decoupled from application logic.
     *
     * @return bean defined by application DI system.
     *
     * @throws IllegalArgumentException if cannot find bean by its class.
     */
    Object getApplicationBean(Class<?> beanType, Set<Annotation> knownAnnotations);

    /**
     * It is sometimes necessary to access bean from application and share it with test. ex object mapper.
     * Please use it wisely so your test stay decoupled from application logic.
     *
     * @return bean defined by application DI system.
     *
     * @throws IllegalArgumentException if cannot find bean by its class.
     */
    Object getTestBean(Class<?> beanType, Set<Annotation> qualifier);

    /**
     * @param ref of the mock. The one provided in configuration
     *
     * @return mock provider
     *
     * @throws IllegalArgumentException if cannot find mock by provided ref.
     */
    Object getMock(String ref);

    /**
     * @param mockType of the mock.
     *
     * @return mock provider
     *
     * @throws IllegalArgumentException if cannot find mock by provided class.
     */
    <T> T getMock(Class<?> mockType);

    Stream<MockProvider> getMocks();

    default int getFreePort() {
        return FreePorts.findFreePort();
    }

    void register(LifeCycleListener listener);
}
