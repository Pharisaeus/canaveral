package pl.codewise.samples.spring.it;

import org.junit.jupiter.api.extension.ExtendWith;
import pl.codewise.canaveral.core.bean.inject.InjectMock;
import pl.codewise.canaveral.core.bean.inject.InjectTestBean;
import pl.codewise.canaveral.core.runtime.ConfigureRunnerWith;
import pl.codewise.canaveral.mock.http.HttpNoDepsMockProvider;
import pl.codewise.canaveral.runner.junit5.JUnit5CanaveralRunner;
import pl.codewise.samples.spring.client.RestClient;
import pl.codewise.samples.spring.it.configuration.BinaryMockServer;
import pl.codewise.samples.spring.it.configuration.SampleAppRunnerConfiguration;

@ExtendWith(JUnit5CanaveralRunner.class)
@ConfigureRunnerWith(configuration = SampleAppRunnerConfiguration.class)
abstract class BaseIT {

    @InjectMock
    HttpNoDepsMockProvider httpMockProvider;

    @InjectTestBean
    RestClient restClient;

    @InjectMock
    BinaryMockServer binaryMockServer;




}
