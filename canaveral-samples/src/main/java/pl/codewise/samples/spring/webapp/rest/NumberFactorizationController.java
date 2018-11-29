package pl.codewise.samples.spring.webapp.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping(path = "/factorize")
public class NumberFactorizationController {

    private static final Logger log = LoggerFactory.getLogger(NumberFactorizationController.class);

    private String numbersRepoHost;
    private int numbersRepoPort;

    @Autowired
    public NumberFactorizationController(@Value("${pl.codewise.binary.endpoint}") String numbersStorageEndpoint) {
        String[] split = numbersStorageEndpoint.split(":");
        numbersRepoHost = split[0];
        numbersRepoPort = Integer.parseInt(split[1]);
    }

    @RequestMapping(method = GET, produces = {MediaType.APPLICATION_JSON_VALUE})
    public Integer factorizeNumber(@RequestParam("number") long number) throws IOException {
        List<Long> divisors = findDivisors(number);
        try (BinaryClient numberStorageClient = BinaryClient.connect(numbersRepoHost, numbersRepoPort)) {
            for (Long divisor : divisors) {
                numberStorageClient.storeNumber(divisor);
            }
        }
        log.info("Found #{} divisors for {}: {}", divisors.size(), number, divisors);
        return divisors.size();
    }

    private List<Long> findDivisors(long number) {
        List<Long> divisors = new ArrayList<>();
        for (long n = 2; n <= Math.sqrt(number); ) {
            if (number % n == 0) {
                divisors.add(n);
                number = number / n;
            } else {
                n++;
            }
        }
        divisors.add(number);

        return divisors;
    }

    static class BinaryClient implements AutoCloseable {

        private final Socket socket;

        private BinaryClient(Socket socket) {
            this.socket = socket;
        }

        static BinaryClient connect(String host, int port) throws IOException {
            Socket socket = new Socket(host, port);
            socket.setSoTimeout(5000);
            return new BinaryClient(socket);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        void storeNumber(long inputNumber) throws IOException {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeLong(inputNumber);
        }
    }
}
