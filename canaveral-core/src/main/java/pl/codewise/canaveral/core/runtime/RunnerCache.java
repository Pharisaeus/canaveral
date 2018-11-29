package pl.codewise.canaveral.core.runtime;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.codewise.canaveral.core.ApplicationProvider;
import pl.codewise.canaveral.core.mock.MockProvider;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

class RunnerCache implements RunnerContext {

    private static final Logger log = LoggerFactory.getLogger(RunnerCache.class);

    private final Map<String, Object> objects;
    private final Set<MockProvider> mockProviders;
    private final Set<LifeCycleListener> listeners;

    private boolean isInitialized = false;
    private RunnerInitializationException initializationException;

    private String providerName;
    private RunnerConfiguration configuration;

    RunnerCache(String canonicalName, RunnerConfiguration configuration) {
        this.providerName = canonicalName;
        this.configuration = configuration;
        this.objects = new HashMap<>();
        this.mockProviders = new HashSet<>();
        this.listeners = new HashSet<>();
    }

    @Override
    public RunnerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean hasApplicationProvider() {
        return configuration.getApplicationProvider() != null;
    }

    @Override
    public ApplicationProvider getApplicationProvider() {
        return configuration.getApplicationProvider();
    }

    @Override
    public boolean hasTestConfigurationProvider() {
        return configuration.getTestContextProvider() != null;
    }

    @Override
    public Object getApplicationBean(Class<?> beanType, Set<Annotation> knownAnnotations) {
        try {
            if (hasApplicationProvider()) {
                return configuration.getApplicationProvider().findBeanOrThrow(beanType, knownAnnotations);
            }

            throw new IllegalStateException("No provider (application or test context) to get beans from.");
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not find bean identified by " + beanType.getCanonicalName(), e);
        }
    }

    @Override
    public Object getTestBean(Class<?> beanType, Set<Annotation> knownAnnotations) {
        try {
            if (hasTestConfigurationProvider()) {
                return configuration.getTestContextProvider().findBeanOrThrow(beanType, knownAnnotations);
            }

            throw new IllegalStateException("No provider (application or test context) to get beans from.");
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not find bean identified by " + beanType.getCanonicalName(), e);
        }
    }

    @Override
    public boolean isNotInitialized() {
        return !isInitialized;
    }

    @Override
    public boolean hasInitializationAlreadyFailed() {
        return initializationException != null;
    }

    @Override
    public Object getMock(String ref) {
        Object o = objects.get(ref);
        Preconditions.checkArgument(Objects.nonNull(o), ref + " was not found. Cannot inject this mock!");
        return o;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMock(Class<?> mockType) {
        return (T) objects.values().stream()
                .filter(object -> mockType.isAssignableFrom(object.getClass()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find any mock of type " + mockType));
    }

    @Override
    public Stream<MockProvider> getMocks() {
        return mockProviders.stream();
    }

    void putMockProvider(MockProvider mockProvider) {
        mockProviders.add(mockProvider);
    }

    void putMockObject(String ref, Object mock) {
        objects.put(ref, mock);
    }


    @Override
    public void register(LifeCycleListener listener) {
        listeners.add(listener);
    }

    void callAllMocksCreated() {
        sortListenersByPriority()
                .forEach(l -> l.afterAllMocksCreated(this));
    }

    private List<LifeCycleListener> sortListenersByPriority() {
        ArrayList<LifeCycleListener> sortedListeners = new ArrayList<>(listeners);
        sortedListeners.sort(Comparator.comparing(LifeCycleListener::getPriority));

        return ImmutableList.copyOf(sortedListeners);
    }

    void callStopMocks() {
        mockProviders.forEach(l -> {
            try {
                l.stop();
            } catch (Exception e) {
                log.error("Could not stop mock {}.", l);
            }
        });
    }

    String getProviderName() {
        return providerName;
    }

    void setInitialized() {
        isInitialized = true;
    }

    void setCleaned() {
        isInitialized = false;
    }

    RunnerInitializationException getInitializationCause() {
        return initializationException;
    }

    void setInitializationFailedCause(Exception e) {
        this.initializationException = new RunnerInitializationException(e);
    }
}
