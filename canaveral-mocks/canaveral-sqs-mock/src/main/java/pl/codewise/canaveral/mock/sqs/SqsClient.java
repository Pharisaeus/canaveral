package pl.codewise.canaveral.mock.sqs;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqsClient {

    private static final Logger log = LoggerFactory.getLogger(SqsClient.class);

    private final AmazonSQS client;
    private final int port;

    SqsClient(int port) {
        AWSStaticCredentialsProvider fakeCredentials =
                new AWSStaticCredentialsProvider(new BasicAWSCredentials("foo", "bar"));
        this.client = AmazonSQSClientBuilder.standard()
                .withCredentials(fakeCredentials)
                .withEndpointConfiguration(new EndpointConfiguration("http://localhost:" + port, null))
                .build();
        this.port = port;
    }

    public SqsQueueClient connect(String queueName) {
        return new SqsQueueClient(queueName, this);
    }

    public void send(String queueName, String message) {
        log.info("Writing message to queue: {}, Message: {}", queueName);
        client.sendMessage(new SendMessageRequest(getQueueUrl(queueName), message));
    }

    public int getQueueLength(String queueName) {
        GetQueueAttributesResult getQueueAttributesResult =
                client.getQueueAttributes(
                        new GetQueueAttributesRequest().withQueueUrl(getQueueUrl(queueName))
                                .withAttributeNames("ApproximateNumberOfMessages"));
        String queueSize = getQueueAttributesResult.getAttributes().get("ApproximateNumberOfMessages");
        return Integer.parseInt(queueSize);
    }

    public SqsQueueClient createQueue(String queueName) {
        client.createQueue(new CreateQueueRequest(queueName));
        return new SqsQueueClient(queueName, this);
    }

    public String getQueueUrl(String queueName) {
        return "http://localhost:" + port + "/queue/" + queueName;
    }

    public Message getMessage(String queueName) {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setQueueUrl(getQueueUrl(queueName));
        receiveMessageRequest.setWaitTimeSeconds(1);
        receiveMessageRequest.setMaxNumberOfMessages(1);
        ReceiveMessageResult receiveMessageResult = client.receiveMessage(receiveMessageRequest);

        return Iterables.getFirst(receiveMessageResult.getMessages(), null);
    }
}

