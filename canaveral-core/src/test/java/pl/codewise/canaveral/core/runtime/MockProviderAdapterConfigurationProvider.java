package pl.codewise.canaveral.core.runtime;

import pl.codewise.canaveral.core.mock.MockProvider;
import pl.codewise.canaveral.core.mock.MockProviderAdapter;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class MockProviderAdapterConfigurationProvider implements RunnerConfigurationProvider {

    protected static MockProviderAdapter<DummyMockObject> dummyMockWrapper;

    @Override
    public RunnerConfiguration configure() {
        return RunnerConfiguration.builder()
                .withMocks(RunnerConfiguration.mocksBuilder()
                        .provideMock("adaptedMock", this::provider))
                .build();
    }

    private MockProvider provider(String name) {
        dummyMockWrapper = new MockProviderAdapter<DummyMockObject>(name, new DummyMockObject()) {
            protected int initialize(RunnerContext context) {
                providedMock().add("Started");
                providedMock().add(getMockName());
                return 0;
            }

            @Override
            public void stop() {
                providedMock().add("Stopped: " + providedMock().getList());
            }
        };
        return dummyMockWrapper;
    }

    public static class DummyMockObject {

        private final List<String> list = new ArrayList<>();

        public void add(String item) {
            list.add(item);
        }

        public List<String> getList() {
            return list;
        }
    }
}
