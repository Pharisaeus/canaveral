package pl.codewise.canaveral.core.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("ResultOfMethodCallIgnored")
class RunnerTest {

    private Map<String, RunnerCache> cache;
    private Runner runner;

    @BeforeEach
    void setUp() {
        cache = new HashMap<>();
        runner = new Runner(cache);

        Mockito.reset(FullRunnerConfigurationProvider.applicationProviderMock);
        Mockito.reset(FullRunnerConfigurationProvider.testContextMock);
        Mockito.reset(MockedRunnerConfigurationProvider.configurationMock);
    }

    @AfterEach
    void tearDown() {
        cache.forEach((key, cache) -> runner.clearRunnerCache(cache));
    }

    @Test
    void shouldInitializeTestWithMinimalConfiguration() {

        // when
        runner.configureRunnerForTest(MinimalRunnerConfigurationTestClass.class);

        // then
        assertThat(cache).hasSize(1);
        assertThat(cache.values())
                .extracting(RunnerCache::isNotInitialized).containsOnly(false);
    }

    @Test
    void shouldNotReinitializeRunnerContextForSameConfiguartion() {
        runner.configureRunnerForTest(MinimalRunnerConfigurationTestClass.class);

        // when
        runner.configureRunnerForTest(MinimalRunnerConfigurationTestClass.class);

        // then
        assertThat(cache).hasSize(1);
    }

    @Test
    void shouldHandleErrorWhileInitializing() {
        Exception expectedException = new RuntimeException("test - fail on initialization");

        doThrow(expectedException)
                .when(MockedRunnerConfigurationProvider.configurationMock)
                .getSystemProperties();

        // when
        assertThatThrownBy(() -> runner.configureRunnerForTest(MockedRunnerConfigurationTestClass.class))
                .hasCause(expectedException)
                .isInstanceOf(RunnerInitializationException.class);
        assertThatThrownBy(() -> runner.configureRunnerForTest(MockedRunnerConfigurationTestClass.class))
                .hasCause(expectedException);

        // then
        RunnerCache runnerCache = cache.get(MockedRunnerConfigurationProvider.class.getCanonicalName());
        assertThat(runnerCache.isNotInitialized()).isTrue();
        assertThat(runnerCache.hasInitializationAlreadyFailed()).isTrue();
        assertThat(runnerCache.getInitializationCause()).isInstanceOf(RunnerInitializationException.class);
        assertThat(runnerCache.getInitializationCause()).hasCause(expectedException);

        verify(MockedRunnerConfigurationProvider.configurationMock, times(1)).getSystemProperties();
    }

    @Test
    void shouldInitializeTestsWithDifferentConfigurations() {
        setCanProceedForApplicationAndTestContext();

        runner.configureRunnerForTest(FullRunnerConfigurationTestClass.class);

        // when
        runner.configureRunnerForTest(MinimalRunnerConfigurationTestClass.class);

        // then
        assertThat(cache).hasSize(2);
        assertThat(cache.values())
                .extracting(RunnerCache::isNotInitialized).containsOnly(false);
    }

    @Test
    void shouldInitializeFullConfiguration() {
        setCanProceedForApplicationAndTestContext();

        // when
        runner.configureRunnerForTest(FullRunnerConfigurationTestClass.class);

        // then
        assertThat(cache).hasSize(1);
        RunnerCache runnerCache = cache.get(FullRunnerConfigurationProvider.class.getCanonicalName());
        assertThat(runnerCache.isNotInitialized()).isFalse();

        verify(FullRunnerConfigurationProvider.applicationProviderMock).start(runnerCache);
        verify(FullRunnerConfigurationProvider.applicationProviderMock).canProceed(runnerCache);
        verify(FullRunnerConfigurationProvider.testContextMock).initialize(runnerCache);
        verify(FullRunnerConfigurationProvider.testContextMock).canProceed(runnerCache);
    }

    @Test
    void shouldNotProceedWithFullConfigurationInitializationIfApplicationCannotStart() {
        when(FullRunnerConfigurationProvider.applicationProviderMock.canProceed(any()))
                .thenReturn(false);

        InitializationError expected = new InitializationError("Application is not ready yet. See configured progress" +
                " assertion.");

        // when
        assertThatThrownBy(() -> runner.configureRunnerForTest(FullRunnerConfigurationTestClass.class))
                // then
                .isInstanceOf(RunnerInitializationException.class)
                .hasCause(expected);
        assertThat(cache).hasSize(1);
        RunnerCache runnerCache = cache.get(FullRunnerConfigurationProvider.class.getCanonicalName());
        assertThat(runnerCache.isNotInitialized()).isTrue();

        verify(FullRunnerConfigurationProvider.applicationProviderMock).start(runnerCache);
        verify(FullRunnerConfigurationProvider.applicationProviderMock).canProceed(runnerCache);
        verify(FullRunnerConfigurationProvider.testContextMock, never()).initialize(any());
        verify(FullRunnerConfigurationProvider.testContextMock, never()).canProceed(any());
    }

    @Test
    void shouldNotProceedWithFullConfigurationInitializationIfTestContextCannotStart() {
        when(FullRunnerConfigurationProvider.applicationProviderMock.canProceed(any()))
                .thenReturn(true);
        when(FullRunnerConfigurationProvider.testContextMock.canProceed(any()))
                .thenReturn(false);

        InitializationError expected = new InitializationError("Test context is not ready yet. See configured " +
                "progress assertion.");
        // when
        assertThatThrownBy(() -> runner.configureRunnerForTest(FullRunnerConfigurationTestClass.class))
                // then
                .isInstanceOf(RunnerInitializationException.class)
                .hasCause(expected);

        // then
        assertThat(cache).hasSize(1);
        RunnerCache runnerCache = cache.get(FullRunnerConfigurationProvider.class.getCanonicalName());
        assertThat(runnerCache.isNotInitialized()).isTrue();

        verify(FullRunnerConfigurationProvider.applicationProviderMock).start(runnerCache);
        verify(FullRunnerConfigurationProvider.applicationProviderMock).canProceed(runnerCache);
        verify(FullRunnerConfigurationProvider.testContextMock).initialize(runnerCache);
        verify(FullRunnerConfigurationProvider.testContextMock).canProceed(runnerCache);
    }

    @Test
    void shouldCallAfterAllMockCreatedLifeCycleListenerInterfaceMethod() {
        // when
        runner.configureRunnerForTest(MinimalRunnerConfigurationTestClass.class);

        // then
        assertThat(cache).hasSize(1);
        RunnerCache runnerCache = cache.get(MinimalRunnerConfigurationProvider.class.getCanonicalName());
        DummyMockProvider mock = runnerCache.getMock(DummyMockProvider.class);

        assertThat(mock.cacheReference.get()).isSameAs(runnerCache);
        assertThat(mock.calledAfterAllMocksCreated.get()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldInjectRequestedMocks() {
        Clock clock = Clock.systemDefaultZone();
        ObjectMapper objectMapper = new ObjectMapper();
        Clock testClock = Clock.system(ZoneId.of("UTC+9"));

        setCanProceedForApplicationAndTestContext();

        when(FullRunnerConfigurationProvider.applicationProviderMock.findBeanOrThrow(eq(Clock.class), any()))
                .thenReturn(clock);
        when(FullRunnerConfigurationProvider.applicationProviderMock.findBeanOrThrow(eq(ObjectMapper.class), any()))
                .thenReturn(objectMapper);
        when(FullRunnerConfigurationProvider.testContextMock.findBeanOrThrow(eq(Clock.class), any())).thenReturn
                (testClock);

        TestInstanceHelper testInstanceHelper = runner.configureRunnerForTest(FullRunnerConfigurationTestClass.class);
        FullRunnerConfigurationTestClass testInstance = new FullRunnerConfigurationTestClass();

        // when
        testInstanceHelper.initializeTestInstance(testInstance);

        // then
        assertThat(cache).hasSize(1);
        RunnerCache runnerCache = cache.get(FullRunnerConfigurationProvider.class.getCanonicalName());
        assertThat(runnerCache.isNotInitialized()).isFalse();

        assertThat(testInstance.clock).isSameAs(clock);
        assertThat(testInstance.testClock).isSameAs(testClock);

        assertThat(testInstance.mapper).isSameAs(objectMapper);

        assertThat(testInstance.mockProvider).isNotSameAs(testInstance.otherDummyMockProvider);
        assertThat(testInstance.mockProvider).isNotNull();
        assertThat(testInstance.otherDummyMockProvider).isNotNull();

        ArgumentCaptor<Set<Annotation>> passedAnnotationCaptor = ArgumentCaptor.forClass(Set.class);
        verify(FullRunnerConfigurationProvider.applicationProviderMock).findBeanOrThrow(eq(ObjectMapper.class),
                passedAnnotationCaptor.capture());

        assertThat(passedAnnotationCaptor.getValue()).hasSize(2);
    }

    @Test
    void shouldReinitializeContextWhenRequired() {
        //given
        setCanProceedForApplicationAndTestContext();
        runner.configureRunnerForTest(FullRunnerConfigurationTestClass.class);

        assertThat(cache.values())
                .extracting(RunnerCache::isNotInitialized).containsOnly(false);

        //given
        RunnerCache minimalRunnerCache = cache.get(FullRunnerConfigurationProvider.class.getCanonicalName());

        DummyMockProvider firstDummyMock = minimalRunnerCache.getMock(DummyMockProvider.class);

        //ensure that stop method wasn't invoked
        assertThat(firstDummyMock.calledStop.get()).isFalse();

        //when
        runner.configureRunnerForTest(ReinitializedRunnerConfigurationTestClass.class);

        //then
        RunnerCache reinitializedRunnerCache = cache.get(FullRunnerConfigurationProvider.class.getCanonicalName());
        DummyMockProvider reinitializedDummyMockProvider = reinitializedRunnerCache.getMock(DummyMockProvider.class);

        //ensures dummyMockProvider was reinitialize and new isntance was created
        assertThat(reinitializedDummyMockProvider).isNotEqualTo(firstDummyMock);

        //cache should be cleaned and initialized with new RunnerConfiguration
        assertThat(cache).hasSize(1);

        //stop method has been called for the first the first mock
        assertThat(firstDummyMock.calledStop.get()).isTrue();
        assertThat(reinitializedDummyMockProvider.calledStop.get()).isFalse();
    }

    @Test
    void shouldRegisterAndInjectProvidedMock() {
        //given
        TestInstanceHelper testInstanceHelper =
                Runner.instance().configureRunnerForTest(MockProviderAdapterTestClass.class);

        MockProviderAdapterTestClass testInstance = new MockProviderAdapterTestClass();
        //when
        testInstanceHelper.initializeTestInstance(testInstance);

        //then
        assertThat(testInstance.dummyMockObject)
                .isEqualTo(MockProviderAdapterConfigurationProvider.dummyMockWrapper.providedMock());

        assertThat(testInstance.namedDummyMockObject)
                .isEqualTo(MockProviderAdapterConfigurationProvider.dummyMockWrapper.providedMock());
    }

    private void setCanProceedForApplicationAndTestContext() {
        when(FullRunnerConfigurationProvider.applicationProviderMock.canProceed(any()))
                .thenReturn(true);
        when(FullRunnerConfigurationProvider.testContextMock.canProceed(any()))
                .thenReturn(true);
    }
}