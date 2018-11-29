package pl.codewise.canaveral.core.runtime;

import pl.codewise.canaveral.core.bean.inject.InjectMock;

@ConfigureRunnerWith(configuration = MockProviderAdapterConfigurationProvider.class)
public class MockProviderAdapterTestClass {

    @InjectMock
    MockProviderAdapterConfigurationProvider.DummyMockObject dummyMockObject;

    @InjectMock("adaptedMock")
    MockProviderAdapterConfigurationProvider.DummyMockObject namedDummyMockObject;
}
