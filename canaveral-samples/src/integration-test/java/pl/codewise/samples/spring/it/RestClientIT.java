package pl.codewise.samples.spring.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpServerErrorException;
import pl.codewise.canaveral.mock.http.HttpRawRequest;
import pl.codewise.canaveral.mock.http.Method;
import pl.codewise.canaveral.mock.http.Mime;
import pl.codewise.canaveral.mock.http.MockRuleProvider;
import pl.codewise.canaveral.mock.http.HttpStatusCode;
import pl.codewise.samples.spring.webapp.rest.model.Response;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientIT extends BaseIT {

    @Inject
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectMapper appContextObjectMapper;

    @Value("${pl.codewise.luckyNumber:}")
    private int luckyNumber;

    @BeforeEach
    void setUp() {
        httpMockProvider.resetToDefaults();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldAskWithQuery() throws Exception {
        // given
        Map<String, String> expectedPayload = ImmutableMap.of("replay", "true");

        httpMockProvider.createRule()
                .whenCalledWith(Method.GET, "/search")
                .withQueryParam("q", "is elephant big?")
                .thenRespondWith(MockRuleProvider.Body.from(
                        expectedPayload,
                        Mime.JSON));

        // when
        Response response = restClient.askGoogle("is elephant big?");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBody()).isEqualToNormalizingNewlines(objectMapper.writeValueAsString(expectedPayload));

        // and even assert on captured request
        List<HttpRawRequest> capturedRequests = httpMockProvider.getCapturedRequests();
        assertThat(capturedRequests)
                .hasSize(1)
                .extracting(HttpRawRequest::getQueryParams)
                .contains(ImmutableMap.of("q", Collections.singletonList("is elephant big?")));
    }

    @Test
    void shouldFail() {
        // given
        httpMockProvider.createRule()
                .whenCalledWith(Method.GET, "/search")
                .withQueryParam("q", "fail now!")
                .thenRespondWith(MockRuleProvider.Body.error(), HttpStatusCode.INTERNAL_SERVER_ERROR);

        // when
        assertThatThrownBy(() -> restClient.askGoogle("fail now!"))
                .hasMessage("500 null")
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void shouldAutowireFromSpringAppContext() {
        assertThat(appContextObjectMapper).isSameAs(objectMapper);
        assertThat(luckyNumber).isEqualTo(3);
    }
}
