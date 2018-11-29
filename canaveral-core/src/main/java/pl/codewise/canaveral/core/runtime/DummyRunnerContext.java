package pl.codewise.canaveral.core.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.codewise.canaveral.core.ApplicationProvider;
import pl.codewise.canaveral.core.mock.MockProvider;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.stream.Stream;

public class DummyRunnerContext implements RunnerContext {

    private static final Logger logger = LoggerFactory.getLogger(DummyRunnerContext.class);

    @Override
    public RunnerConfiguration getConfiguration() {
        throw new RuntimeException("this implementation is for testing purposes.");
    }

    @Override
    public boolean isNotInitialized() {
        return false;
    }

    @Override
    public boolean hasInitializationAlreadyFailed() {
        return false;
    }

    @Override
    public boolean hasApplicationProvider() {
        return false;
    }

    @Override
    public ApplicationProvider getApplicationProvider() {
        return null;
    }

    @Override
    public boolean hasTestConfigurationProvider() {
        return false;
    }

    @Override
    public Object getApplicationBean(Class<?> beanType, Set<Annotation> knownAnnotations) {
        return null;
    }

    @Override
    public Object getTestBean(Class<?> beanType, Set<Annotation> qualifier) {
        return null;
    }

    @Override
    public Object getMock(String ref) {
        return null;
    }

    @Override
    public <T> T getMock(Class<?> mockType) {
        return null;
    }

    @Override
    public Stream<MockProvider> getMocks() {
        return null;
    }

    @Override
    public void register(LifeCycleListener listener) {
        throw new RuntimeException("this implementation is for testing purposes.");
    }
}
    
