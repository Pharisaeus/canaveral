package pl.codewise.canaveral.mock.sqs;

import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsMockServer {

    private static final Logger log = LoggerFactory.getLogger(SqsMockServer.class);

    private final int port;
    private SQSRestServer sqsServer;

    public SqsMockServer(int port) {
        this.port = port;
    }

    public void start() {
        log.info("Starting SQS mock");
        startElasticMQ();
    }

    public void stop() {
        log.info("Stopping SQS mock");
        stopElasticMQ();
    }

    public void reset() {
        log.info("Restarting SQS mock");
        stopElasticMQ();
        startElasticMQ();
    }

    public String getEndpoint() {
        return "http://localhost:" + port + "/";
    }

    public SqsClient getClient() {
        return new SqsClient(port);
    }

    private void startElasticMQ() {
        sqsServer = SQSRestServerBuilder.withPort(port).start();
        sqsServer.waitUntilStarted();
    }

    private void stopElasticMQ() {
        sqsServer.stopAndWait();
    }
}