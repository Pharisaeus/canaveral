package pl.codewise.canaveral.mock.jmx;


import com.google.common.collect.ImmutableList;
import pl.codewise.canaveral.core.mock.MockConfig;

import java.util.List;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

public class JmxMockConfig implements MockConfig<JmxMockProvider> {

    private String host = HOST;
    private String jmxPortProperty;
    private List<JmxMockRule> rules;

    @Override
    public JmxMockProvider build(String mockName) {
        return new JmxMockProvider(this, mockName);
    }

    public JmxMockConfig registerJmxPortUnder(String property) {
        checkArgument(!isNullOrEmpty(property));
        this.jmxPortProperty = property;
        return this;
    }

    public JmxMockConfig withRules(Consumer<JmxMockRuleProvider> ruleProvider) {
        JmxRuleCreator jmxRuleCreator = new JmxRuleCreator();
        JmxMockRuleProvider jmxMockRuleProvider = new JmxMockRuleProvider(jmxRuleCreator);
        ruleProvider.accept(jmxMockRuleProvider);
        rules = ImmutableList.copyOf(jmxRuleCreator.getRules());
        return this;
    }

    public String getHost() {
        return host;
    }

    public String getJmxPortProperty() {
        return jmxPortProperty;
    }

    public List<JmxMockRule> getRules() {
        return rules;
    }
}
