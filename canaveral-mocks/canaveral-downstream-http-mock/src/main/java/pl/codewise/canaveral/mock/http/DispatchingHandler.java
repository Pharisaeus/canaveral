package pl.codewise.canaveral.mock.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import static pl.codewise.canaveral.mock.http.Mime.TEXT;

class DispatchingHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchingHandler.class);

    private final HttpRuleRepository repository;
    private final Recorder recorder;
    private final HttpResponseRule UNKNOWN = new HttpResponseRule(new byte[0], TEXT, HttpStatusCode.NOT_FOUND, new
            Headers());

    DispatchingHandler(HttpRuleRepository repository, Recorder recorder) {
        this.repository = repository;
        this.recorder = recorder;
    }

    void handle(HttpExchange exchange) {
        URI requestURI = exchange.getRequestURI();
        String requestMethod = exchange.getRequestMethod();

        byte[] body;
        try {
            body = IOUtils.toByteArray(exchange.getRequestBody());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not handle request " + requestMethod + " " + requestURI);
        }
        HttpRawRequest rawRequest = HttpRawRequest.from(exchange, body);
        recorder.add(rawRequest);

        log.debug("Trying to find a response for {} {}", requestMethod, requestURI);
        HttpResponseRule ruleResponse = repository.findRule(rawRequest)
                .map(MockRule::getResponse)
                .orElse(UNKNOWN);

        if (ruleResponse == UNKNOWN) {
            log.warn("Could not match request to {} /{}", requestMethod, requestURI);
        }

        exchange.getResponseHeaders().set("Content-Type", ruleResponse.getContentType().getMime() + "; charset=UTF-8");
        ruleResponse
                .getHeaders()
                .forEach((key, values) -> values.forEach(val -> exchange.getResponseHeaders().add(key, val)));
        try {
            exchange.sendResponseHeaders(ruleResponse.getStatus().getCode(), ruleResponse.getBody().length);
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(ruleResponse.getBody());
            responseBody.close();
        } catch (IOException e) {
            log.error("Could not send response :/ " + ruleResponse, e);
        }
        exchange.close();
    }
}
