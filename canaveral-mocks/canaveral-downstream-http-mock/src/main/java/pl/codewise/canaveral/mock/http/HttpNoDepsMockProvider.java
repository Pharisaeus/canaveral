package pl.codewise.canaveral.mock.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.codewise.canaveral.core.mock.MockConfig;
import pl.codewise.canaveral.core.mock.MockProvider;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

public class HttpNoDepsMockProvider implements MockProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpNoDepsMockProvider.class);

    private final ProviderConfig mockConfig;
    private final String mockName;
    private int port = 0;
    private HttpServer mockServer;
    private MockRuleProvider mockRuleProvider;
    private HttpRuleRepository repository;
    private Recorder recorder;

    private HttpNoDepsMockProvider(ProviderConfig mockConfig, String mockName) {
        this.mockConfig = mockConfig;
        this.mockName = mockName;
    }

    public static ProviderConfig newConfig() {
        return new ProviderConfig();
    }

    @Override
    public int getPort() {
        checkArgument(port != 0, "Mock is not started yet!");
        return port;
    }

    @Override
    public String getHost() {
        return mockConfig.host;
    }

    @Override
    public String getEndpoint() {
        return "http://" + getHost() + ":" + getPort();
    }

    @Override
    public String getMockName() {
        return mockName;
    }

    @Override
    public void start(RunnerContext context) throws Exception {
        this.port = context.getFreePort();

        if (!mockConfig.endpointProperties.isEmpty()) {
            mockConfig.endpointProperties.forEach(property -> {
                System.setProperty(property, getEndpoint());
                log.info("Setting system property {} to {}.", property, System.getProperty(property));
            });
        }
        if (!mockConfig.portProperties.isEmpty()) {
            mockConfig.portProperties.forEach(property -> {
                System.setProperty(property, Integer.toString(getPort()));
                log.info("Setting system property {} to {}.", property, System.getProperty(property));
            });
        }

        repository = new HttpRuleRepository(mockConfig.defaultsRules);
        recorder = new Recorder();

        mockRuleProvider = new MockRuleProvider(repository);
        DispatchingHandler dispatchingHandler = new DispatchingHandler(repository, recorder);
        mockServer = HttpServer.create(new InetSocketAddress(port), 0);
        mockServer.createContext("/", dispatchingHandler::handle);
        mockServer.start();
    }

    @Override
    public void stop() {
        mockServer.stop(0);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mockName", mockName)
                .toString();
    }

    public MockRuleProvider createRule() {
        return mockRuleProvider;
    }

    public void resetToDefaults() {
        repository.resetToDefault();
        recorder.reset();
    }

    public List<HttpRawRequest> getCapturedRequests() {
        return recorder.getLastRequests();
    }

    @VisibleForTesting
    HttpServer getMockServer() {
        return mockServer;
    }

    public static class ProviderConfig implements MockConfig<HttpNoDepsMockProvider> {

        private String host = HOST;
        private Set<String> endpointProperties = new HashSet<>();
        private Set<String> portProperties = new HashSet<>();
        private List<MockRule> defaultsRules = Collections.emptyList();

        private ProviderConfig() {
        }

        @Override
        public HttpNoDepsMockProvider build(String mockName) {
            return new HttpNoDepsMockProvider(this, mockName);
        }

        public ProviderConfig registerPortUnder(String property) {
            checkArgument(!isNullOrEmpty(property));

            portProperties.add(property);
            return this;
        }

        public ProviderConfig registerEndpointUnder(String property) {
            checkArgument(!isNullOrEmpty(property));

            endpointProperties.add(property);
            return this;
        }

        public ProviderConfig withHost(String host) {
            this.host = host;
            return this;
        }

        public ProviderConfig withRules(Consumer<MockRuleProvider> ruleProvider) {
            DefaultRuleCreator ruleCreator = new DefaultRuleCreator();
            MockRuleProvider mockRuleProvider = new MockRuleProvider(ruleCreator);
            ruleProvider.accept(mockRuleProvider);

            defaultsRules = ImmutableList.copyOf(ruleCreator.rules);
            return this;
        }
    }

    private static class DefaultRuleCreator implements HttpRuleCreator {

        private List<MockRule> rules = new ArrayList<>();

        @Override
        public void addRule(HttpRequestRule request, HttpResponseRule response) {
            rules.add(0, MockRule.create(request, response));
        }

        @Override
        public void addRule(Predicate<HttpRawRequest> requestPredicate, HttpResponseRule response) {
            rules.add(MockRule.create(requestPredicate, response));
        }
    }
}
