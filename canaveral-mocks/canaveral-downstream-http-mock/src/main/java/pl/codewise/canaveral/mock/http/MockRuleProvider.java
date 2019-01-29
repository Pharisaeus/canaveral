package pl.codewise.canaveral.mock.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableMap.copyOf;
import static java.util.Collections.emptyMap;

public class MockRuleProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final MessageCodec messageCodec = new MessageCodec(objectMapper);
    private final HttpRuleCreator ruleCreator;

    static {
        objectMapper.findAndRegisterModules();
    }

    public MockRuleProvider(HttpRuleCreator ruleCreator) {
        this.ruleCreator = ruleCreator;
    }

    public ResponseCollector whenCalledWith(Method method, String pathPattern) {
        return new ResponseCollector(ruleCreator, method, pathPattern);
    }

    public ProvidedPredicateResponseCollector whenCalledWith(Predicate<HttpRawRequest> requestPredicate) {
        return new ProvidedPredicateResponseCollector(ruleCreator, requestPredicate);
    }

    public static class File {

        private final String pathToResource;
        private Mime mime;

        private File(String pathToResource) {
            this.pathToResource = pathToResource;
        }

        public static File useResource(String pathToResource) {
            return new File(pathToResource);
        }

        public File withResponseContentType(Mime mime) {
            this.mime = mime;
            return this;
        }

        public Body getBody() {
            return Body.from(messageCodec.fileContentToBytes(pathToResource), mime);
        }
    }

    public static class Body {

        private final byte[] body;
        private final Mime mime;

        private Body(String body, Mime mime) {
            this(body.getBytes(Charset.forName("UTF-8")), mime);
        }

        private Body(byte[] body, Mime mime) {
            this.mime = mime;
            this.body = body;
        }

        public static Body from(byte[] body, Mime mime) {
            return new Body(body, mime);
        }

        public static Body from(Object value, Mime mime) {
            byte[] body;
            Mime bodyMime = mime;

            if (value instanceof String) {
                throw new IllegalArgumentException("There are better ways to send String back!");
            }
            try {
                body = objectMapper.writeValueAsBytes(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Cannot serialize object = " + value + " to " + mime, e);
            }
            return new Body(body, bodyMime);
        }

        public static Body error() {
            return new Body("Mock server could not process request.", Mime.TEXT);
        }

        public static Body asJsonFrom(String value) {
            return new Body(value, Mime.JSON);
        }

        public static Body asTextFrom(String value) {
            return new Body(value, Mime.TEXT);
        }

        public static Body asEncodedFieldsFrom(Map<String, String> fields) {
            List<String> params =
                    fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                            .collect(Collectors.toList());
            return new Body(Joiner.on("&").join(params), Mime.X_WWW_FORM);
        }

        public byte[] getBody() {
            return body;
        }

        public Mime getMime() {
            return mime;
        }

        private static String encode(String val) {
            try {
                return URLEncoder.encode(val, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException("Could not encode " + val);
            }
        }
    }

    public class ResponseCollector extends Respondable {

        private final HttpRuleCreator ruleCreator;
        private final Method method;
        private final String pathPattern;
        private final Map<String, List<String>> headers = new HashMap<>();
        private final Map<String, List<String>> query = new HashMap<>();
        private Body payload = new Body(new byte[0], Mime.X_WWW_FORM);

        private ResponseCollector(HttpRuleCreator ruleCreator, Method method, String pathPattern) {
            this.ruleCreator = ruleCreator;
            this.method = method;
            this.pathPattern = pathPattern;
        }

        @Override
        public void thenRespondWith(Body body, HttpStatusCode httpStatusCode, Map<String, List<String>> responseHeaders) {
            HttpResponseRule httpResponseRule = new HttpResponseRule(body.body,
                    body.mime,
                    httpStatusCode,
                    ImmutableMap.copyOf(responseHeaders));

            HttpRequestRule httpRequestRule = new HttpRequestRule(method,
                    pathPattern,
                    copyOf(query),
                    copyOf(headers),
                    payload.getBody());

            ruleCreator.addRule(httpRequestRule, httpResponseRule);
        }

        public ResponseCollector accepting(Mime mime) {
            addHeader("Accept", mime.getMime());
            return this;
        }

        public ResponseCollector withCookie(String cookie) {
            addHeader("Cookie", cookie.replace(" ", ""));
            return this;
        }

        public ResponseCollector withHeader(String headerName, String headerValue) {
            addHeader(headerName, headerValue);
            return this;
        }

        public ResponseCollector withQueryParam(String queryName, String queryValue) {
            query
                    .computeIfAbsent(queryName, (key) -> new ArrayList<>())
                    .add(queryValue);
            return this;
        }

        public ResponseCollector withPayload(Body payload) {
            this.payload = payload;
            return this;
        }

        private void addHeader(String headerName, String headerValue) {
            headers.computeIfAbsent(headerName, key -> new ArrayList<>()).add(headerValue);
        }
    }

    public class ProvidedPredicateResponseCollector extends Respondable {

        private final HttpRuleCreator ruleCreator;
        private Predicate<HttpRawRequest> requestPredicate;

        private ProvidedPredicateResponseCollector(HttpRuleCreator ruleCreator,
                Predicate<HttpRawRequest> requestPredicate) {
            this.ruleCreator = ruleCreator;
            this.requestPredicate = requestPredicate;
        }

        @Override
        public void thenRespondWith(Body body, HttpStatusCode httpStatusCode, Map<String, List<String>> responseHeaders) {
            HttpResponseRule httpResponseRule = new HttpResponseRule(body.body,
                    body.mime,
                    httpStatusCode,
                    ImmutableMap.copyOf(responseHeaders));

            ruleCreator.addRule(requestPredicate, httpResponseRule);
        }
    }

    private abstract class Respondable {

        public void thenRespondWith(Body body) {
            thenRespondWith(body, HttpStatusCode.OK);
        }

        public void thenRespondWith(Body body, HttpStatusCode httpStatusCode) {
            thenRespondWith(body, httpStatusCode, emptyMap());
        }

        public void thenRespondWith(Body body, Map<String, List<String>> responseHeaders) {
            thenRespondWith(body, HttpStatusCode.OK, responseHeaders);
        }

        abstract public void thenRespondWith(Body body, HttpStatusCode httpStatusCode,
                Map<String, List<String>> responseHeaders);
    }
}
