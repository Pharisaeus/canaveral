package pl.codewise.canaveral.mock.eureka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.MyDataCenterInfo;
import com.netflix.discovery.converters.jackson.EurekaJsonJacksonCodec;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pl.codewise.canaveral.core.mock.DiscoverableMockProvider;
import pl.codewise.canaveral.core.mock.MockProvider;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.netflix.appinfo.InstanceInfo.InstanceStatus.STARTING;
import static com.netflix.appinfo.InstanceInfo.InstanceStatus.UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// TODO replace with real eureka client
class EurekaMockProviderIntegrationTest {

    private static final String HOST_NAME_2 = "10.0.11.12";
    private static final String LOCALHOST = "localhost";
    private final ObjectMapper responseMapper = new EurekaJsonJacksonCodec().getObjectMapper(Applications.class);
    private final ObjectMapper requestMapper = new EurekaJsonJacksonCodec().getObjectMapper(EurekaHandler
            .InstanceInfoWrapper.class);
    @Mock
    private RunnerContext runnerContext;
    @Mock
    private MockProvider noOpMockProvider;
    @Mock
    private DiscoverableMockProvider discoverableMockProvider1;
    @Mock
    private DiscoverableMockProvider discoverableMockProvider2;
    private EurekaMockProvider eurekaMockProvider;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        eurekaMockProvider = EurekaMockProvider.newConfig()
                .registerEndpointUnder("eureka.client.serviceUrl.defaultZone")
                .mockJsonResponseFor("/eureka/apps/")
                .enableEnableLazyAppsRegistration(true)
                .build("EUREKA_APP");

        when(runnerContext.getFreePort()).thenCallRealMethod();

        eurekaMockProvider.start(runnerContext);

        when(discoverableMockProvider1.wantsRegistration()).thenReturn(true);
        when(discoverableMockProvider1.getDiscoverableAppNames()).thenReturn(ImmutableSet.of("A", "B"));
        when(discoverableMockProvider1.getPort()).thenReturn(50493);
        when(discoverableMockProvider1.getHost()).thenReturn("localhost");

        when(discoverableMockProvider2.wantsRegistration()).thenReturn(true);
        when(discoverableMockProvider2.getDiscoverableAppNames()).thenReturn(ImmutableSet.of("B"));
        when(discoverableMockProvider2.getPort()).thenReturn(50493);
        when(discoverableMockProvider2.getHost()).thenReturn("10.0.11.12");
    }

    @Test
    void shouldReturnEmptyApplications() throws Exception {
        // given
        URL obj = new URL(urlToService("/apps/?"));
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");

        // when
        int responseCode = con.getResponseCode();

        // then
        assertThat(responseCode).isEqualTo(200);
        assertThat(getResponse(con).getRegisteredApplications()).isEmpty();
    }

    @Test
    void shouldReturnConfiguredDiscoverableApplicationsWithStaticallyRegisteredApps() throws Exception {
        // given
        eurekaMockProvider = EurekaMockProvider.newConfig()
                .registerEndpointUnder("eureka.client.serviceUrl.defaultZone")
                .mockJsonResponseFor("/eureka/apps/")
                .enableEnableLazyAppsRegistration(true)
                .registerApplication("Z", EurekaMockProvider.InstanceConfig.newInstance("172.10.0.1"))
                .registerApplication("Z", EurekaMockProvider.InstanceConfig.newInstance("172.20.0.2"),
                        EurekaMockProvider.InstanceConfig.newInstance("172.20.0.3"))
                .registerApplication("Y", EurekaMockProvider.InstanceConfig.newInstance(UUID.randomUUID().toString(),
                        "174.40.1.1", 8018))
                .build("EUREKA_APP");
        eurekaMockProvider.start(runnerContext);

        when(runnerContext.getMocks()).thenReturn(Stream.of(
                discoverableMockProvider1
        ));

        // when
        eurekaMockProvider.allMocksCreated(runnerContext);

        // then
        URL obj = new URL(urlToService("/apps/?"));
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        assertThat(responseCode).isEqualTo(200);

        List<Application> registeredApplications = getResponse(con).getRegisteredApplications();

        assertThat(registeredApplications)
                .hasSize(4)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getHostName)
                .hasSize(6)
                .containsOnly("172.10.0.1", "172.20.0.2", "172.20.0.3", "174.40.1.1", LOCALHOST);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getPort)
                .hasSize(6)
                .containsOnly(EurekaMockProvider.InstanceConfig.DEFAULT_PORT, EurekaMockProvider.InstanceConfig
                        .DEFAULT_PORT, EurekaMockProvider.InstanceConfig.DEFAULT_PORT, 8018, 50493);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getAppName)
                .containsOnly("Z", "Y", "A", "B");
    }

    @Test
    void shouldReturnConfiguredDiscoverableApplications() throws Exception {
        // given
        when(runnerContext.getMocks()).thenReturn(Stream.of(
                noOpMockProvider,
                discoverableMockProvider1,
                discoverableMockProvider2
        ));

        // when
        eurekaMockProvider.allMocksCreated(runnerContext);

        // then
        URL obj = new URL(urlToService("/apps/?"));
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        int responseCode = con.getResponseCode();
        assertThat(responseCode).isEqualTo(200);

        List<Application> registeredApplications = getResponse(con).getRegisteredApplications();

        assertThat(registeredApplications)
                .hasSize(2)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getHostName)
                .hasSize(3)
                .containsOnly(LOCALHOST, HOST_NAME_2);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getPort)
                .hasSize(3)
                .containsOnly(50493);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getAppName)
                .containsOnly("A", "B");
    }

    @Test
    void shouldReturnLazilyDiscoveredApplications() throws Exception {
        // given
        int expectedAppPort = 45333;
        String expectedAppHost = "localhost";
        String expectedAppName = "A";
        long initialVersion = 0L;

        URL getUrl = new URL(urlToService("/apps/?"));
        HttpURLConnection getCon = (HttpURLConnection) getUrl.openConnection();
        getCon.setRequestMethod("GET");

        int responseCode = getCon.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        assertThat(getResponse(getCon).getVersion()).isEqualTo(initialVersion);

        // when
        registerApp(expectedAppPort, expectedAppHost, expectedAppName, STARTING);

        // then
        getUrl = new URL(urlToService("/apps/?"));
        getCon = (HttpURLConnection) getUrl.openConnection();
        getCon.setRequestMethod("GET");

        responseCode = getCon.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        Applications response = getResponse(getCon);
        assertThat(response.getVersion()).isEqualTo(initialVersion + 1);
        List<Application> registeredApplications = response.getRegisteredApplications();
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getHostName)
                .hasSize(1)
                .containsOnly(expectedAppHost);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getPort)
                .hasSize(1)
                .containsOnly(expectedAppPort);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getStatus)
                .hasSize(1)
                .containsOnly(STARTING);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getAppName)
                .containsOnly(expectedAppName);

        // and when
        registerApp(expectedAppPort, expectedAppHost, expectedAppName, UP);

        // then
        getUrl = new URL(urlToService("/apps/?"));
        getCon = (HttpURLConnection) getUrl.openConnection();
        getCon.setRequestMethod("GET");

        responseCode = getCon.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        response = getResponse(getCon);
        assertThat(response.getVersion()).isEqualTo(initialVersion + 2);
        registeredApplications = response.getRegisteredApplications();
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getHostName)
                .hasSize(1)
                .containsOnly(expectedAppHost);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getPort)
                .hasSize(1)
                .containsOnly(expectedAppPort);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getStatus)
                .hasSize(1)
                .containsOnly(UP);
    }

    @Test
    void shouldReturnAllApplications() throws Exception {
        // given
        long initialVersion = 0L;

        URL getUrl = new URL(urlToService("/apps/?"));
        HttpURLConnection getCon = (HttpURLConnection) getUrl.openConnection();
        getCon.setRequestMethod("GET");

        int responseCode = getCon.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        assertThat(getResponse(getCon).getVersion()).isEqualTo(initialVersion);

        registerApp(45333, "localhost", "LApp", STARTING);

        when(runnerContext.getMocks()).thenReturn(Stream.of(
                noOpMockProvider,
                discoverableMockProvider1,
                discoverableMockProvider2
        ));
        eurekaMockProvider.allMocksCreated(runnerContext);

        // when
        getUrl = new URL(urlToService("/apps/?"));
        getCon = (HttpURLConnection) getUrl.openConnection();
        getCon.setRequestMethod("GET");

        responseCode = getCon.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
        Applications response = getResponse(getCon);
        assertThat(response.getVersion()).isEqualTo(initialVersion + 1);
        List<Application> registeredApplications = response.getRegisteredApplications();
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getHostName)
                .hasSize(4)
                .containsOnly(LOCALHOST, HOST_NAME_2);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getPort)
                .containsOnly(45333, discoverableMockProvider1.getPort());
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getStatus)
                .containsOnly(STARTING, UP);
        assertThat(registeredApplications)
                .flatExtracting(Application::getInstances)
                .extracting(InstanceInfo::getAppName)
                .containsOnly("A", "B", "LApp");
    }

    @Test
    void shouldHandleAppHeartbeatOfUnknownApp() throws Exception {
        // given
        int expectedAppPort = 45333;
        String expectedAppHost = "localhost";
        String expectedAppName = "A";

        // when
        HttpURLConnection updateCon = updateApp(expectedAppPort, expectedAppHost, expectedAppName);

        // then
        int responseCode = updateCon.getResponseCode();
        assertThat(responseCode).isEqualTo(404);
    }

    @Test
    void shouldHandleAppHeartbeat() throws Exception {
        // given
        int expectedAppPort = 45333;
        String expectedAppHost = "localhost";
        String expectedAppName = "A";

        registerApp(expectedAppPort, expectedAppHost, expectedAppName, STARTING);

        // when
        HttpURLConnection updateCon = updateApp(expectedAppPort, expectedAppHost, expectedAppName);

        // then
        int responseCode = updateCon.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
    }

    @Test
    void shouldHandleAppDelete() throws Exception {
        // given

        // when
        HttpURLConnection updateCon = deleteApp("A");

        // then
        int responseCode = updateCon.getResponseCode();
        assertThat(responseCode).isEqualTo(200);
    }

    private void registerApp(int expectedAppPort, String expectedAppHost, String expectedAppName, InstanceStatus status)
            throws IOException {
        EurekaHandler.InstanceInfoWrapper body = getInstanceInfo(expectedAppName, expectedAppPort, expectedAppHost,
                status);
        byte[] bytes = requestMapper.writeValueAsBytes(body);

        URL postUrl = new URL(urlToService("/apps/" + expectedAppName));
        HttpURLConnection postCon = (HttpURLConnection) postUrl.openConnection();
        postCon.setRequestMethod("POST");
        postCon.setRequestProperty("Accept", MediaType.JSON_UTF_8.toString());
        postCon.setRequestProperty("Content-Type", MediaType.JSON_UTF_8.toString());
        postCon.setDoOutput(true);
        writeBody(postCon, bytes);

        int responseCode = postCon.getResponseCode();
        assertThat(responseCode).isEqualTo(204);
    }

    private HttpURLConnection updateApp(int expectedAppPort, String expectedAppHost, String expectedAppName)
            throws IOException {
        EurekaHandler.InstanceInfoWrapper body = getInstanceInfo(expectedAppName, expectedAppPort, expectedAppHost,
                STARTING);
        byte[] bytes = requestMapper.writeValueAsBytes(body);

        URL postUrl = new URL(urlToService("/apps/" + expectedAppName + "/TEST-CLIENT_HOST"));
        HttpURLConnection updateCon = (HttpURLConnection) postUrl.openConnection();
        updateCon.setRequestMethod("PUT");
        updateCon.setRequestProperty("Accept", MediaType.JSON_UTF_8.toString());
        updateCon.setRequestProperty("Content-Type", MediaType.JSON_UTF_8.toString());
        updateCon.setDoOutput(true);
        writeBody(updateCon, bytes);

        return updateCon;
    }

    private HttpURLConnection deleteApp(String expectedAppName)
            throws IOException {
        URL postUrl = new URL(urlToService("/apps/" + expectedAppName + "/TEST-CLIENT_HOST"));
        HttpURLConnection updateCon = (HttpURLConnection) postUrl.openConnection();
        updateCon.setRequestMethod("DELETE");
        updateCon.setRequestProperty("Accept", MediaType.JSON_UTF_8.toString());
        updateCon.setRequestProperty("Content-Type", MediaType.JSON_UTF_8.toString());

        return updateCon;
    }

    private void writeBody(HttpURLConnection postCon, byte[] bytes) throws IOException {
        OutputStream outputStream = postCon.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
    }

    private Applications getResponse(HttpURLConnection con) throws IOException {
        String responseBody = getResponseBody(con);
        return responseMapper.readValue(responseBody, Applications.class);
    }

    private String getResponseBody(HttpURLConnection con) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response.toString();
    }

    private String urlToService(String path) {
        return eurekaMockProvider.getEndpoint() + path;
    }

    private EurekaHandler.InstanceInfoWrapper getInstanceInfo(String appName, int port, String host, InstanceStatus
            status) {
        EurekaHandler.InstanceInfoWrapper wrapper = new EurekaHandler.InstanceInfoWrapper();
        wrapper.setInstance(new InstanceInfo(
                UUID.randomUUID().toString(),
                appName,
                "",
                host,
                "",
                new InstanceInfo.PortWrapper(true, port),
                null,
                "",
                "",
                "",
                "",
                appName,
                "",
                -1,
                new MyDataCenterInfo(DataCenterInfo.Name.MyOwn),
                host,
                status,
                null,
                null,
                false,
                null,
                Instant.now().toEpochMilli(),
                Instant.now().toEpochMilli(),
                null,
                ""));

        return wrapper;
    }
}