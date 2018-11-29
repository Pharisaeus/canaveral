package pl.codewise.canaveral.mock.jmx;

import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class JmxMockRule {

    private String objectName;
    private String methodName;
    private Object[] parameters;
    private Supplier<Object> response;
    private BiPredicate<String, Object[]> condition;

    public JmxMockRule(String objectName, String methodName, Object[] parameters, Supplier<Object> response) {
        this.objectName = objectName;
        this.methodName = methodName;
        this.parameters = parameters;
        this.response = response;
    }

    public BiPredicate<String, Object[]> getCondition() {
        return (methodName, parameters) ->
                this.methodName.equals(methodName) &&
                        Arrays.equals(this.parameters, parameters);
    }

    public Supplier<Object> getResponse() {
        return response;
    }

    public String getObjectName() {
        return objectName;
    }
}
