package pl.codewise.canaveral.mock.jmx;

import java.util.function.Supplier;

public class MBeanProvider {

    private JmxRuleCreator jmxRuleCreator;
    private String objectName;

    public MBeanProvider(String objectName, JmxRuleCreator jmxRuleCreator) {
        this.objectName = objectName;
        this.jmxRuleCreator = jmxRuleCreator;
    }

    public ResponseCollector whenCalled(String methodName, Object... parameters) {
        return new ResponseCollector(this, jmxRuleCreator, objectName, methodName, parameters);
    }

    public static class ResponseCollector {

        private MBeanProvider parent;
        private final JmxRuleCreator jmxRuleCreator;
        private String objectName;
        private final String methodName;
        private final Object[] parameters;

        public ResponseCollector(MBeanProvider mBeanProvider,
                JmxRuleCreator jmxRuleCreator,
                String objectName,
                String methodName,
                Object[] parameters) {
            parent = mBeanProvider;
            this.jmxRuleCreator = jmxRuleCreator;
            this.objectName = objectName;
            this.methodName = methodName;
            this.parameters = parameters;
        }

        public MBeanProvider thenRespondWith(Object response) {
            return thenRespondWith(() -> response);
        }

        public MBeanProvider thenRespondWith(Supplier<Object> responseSupplier) {
            jmxRuleCreator.addRule(objectName, methodName, parameters, responseSupplier);
            return parent;
        }
    }
}
