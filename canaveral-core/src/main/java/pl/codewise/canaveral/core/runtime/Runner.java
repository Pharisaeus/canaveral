package pl.codewise.canaveral.core.runtime;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.codewise.canaveral.core.ApplicationProvider;
import pl.codewise.canaveral.core.TestContextProvider;
import pl.codewise.canaveral.core.util.PropertyHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);
    private static final Runner runner = new Runner();
    private static final Object MONITOR = new Object();

    private final Map<String, RunnerCache> CACHE_BY_PROVIDER;

    @VisibleForTesting
    Runner(Map<String, RunnerCache> cache) {
        CACHE_BY_PROVIDER = cache;
    }

    private Runner() {
        this(new HashMap<>());
    }

    public static Runner instance() {
        return runner;
    }

    public TestInstanceHelper configureRunnerForTest(Class<?> testClass) {
        log.debug("Configuring runner from [" + testClass + "]");
        synchronized (MONITOR) {
            ConfigureRunnerWith annotation = getConfigurationAnnotation(testClass);
            if (annotation.reinitialize()) {
                CACHE_BY_PROVIDER.values().forEach(this::clearRunnerCache);

                CACHE_BY_PROVIDER.clear();
            }

            RunnerConfigurationProvider provider = instantiateRunnerConfigurationProvider(testClass, annotation);

            String canonicalName = provider.getClass().getCanonicalName();

            RunnerCache runnerCache = CACHE_BY_PROVIDER.computeIfAbsent(
                    canonicalName,
                    name -> new RunnerCache(canonicalName, provider.configure())
            );

            if (runnerCache.isNotInitialized()) {
                if (runnerCache.hasInitializationAlreadyFailed()) {
                    decorateWarn("Initialization skipped due to errors!");
                    throw runnerCache.getInitializationCause();
                }

                try {
                    initializeCacheSafe(runnerCache);
                    runnerCache.setInitialized();
                } catch (Exception e) {
                    runnerCache.setInitializationFailedCause(e);
                    decorateError("Houston, we have a problem ..", e);
                    throw runnerCache.getInitializationCause();
                }
            }
            return new TestInstanceHelper(runnerCache);
        }
    }

    void clearRunnerCache(RunnerCache runnerCache) {
        if (runnerCache.isNotInitialized()) {
            return;
        }
        synchronized (MONITOR) {
            decorateSection("Process is shutting down!");
            decorateSimple("Clearing context for {}.", runnerCache.getProviderName());

            try {
                RunnerConfiguration configuration = runnerCache.getConfiguration();
                if (runnerCache.hasApplicationProvider()) {
                    decorateSimple("Cleaning application.");
                    configuration.getApplicationProvider().clean();
                }

                if (runnerCache.hasTestConfigurationProvider()) {
                    decorateSimple("Cleaning test context.");
                    configuration.getTestContextProvider().clean();
                }

                decorateSimple("Closing remaining mocks now!");
                runnerCache.callStopMocks();

                decorateSimple("Clearing system properties");
                PropertyHelper.clearProperties(configuration.getSystemProperties().keySet());
            } catch (Exception e) {
                log.error("Could not clean.", e);
            }
            runnerCache.setCleaned();
        }
    }

    private RunnerConfigurationProvider getConfiguration(Class<?> klass) {
        ConfigureRunnerWith annotation = getConfigurationAnnotation(klass);
        return instantiateRunnerConfigurationProvider(klass, annotation);
        }

    private RunnerConfigurationProvider instantiateRunnerConfigurationProvider(Class<?> klass,
            ConfigureRunnerWith annotation) {
        Class<? extends RunnerConfigurationProvider> configurationClass = annotation.configuration();
        try {
            return configurationClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate configuration provider of class " +
                    configurationClass + " defined for " + klass, e);
        }
    }

    private ConfigureRunnerWith getConfigurationAnnotation(Class<?> klass) {
        ConfigureRunnerWith annotation = klass.getAnnotation(ConfigureRunnerWith.class);
        if (annotation == null) {
            throw new IllegalStateException("Could not find a ConfigureRunnerWith annotation on " + klass);
        }
        return annotation;
    }

    private void initializeCacheSafe(RunnerCache runnerCache) {
        printBanner();

        RunnerConfiguration configuration = runnerCache.getConfiguration();

        decorateSection("Setting system properties");
        PropertyHelper.setProperties(configuration.getSystemProperties());
        PropertyHelper.setProperties(configuration.getRandomPortsProperty(), runnerCache.getFreePort());

        registerShutdownHook(runnerCache);

        decorateSection("Starting mocks");
        initializeMocks(runnerCache, configuration);

        if (runnerCache.hasApplicationProvider()) {
            decorateSection("Starting application");
            initializeApplication(runnerCache, configuration.getApplicationProvider());
        } else {
            log.trace("Application provider was not configured");
        }

        if (runnerCache.hasTestConfigurationProvider()) {
            decorateSection("Configuring test.");
            initializeTestContext(runnerCache, configuration.getTestContextProvider());
        } else {
            log.trace("Test configuration provider was not configured");
        }

        decorateSection("Test Runner configured. Good luck!");
    }

    private void registerShutdownHook(RunnerCache runnerCache) {
        Thread hook = new Thread(() -> clearRunnerCache(runnerCache));
        Runtime.getRuntime().addShutdownHook(hook);
    }

    private void initializeMocks(RunnerCache cache, RunnerConfiguration configuration)
            throws RunnerInitializationException {
        RunnerConfiguration.MockProvidersConfiguration mockProvidersConfiguration = configuration
                .getMockProvidersConfiguration();

        try {
            mockProvidersConfiguration.forEach((ref, provider) -> {
                provider.start(cache);
                decorateSimple("Starting {} on port {}.", ref, provider.getPort());

                cache.putMockProvider(provider);
                cache.putMockObject(ref, provider.providedMock());

            });
            decorateSimple("All mocks created.");

            cache.callAllMocksCreated();
        } catch (Exception e) {
            throw new RunnerInitializationException(e);
        }
    }

    private void initializeApplication(RunnerCache cache, ApplicationProvider applicationProvider) {
        applicationProvider.start(cache);
        if (applicationProvider.canProceed(cache)) {
            decorateSimple("Application under test is ready to accept requests!");
        } else {
            throw new InitializationError("Application is not ready yet. See configured progress assertion.");
        }
    }

    private void initializeTestContext(RunnerCache cache, TestContextProvider testContextProvider) {
        testContextProvider.initialize(cache);
        if (testContextProvider.canProceed(cache)) {
            decorateSimple("Test setup is ready.");
        } else {
            throw new InitializationError("Test context is not ready yet. See configured progress assertion.");
        }
    }

    private void printBanner() {
        try (
                InputStream startingBanner = getClass().getResourceAsStream("/opening-banner.txt");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ) {
            String banner = copyToString(startingBanner);
            PrintStream out = new PrintStream(baos);
            out.print(banner);
            log.info(baos.toString("UTF-8"));
        } catch (IOException e) {
            log.warn("I could print pretty banner for you :/");
        }
    }

    private String copyToString(InputStream in) throws IOException {
        if (in == null) {
            return "";
        } else {
            StringBuilder out = new StringBuilder();
            InputStreamReader reader = new InputStreamReader(in, Charset.forName("UTF-8"));
            char[] buffer = new char[4096];

            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                out.append(buffer, 0, bytesRead);
            }

            return out.toString();
        }
    }

    private void decorateSection(String sectionTitle) {
        int rightPad = 60 - 6 - sectionTitle.length();

        log.info("************************************************************");
        log.info("*                                                          *");
        log.info("*    {}{}*", sectionTitle, StringUtils.repeat(" ", rightPad));
        log.info("*                                                          *");
        log.info("************************************************************");
    }

    private void decorateSimple(Object... params) {
        log.info("*    " + params[0], Arrays.copyOfRange(params, 1, params.length));
    }

    private void decorateWarn(Object... params) {
        log.warn("*    " + params[0], Arrays.copyOfRange(params, 1, params.length));
    }

    private void decorateError(Object... params) {
        log.error("*    " + params[0], Arrays.copyOfRange(params, 1, params.length));
    }
}
