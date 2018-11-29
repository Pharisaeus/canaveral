package pl.codewise.canaveral.mock.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.util.List;
import java.util.Optional;

public class MockMBean implements DynamicMBean {

    private static final Logger log = LoggerFactory.getLogger(MockMBean.class);

    private final List<JmxMockRule> rules;

    public MockMBean(List<JmxMockRule> rules) {
        this.rules = rules;
    }

    public Optional<JmxMockRule> findMatchingRule(String methodName, Object[] parameters) {
        log.debug("Finding a rule matching parameters methodName={}, parameters={}", methodName, parameters);
        return rules.stream().filter(rule -> rule.getCondition().test(methodName, parameters)).findFirst();
    }

    @Override
    public Object getAttribute(String attribute)
            throws AttributeNotFoundException, MBeanException, ReflectionException {
        return "test";
    }

    @Override
    public void setAttribute(Attribute attribute)
            throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException,
            ReflectionException {
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        return new AttributeList();
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        return new AttributeList();
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature)
            throws MBeanException, ReflectionException {
        Optional<JmxMockRule> matchingRule = findMatchingRule(actionName, params);
        return matchingRule.orElseThrow(
                () -> new IllegalArgumentException("No matching rule for parameters " + actionName + " " + params))
                .getResponse().get();
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return new MBeanInfo("", "",
                new MBeanAttributeInfo[] {},
                new MBeanConstructorInfo[] {},
                new MBeanOperationInfo[] {},
                new MBeanNotificationInfo[] {});
    }
}
