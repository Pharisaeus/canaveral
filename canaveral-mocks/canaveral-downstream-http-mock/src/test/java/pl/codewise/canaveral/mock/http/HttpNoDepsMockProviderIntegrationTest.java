package pl.codewise.canaveral.mock.http;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.codewise.canaveral.core.runtime.DummyRunnerContext;
import pl.codewise.canaveral.mock.http.HttpRawRequest.Header;
import pl.codewise.canaveral.mock.http.MockRuleProvider.Body;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class HttpNoDepsMockProviderIntegrationTest {

    private int freePort;
    private HttpNoDepsMockProvider httpMockProvider;
    private DummyRunnerContext runnerContext;

    @BeforeEach
    void setUp() throws Exception {
        httpMockProvider = HttpNoDepsMockProvider.newConfig()
                .registerEndpointUnder("dummy.endpoint.property")
                .registerPortUnder("dummy.port.property")
                .withRules(rules -> rules
                        .whenCalledWith(Method.GET, "/path-to-resource")
                        .accepting(Mime.JSON)
                        .withHeader("b", "3")
                        .withQueryParam("a", "1")
                        .thenRespondWith(Body.asJsonFrom("{\"name\": \"bob\"}"))
                )
                .build("HTTP SERVICE MOCK");
        runnerContext = new DummyRunnerContext();

        httpMockProvider.start(runnerContext);
        freePort = httpMockProvider.getPort();
    }

    @AfterEach
    void tearDown() {
        httpMockProvider.resetToDefaults();
        httpMockProvider.stop();
    }

    @Test
    void shouldSetPropertiesFromConfig() throws Exception {
        // given
        tearDown();

        httpMockProvider = HttpNoDepsMockProvider.newConfig()
                .registerEndpointUnder("other.endpoint.property")
                .registerPortUnder("other.port.property")
                .build("HTTP SERVICE MOCK");

        assertThat(getProperty("other.endpoint.property")).isNotEqualToIgnoringCase("http://localhost:" + freePort);
        assertThat(getProperty("other.port.property")).isNotEqualToIgnoringCase("" + freePort);

        // when
        httpMockProvider.start(runnerContext);
        freePort = httpMockProvider.getPort();

        // then
        assertThat(getProperty("other.endpoint.property")).isEqualTo("http://localhost:" + freePort);
        assertThat(getProperty("other.port.property")).isEqualTo("" + freePort);

        assertThat(httpMockProvider.getEndpoint()).isEqualTo("http://localhost:" + freePort);
        assertThat(httpMockProvider.getHost()).isEqualTo("localhost");
        assertThat(httpMockProvider.getPort()).isEqualTo(freePort);
        assertThat(httpMockProvider.getMockName()).isEqualTo("HTTP SERVICE MOCK");
    }

    @Test
    void shouldGetResource() {
        // given
        HttpGet getRequest = createGetRequest("/path-to-resource?a=1");
        getRequest.addHeader("Accept", Mime.JSON.getMime());
        getRequest.addHeader("b", "3");

        // when
        HttpResponse response = getResponseForRequest(getRequest);

        // then
        assertThat(response.statusCode).isEqualTo(200);
        assertThat(response.bodyAsString).isEqualTo("{\"name\": \"bob\"}");

        List<HttpRawRequest> capturedRequests = httpMockProvider.getCapturedRequests();
        Assertions.assertThat(capturedRequests).hasSize(1);
        Assertions.assertThat(capturedRequests).extracting(HttpRawRequest::getMethod).containsOnly(Method.GET);
        Assertions.assertThat(capturedRequests)
                .flatExtracting(HttpRawRequest::getQueryParams)
                .containsOnly(ImmutableMap.of("a", ImmutableList.of("1")));
        Assertions.assertThat(capturedRequests)
                .flatExtracting(HttpRawRequest::getHeadersAsList)
                .contains(
                        Header.from("Accept", Mime.JSON.getMime()),
                        Header.from("B", "3"));
    }

    @Test
    void shouldFailOnWrongAcceptHeader() {
        // given
        HttpGet getRequest = createGetRequest("/path-to-resource");
        getRequest.addHeader("b", "3");

        // when
        HttpResponse response = getResponseForRequest(getRequest);

        // then
        assertThat(response.statusCode).isEqualTo(404);
    }

    @Test
    void shouldFailOnWrongCustomHeader() {
        // given
        HttpGet getRequest = createGetRequest("/path-to-resource");
        getRequest.addHeader("Accept", Mime.JSON.getMime());

        // when
        HttpResponse response = getResponseForRequest(getRequest);

        // then
        assertThat(response.statusCode).isEqualTo(404);
    }

    @Test
    void shouldFailOnWrongQuery() {
        // given
        HttpGet getRequest = createGetRequest("/path-to-resource");
        getRequest.addHeader("Accept", Mime.JSON.getMime());
        getRequest.addHeader("b", "3");

        // when
        HttpResponse response = getResponseForRequest(getRequest);

        // then
        assertThat(response.statusCode).isEqualTo(404);
    }

    @Test
    void shouldFailOnWrongPath() {
        // given
        HttpGet getRequest = createGetRequest("/other-path");
        getRequest.addHeader("Accept", Mime.JSON.getMime());
        getRequest.addHeader("b", "3");

        // when
        HttpResponse response = getResponseForRequest(getRequest);

        // then
        assertThat(response.statusCode).isEqualTo(404);
    }

    @Test
    void shouldAddRuleAfterStart() {
        // given
        httpMockProvider.createRule()
                .whenCalledWith(Method.GET, "/other-resource")
                .withHeader("bb", "4")
                .withQueryParam("aa", "2")
                .thenRespondWith(Body.asJsonFrom("{\"other\": \"alice\"}"));

        HttpGet getRequest = createGetRequest("/other-resource?aa=2");
        getRequest.addHeader("Accept", Mime.JSON.getMime());
        getRequest.addHeader("bb", "4");

        // when
        HttpResponse response = getResponseForRequest(getRequest);

        // then
        assertThat(response.statusCode).isEqualTo(200);
        assertThat(response.bodyAsString).isEqualTo("{\"other\": \"alice\"}");
    }

    @Test
    void shouldAcceptMultipleHeaderWithSameKey() {
        // given
        httpMockProvider.createRule()
                .whenCalledWith(Method.GET, "/other-resource")
                .withHeader("bb", "3")
                .withHeader("bb", "4")
                .withHeader("cc", "3")
                .thenRespondWith(Body.asTextFrom(""));

        HttpGet getRequest = createGetRequest("/other-resource");
        getRequest.addHeader("bb", "3");
        getRequest.addHeader("bb", "4");
        getRequest.addHeader("cc", "3");

        // when
        HttpResponse response = getResponseForRequest(getRequest);

        // then
        assertThat(response.statusCode).isEqualTo(200);
    }

    @Test
    void shouldAcceptFormPayloadFields() {
        // given
        Body payload = Body.asEncodedFieldsFrom(ImmutableMap.<String, String>builder()
                .put("aa", "3")
                .put("bb", "4")
                .put("cc", "3")
                .build());
        httpMockProvider.createRule()
                .whenCalledWith(Method.POST, "/other-resource")
                .withPayload(payload)
                .thenRespondWith(Body.asTextFrom("abc"));

        HttpPost postRequest = createPostRequest("/other-resource", payload.getBody());

        // when
        HttpResponse response = getResponseForRequest(postRequest);

        // then
        assertThat(response.statusCode).isEqualTo(200);
        assertThat(response.bodyAsString).isEqualTo("abc");
    }

    @Test
    void shouldNotMatchWhenFormPayloadFieldsDoNotMatch() {
        // given
        Body expected = Body.asEncodedFieldsFrom(ImmutableMap.<String, String>builder()
                .put("aa", "3")
                .put("bb", "4")
                .put("cc", "3")
                .build());
        Body payload = Body.asEncodedFieldsFrom(ImmutableMap.<String, String>builder()
                .put("aa", "999999")
                .put("bb", "4")
                .put("cc", "3")
                .build());
        httpMockProvider.createRule()
                .whenCalledWith(Method.POST, "/other-resource")
                .withPayload(expected)
                .thenRespondWith(Body.asTextFrom("abc"));

        HttpPost postRequest = createPostRequest("/other-resource", payload.getBody());

        // when
        HttpResponse response = getResponseForRequest(postRequest);

        // then
        assertThat(response.statusCode).isEqualTo(404);
    }

    @Test
    void shouldRespondWithMultipleHeaders() {
        // given
        httpMockProvider.createRule()
                .whenCalledWith(Method.GET, "/other-resource")
                .thenRespondWith(Body.asTextFrom(""), ImmutableMap.of(
                        "ee", asList("6", "7"),
                        "ff", singletonList("8")
                ));

        // when
        HttpResponse response = getResponseForPath("/other-resource");

        // then
        assertThat(response.statusCode).isEqualTo(200);

        Map<String, List<String>> headers = response.headers;
        assertThat(headers.get("Ee")).contains("6", "7");
        assertThat(headers.get("Ff")).contains("8");
    }

    @Test
    void shouldGetRequestBody() {
        // given
        httpMockProvider.createRule()
                .whenCalledWith(Method.POST, "/other-resource")
                .thenRespondWith(Body.asTextFrom("cos"));
        byte[] payload = "abc".getBytes();

        // when
        HttpResponse response = getResponseForPath("/other-resource", payload);

        // then
        List<HttpRawRequest> capturedRequests = httpMockProvider.getCapturedRequests();
        Assertions.assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).getBody()).isEqualTo(payload);
        assertThat(response.bodyAsString).isEqualTo("cos");
    }

    @Test
    void shouldMatchRequestUsingCustomRequestPredicate() {
        // given
        httpMockProvider.createRule()
                .whenCalledWith(httpRawRequest -> true)
                .thenRespondWith(Body.asTextFrom("cos"));
        byte[] payload = "abc".getBytes();

        // when
        HttpResponse response = getResponseForPath("/other-resource", payload);

        // then
        List<HttpRawRequest> capturedRequests = httpMockProvider.getCapturedRequests();
        Assertions.assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).getBody()).isEqualTo(payload);
        assertThat(response.bodyAsString).isEqualTo("cos");
    }

    @Test
    void shouldNotMatchRequestUsingCustomRequestPredicate() {
        // given
        httpMockProvider.createRule()
                .whenCalledWith(httpRawRequest -> false)
                .thenRespondWith(Body.asTextFrom("cos"));
        byte[] payload = "abc".getBytes();

        // when
        HttpResponse response = getResponseForPath("/other-resource", payload);

        // then
        List<HttpRawRequest> capturedRequests = httpMockProvider.getCapturedRequests();
        Assertions.assertThat(capturedRequests).hasSize(1);

        assertThat(response.statusCode).isEqualTo(404);
    }

    @SuppressWarnings("SameParameterValue")
    private HttpResponse getResponseForPath(String path, byte[] payload) {
        HttpPost postRequest = createPostRequest(path, payload);
        return getResponseForRequest(postRequest);
    }

    private HttpPost createPostRequest(String path, byte[] payload) {
        HttpPost httpPost = new HttpPost(httpMockProvider.getEndpoint() + path);
        httpPost.setEntity(new ByteArrayEntity(payload));
        return httpPost;
    }

    @SuppressWarnings("SameParameterValue")
    private HttpResponse getResponseForPath(String path) {
        HttpGet request = createGetRequest(path);
        return getResponseForRequest(request);
    }

    private HttpGet createGetRequest(String path) {
        return new HttpGet(httpMockProvider.getEndpoint() + path);
    }

    private HttpResponse getResponseForRequest(HttpUriRequest request) {
        try {
            CloseableHttpClient client = HttpClientBuilder.create().build();
            CloseableHttpResponse response = client.execute(request);
            return new HttpResponse(response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class HttpResponse {

        private final String bodyAsString;
        private final int statusCode;
        private final Map<String, List<String>> headers;

        private HttpResponse(CloseableHttpResponse response) {
            try {
                bodyAsString = EntityUtils.toString(response.getEntity());
            } catch (IOException e) {
                throw new IllegalStateException("could not read body.", e);
            }
            statusCode = response.getStatusLine().getStatusCode();

            headers = new HashMap<>();
            Arrays.stream(response.getAllHeaders())
                    .forEach(header -> headers
                            .computeIfAbsent(header.getName(), key -> new ArrayList<>())
                            .add(header.getValue()));
        }
    }
}