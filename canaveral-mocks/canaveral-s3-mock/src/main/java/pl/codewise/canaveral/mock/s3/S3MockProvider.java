package pl.codewise.canaveral.mock.s3;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.codewise.canaveral.core.mock.MockConfig;
import pl.codewise.canaveral.core.mock.MockProvider;
import pl.codewise.canaveral.core.runtime.RunnerContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.io.ByteStreams.toByteArray;
import static java.util.Collections.emptySet;

public class S3MockProvider implements MockProvider {

    private static final Logger log = LoggerFactory.getLogger(S3MockProvider.class);

    private final S3MockConfig s3MockConfig;
    private final String mockName;
    private int port = 0;
    private S3Mock s3MockServer;

    private S3MockProvider(S3MockConfig s3MockConfig, String mockName) {
        this.s3MockConfig = s3MockConfig;
        this.mockName = mockName;
    }

    public static S3MockConfig newConfig() {
        return new S3MockConfig();
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getHost() {
        return s3MockConfig.host;
    }

    @Override
    public String getEndpoint() {
        checkArgument(port != 0, "Mock is not started yet!");
        return "http://" + getHost() + ":" + getPort();
    }

    @Override
    public String getMockName() {
        return mockName;
    }

    @Override
    public void start(RunnerContext context) {
        this.port = context.getFreePort();

        System.setProperty(s3MockConfig.endpointProperty, getEndpoint());

        s3MockServer = S3MockServer.start(s3MockConfig.host, port, s3MockConfig.buckets);
        loadDefaults();
    }

    @Override
    public void stop() throws Exception {
        s3MockServer.stop();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mockName", mockName)
                .toString();
    }

    public S3Mock getS3Mock() {
        checkArgument(this.s3MockServer != null, "Mock is not started yet!");
        return this.s3MockServer;
    }

    public void resetToDefaults() {
        s3MockServer.clean();
        loadDefaults();
    }

    private void loadDefaults() {
        for (S3Entry entry : s3MockConfig.entries) {
            try (InputStream is = getClass().getResourceAsStream(entry.getPathToResource())) {
                s3MockServer.put(entry.bucket, entry.getFileName(), toByteArray(is));
            } catch (IOException e) {
                log.error("Could not load " + entry, e);
            }
        }
    }

    public static class S3MockConfig implements MockConfig<S3MockProvider> {

        private String endpointProperty = "aws.s3.endpoint";
        private String host = HOST;
        private Set<String> buckets = emptySet();
        private Set<S3Entry> entries = new HashSet<>();

        private S3MockConfig() {
        }

        @Override
        public S3MockProvider build(String mockName) {
            checkArgument(!isNullOrEmpty(endpointProperty), "Endpoint property cannot be empty!");
            checkArgument(!buckets.isEmpty(), "S3 buckets must be provided!");

            return new S3MockProvider(this, mockName);
        }

        public S3MockConfig registerEndpointUnder(String property) {
            endpointProperty = property;
            return this;
        }

        public S3MockConfig withHost(String host) {
            this.host = host;
            return this;
        }

        public S3MockConfig withBuckets(String... buckets) {
            this.buckets = ImmutableSet.copyOf(buckets);
            return this;
        }

        public S3MockConfig put(String bucket, String fileName, String pathToResource) {
            entries.add(new S3Entry(bucket, fileName, pathToResource));
            return this;
        }
    }

    private static class S3Entry {

        private final String bucket;
        private final String fileName;
        private final String pathToResource;

        private S3Entry(String bucket, String fileName, String pathToResource) {
            this.bucket = bucket;
            this.fileName = fileName;
            this.pathToResource = pathToResource;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            S3Entry s3Entry = (S3Entry) o;
            return Objects.equal(bucket, s3Entry.bucket) &&
                    Objects.equal(fileName, s3Entry.fileName) &&
                    Objects.equal(pathToResource, s3Entry.pathToResource);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(bucket, fileName, pathToResource);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("bucket", bucket)
                    .add("fileName", fileName)
                    .add("pathToResource", pathToResource)
                    .toString();
        }

        public String getBucket() {
            return bucket;
        }

        public String getFileName() {
            return fileName;
        }

        public String getPathToResource() {
            return pathToResource;
        }
    }
}
