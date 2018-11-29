package pl.codewise.canaveral.core.mock;

import pl.codewise.canaveral.core.runtime.RunnerContext;

@FunctionalInterface
public interface MockFactory<MockType> {

    MockType create(RunnerContext runnerContext) throws Exception;
}
