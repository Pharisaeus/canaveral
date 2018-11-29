package pl.codewise.canaveral.mock.jmx;

public class JmxMockRuleProvider {

    private JmxRuleCreator jmxRuleCreator;

    public JmxMockRuleProvider(JmxRuleCreator jmxRuleCreator) {
        this.jmxRuleCreator = jmxRuleCreator;
    }

    public MBeanProvider withMBean(String objectName) {
        return new MBeanProvider(objectName, jmxRuleCreator);
    }
}
