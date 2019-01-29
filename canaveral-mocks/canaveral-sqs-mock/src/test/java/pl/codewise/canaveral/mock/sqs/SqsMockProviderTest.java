package pl.codewise.canaveral.mock.sqs;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import pl.codewise.canaveral.core.runtime.RunnerContext;
import pl.codewise.canaveral.mock.sqs.SqsMockProvider.QueueConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SqsMockProviderTest {

    private static final String MOCK_PORT_PROPERTY = "aws.sqs.port";
    private static final String MOCK_ENDPOINT_PROPERTY = "aws.sqs.endpoint";
    private static final String QUEUE_ENDPOINT_PROPERTY = "aws.sqs.queueName";
    private static final String QUEUE_NAME = "queue-name";
    private static final String MOCK_NAME = "sqs-mock";

    @Mock
    private RunnerContext runnerContext;

    private QueueConfig queueConfig = QueueConfig.builder()
            .withQueueName(QUEUE_NAME)
            .withProperty(QUEUE_ENDPOINT_PROPERTY)
            .build();
    private SqsMockProvider sqsMockProvider = SqsMockProvider.newConfig()
            .registerEndpointUnder(MOCK_ENDPOINT_PROPERTY)
            .registerPortUnder(MOCK_PORT_PROPERTY)
            .createQueue(queueConfig)
            .build(MOCK_NAME);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(runnerContext.getFreePort()).thenCallRealMethod();

        sqsMockProvider.start(runnerContext);
    }

    @Test
    void shouldRegisterSqsMockPortProperty() {
        // when
        int mockPort = Integer.valueOf(System.getProperty(MOCK_PORT_PROPERTY));

        // then
        assertThat(mockPort).isEqualTo(sqsMockProvider.getPort());
    }

    @Test
    void shouldRegisterSqsMockEndpointProperty() {
        // when
        String mockEndpoint = System.getProperty(MOCK_ENDPOINT_PROPERTY);

        // then
        assertThat(mockEndpoint)
                .isNotNull()
                .isEqualTo(sqsMockProvider.getEndpoint());
    }

    @Test
    void shouldCreateQueueAndRegisterEndpointUnderProperty() {
        // given
        String queueName = getQueueUrl(sqsMockProvider, queueConfig);

        // when
        String queueEndpoint = System.getProperty(QUEUE_ENDPOINT_PROPERTY);

        // then
        assertThat(queueEndpoint)
                .isNotNull()
                .isEqualTo(queueName);
    }

    @Test
    void shouldReturnMockName() {
        // when
        String mockName = sqsMockProvider.getMockName();

        // then
        assertThat(mockName).isEqualTo(MOCK_NAME);
    }

    @Test
    void shouldReceiveMessage() {
        // given
        String endpoint = sqsMockProvider.getEndpoint();
        String queueUrl = getQueueUrl(sqsMockProvider, queueConfig);
        AmazonSQS amazonSqsClient = createAmazonSqsClient(endpoint);
        String messageBody = "message";

        // when
        SendMessageResult sendResult = amazonSqsClient.sendMessage(queueUrl, messageBody);

        // then
        assertThat(sendResult.getMessageId()).isNotEmpty();

        // when
        ReceiveMessageResult receiveResult = amazonSqsClient.receiveMessage(queueUrl);

        // that
        assertThat(receiveResult.getMessages()).hasSize(1);
        Message message = receiveResult.getMessages().get(0);
        assertThat(message.getBody()).isEqualTo(messageBody);
    }

    @Test
    void shouldStartAnotherSever() {
        // given
        SqsMockProvider sqsMockProvider = SqsMockProvider.newConfig()
                .registerEndpointUnder("other.property")
                .createQueue(QueueConfig.builder()
                        .withQueueName("other-queue")
                        .withProperty("other.queue.property")
                        .build())
                .build("other-name");

        // when
        sqsMockProvider.start(runnerContext);

        // then
        assertThat(sqsMockProvider.getPort()).isNotEqualTo(this.sqsMockProvider.getPort());
        assertThat(sqsMockProvider.getHost()).isEqualTo(this.sqsMockProvider.getHost());
        assertThat(sqsMockProvider.getEndpoint()).isNotEqualTo(this.sqsMockProvider.getEndpoint());
        assertThat(sqsMockProvider.getMockName()).isNotEqualTo(this.sqsMockProvider.getMockName());
    }

    private AmazonSQS createAmazonSqsClient(String endpoint) {
        AWSStaticCredentialsProvider fakeCredentials =
                new AWSStaticCredentialsProvider(new BasicAWSCredentials("foo", "bar"));
        return AmazonSQSClientBuilder.standard()
                .withCredentials(fakeCredentials)
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(endpoint, null))
                .build();
    }

    private String getQueueUrl(SqsMockProvider sqsMockProvider, QueueConfig queueConfig) {
        return sqsMockProvider.getEndpoint() + "/queue/" + queueConfig.getQueueName();
    }
}