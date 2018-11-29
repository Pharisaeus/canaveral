package pl.codewise.canaveral.mock.jmx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class JmxRuleCreator {

    private List<JmxMockRule> rules = new ArrayList<>();

    public void addRule(String objectName, String methodName, Object[] parameters, Supplier<Object> response) {
        rules.add(new JmxMockRule(objectName, methodName, parameters, response));
    }

    public List<JmxMockRule> getRules() {
        return rules;
    }
}
