package pl.codewise.samples.spring.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.codewise.canaveral.core.bean.inject.InjectMock;
import pl.codewise.canaveral.core.bean.inject.InjectTestBean;
import pl.codewise.samples.spring.client.RestClient;
import pl.codewise.samples.spring.it.configuration.BinaryMockServer;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryIT extends BaseIT {

    @InjectTestBean
    RestClient restClient;

    @InjectMock
    BinaryMockServer binaryMockServer;

    @BeforeEach
    public void resetMock() {
        binaryMockServer.reset();
    }

    @Test
    void shouldFactorizePrimeNumber() {
        Integer response = restClient.factorizeNumber(997);

        assertThat(response).isEqualTo(1);

        assertThat(binaryMockServer.getStoredNumbers())
                .containsExactly(997L);
    }

    @Test
    void shouldFactorizeNumberAndStoreFactorsInRepo() {
        Integer response = restClient.factorizeNumber(60);

        assertThat(response).isEqualTo(4);

        assertThat(binaryMockServer.getStoredNumbers())
                .containsExactly(2L, 2L, 3L, 5L);
    }
}
