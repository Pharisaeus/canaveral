package pl.codewise.canaveral.mock.http;

import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

class MockRule {

    private static final Logger log = LoggerFactory.getLogger(MockRule.class);
    private final Predicate<HttpRawRequest> condition;
    private final HttpRequestRule request;
    private final HttpResponseRule response;

    private MockRule(Predicate<HttpRawRequest> requestPredicate, HttpRequestRule request,
            HttpResponseRule response) {
        this.condition = requestPredicate;
        this.request = request;
        this.response = response;
    }

    static MockRule create(Predicate<HttpRawRequest> requestPredicate, HttpResponseRule response) {
        return new MockRule(requestPredicate, null, response);
    }

    static MockRule create(HttpRequestRule ruleRequest, HttpResponseRule ruleResponse) {
        Pattern pathPattern = Pattern.compile(ruleRequest.getPathPattern());
        return new MockRule(incomingRequest -> {
            log.trace("Rule request {}.", incomingRequest);

            Map<String, List<String>> inHeaders = incomingRequest.getHeaders();
            Map<String, List<String>> ruleHeaders = ruleRequest.getHeaders();
            if (hasDifferentKeysOrDifferentValues(ruleHeaders, inHeaders)) {
                log.trace("Incoming request is not matching headers [{}] of this rule [{}].",
                        inHeaders, ruleHeaders);
                return false;
            }

            if (ruleRequest.getMethod() != incomingRequest.getMethod()) {
                log.trace("Incoming request is not matching method [{}] of this rule [{}].",
                        incomingRequest.getMethod(), ruleRequest.getMethod());
                return false;
            }

            int inPathLength = incomingRequest.getPath().length();
            if (!pathPattern.matcher(incomingRequest.getPath()).matches() &&
                    !pathPattern.matcher(incomingRequest.getPath().substring(0, inPathLength - 1)).matches()) {
                log.trace("Incoming request is not matching path [{}] of this rule [{}].",
                        incomingRequest.getPath(), ruleRequest.getPathPattern());
                return false;
            }

            Map<String, List<String>> ruleQuery = ruleRequest.getQuery();
            Map<String, List<String>> inQueryParams = incomingRequest.getQueryParams();
            if (hasDifferentKeysOrDifferentValues(ruleQuery, inQueryParams)) {
                log.trace("Incoming request is not matching query params [{}] of this rule [{}].",
                        inQueryParams, ruleQuery);
                return false;
            }

            byte[] ruleBody = ruleRequest.getBody();
            byte[] inBody = incomingRequest.getBody();
            if (isNotEmpty(ruleBody)) {
                if (!Arrays.equals(ruleBody, inBody)) {
                    log.trace("Incoming request is not matching body [{}] of this rule [{}].",
                            inBody, ruleBody);
                    return false;
                }
            }

            return true;
        }, ruleRequest, ruleResponse);
    }

    private static boolean isNotEmpty(byte[] ruleBody) {
        return ruleBody != null && ruleBody.length > 0;
    }

    private static boolean hasDifferentKeysOrDifferentValues(Map<String, List<String>> rule,
            Map<String, List<String>> in) {
        return !areValid(rule, in) ||
                !rule.entrySet().stream()
                        .allMatch(entry -> in.getOrDefault(entry.getKey(), emptyList()).containsAll(entry.getValue()));
    }

    private static boolean areValid(Map<String, ?> rule, Map<String, ?> in) {
        int ruleSize = 0;
        int inSize = 0;
        if (rule != null) {
            ruleSize = rule.size();
        }
        if (in != null) {
            inSize = in.size();
        }

        // NOTE I am checking only for params that were set in rule, there can be more values in incoming request.
        return ruleSize <= inSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MockRule mockRule = (MockRule) o;
        return Objects.equal(request, mockRule.request);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(request);
    }

    Predicate<HttpRawRequest> getCondition() {
        return condition;
    }

    HttpRequestRule getRequest() {
        return request;
    }

    HttpResponseRule getResponse() {
        return response;
    }
}
