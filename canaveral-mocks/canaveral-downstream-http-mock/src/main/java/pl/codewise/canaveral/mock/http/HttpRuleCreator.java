package pl.codewise.canaveral.mock.http;

import java.util.function.Predicate;

interface HttpRuleCreator {

    void addRule(HttpRequestRule request, HttpResponseRule response);

    void addRule(Predicate<HttpRawRequest> requestPredicate, HttpResponseRule response);
}
