package pl.codewise.samples.spring.webapp.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pl.codewise.samples.spring.webapp.rest.model.Response;

import java.net.URI;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@org.springframework.web.bind.annotation.RestController
@RequestMapping(path = "/v1/google")
public class RestController {

    private static final Logger log = LoggerFactory.getLogger(RestController.class);

    private final String downstreamEndpoint;
    private final RestTemplate downstreamClient;
    private final ObjectMapper mapper;

    @Autowired
    RestController(
            @Value("${pl.codewise.luckyNumber:}") int luckyNumber,
            @Value("${pl.codewise.downstream.query.endpoint:}") String downstreamEndpoint,
            RestTemplate downstreamClient,
            ObjectMapper mapper) {
        this.downstreamEndpoint = downstreamEndpoint;
        this.downstreamClient = downstreamClient;
        this.mapper = mapper;

        log.info("Your lucky number is {}.", luckyNumber);
    }

    @RequestMapping(method = GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public Response search(@RequestParam("q") String query) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(query), "Query must be provided");

        URI uri = UriComponentsBuilder.fromUriString(downstreamEndpoint + "/search")
                .queryParam("q", query)
                .build()
                .toUri();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(HttpHeaders.ACCEPT_ENCODING, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<String> httpEntity = new HttpEntity<>(httpHeaders);
        ResponseEntity<Map> response = downstreamClient.exchange(uri, HttpMethod.GET, httpEntity, Map.class);
        String payload;
        try {
            payload = mapper.writeValueAsString(response.getBody());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot serialize payload.", e);
        }

        return new Response(response.getStatusCode(), payload);
    }
}
