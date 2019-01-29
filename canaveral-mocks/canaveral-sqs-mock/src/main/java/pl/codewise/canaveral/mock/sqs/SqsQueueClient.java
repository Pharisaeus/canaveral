package pl.codewise.canaveral.mock.sqs;

import com.amazonaws.services.sqs.model.Message;

public class SqsQueueClient {

    private final String queueName;
    private final SqsClient sqsClient;

    SqsQueueClient(String queueName, SqsClient sqsClient) {
        this.queueName = queueName;
        this.sqsClient = sqsClient;
    }

    public void send(String message) {
        sqsClient.send(queueName, message);
    }

    public int getQueueLength() {
        return sqsClient.getQueueLength(queueName);
    }

    public SqsQueueClient createQueue() {
        return sqsClient.createQueue(queueName);
    }

    public String getQueueUrl() {
        return sqsClient.getQueueUrl(queueName);
    }

    public Message getMessage() {
        return sqsClient.getMessage(queueName);
    }
}

