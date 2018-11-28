package pl.codewise.canaveral.mock.jmx;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JmxMockTest {

    private static final Logger log = LoggerFactory.getLogger(JmxMockTest.class);
    private static final String JSON_RESPONSE = "{a : b}";
    private static final String PLAIN_RESPONSE = "a: b";

    private JmxMockProvider jmxMockProvider;
    private static final String CAMPAIGN_ID = "bf86b33b-7774-4220-bcf1-59ee80665920";

    @BeforeEach
    void setUp() throws Exception {
        jmxMockProvider = JmxMockProvider.newConfig()
                .registerJmxPortUnder("bidder.us-east.jmx.port")
                .withRules(rules -> {
                    rules.withMBean(
                            "com.codewise.voluum.dsp.metrics.service:name=inMemoryMetricsService," +
                                    "type=InMemoryMetricsService")
                            .whenCalled("getMetricsCount", CAMPAIGN_ID, "json")
                            .thenRespondWith(JSON_RESPONSE)
                            .whenCalled("getMetricsCount", CAMPAIGN_ID)
                            .thenRespondWith(PLAIN_RESPONSE)
                    ;
                    rules.withMBean(
                            "com.codewise.voluum.dsp.metrics.service:name=otherMbean," +
                                    "type=OtherMbean")
                            .whenCalled("test", new Object[] {})
                            .thenRespondWith("otherTestBeanResult");
                })
                .build("bidder-jmx");

        final RunnerContext runnerContext = Mockito.mock(RunnerContext.class);
        Mockito.when(runnerContext.getFreePort()).thenCallRealMethod();
        jmxMockProvider.start(runnerContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        jmxMockProvider.stop();
    }

    @Test
    void shouldMatchPattern() {
        Object response =
                callJmx("com.codewise.voluum.dsp.metrics.service:name=inMemoryMetricsService," +
                                "type=InMemoryMetricsService",
                        "getMetricsCount",
                        new Object[] {CAMPAIGN_ID, "json"},
                        new String[] {String.class.getName(), String.class.getName()});
        assertThat(response).isEqualTo(JSON_RESPONSE);
        Object otherRuleResponse =
                callJmx("com.codewise.voluum.dsp.metrics.service:name=inMemoryMetricsService," +
                                "type=InMemoryMetricsService",
                        "getMetricsCount",
                        new Object[] {CAMPAIGN_ID},
                        new String[] {String.class.getName()});
        assertThat(otherRuleResponse).isEqualTo(PLAIN_RESPONSE);
    }

    @Test
    void shouldThrowExceptionWhenNoRuleMatched() {
        assertThatThrownBy(() -> callJmx(
                "com.codewise.voluum.dsp.metrics.service:name=inMemoryMetricsService,type=InMemoryMetricsService",
                "dd",
                new Object[]{CAMPAIGN_ID, "json"},
                new String[] {String.class.getName(), String.class.getName()}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("No matching rule for parameters dd");
    }

    private Object callJmx(String objectName, String operationName, Object[] parameters, String[] signature) {
        try (JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(jmxMockProvider.getEndpoint()))) {
            MBeanServerConnection mBeanServerConnection = connector.getMBeanServerConnection();
            log.trace("Attempting to invoke {} operation", operationName);
            Object response = mBeanServerConnection.invoke(new ObjectName(objectName), operationName, parameters,
                    signature);
            log.debug("Successfully executed {}", operationName);
            return response;
        } catch (Exception e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e.getCause();
            } else {
                throw new IllegalStateException("Jmx execution failed", e);
            }
        }
    }
}
