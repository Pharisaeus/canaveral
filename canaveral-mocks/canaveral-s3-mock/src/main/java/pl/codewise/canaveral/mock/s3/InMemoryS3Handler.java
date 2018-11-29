package pl.codewise.canaveral.mock.s3;

import com.amazonaws.services.s3.Headers;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Comparator.comparing;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.lang3.StringUtils.defaultString;

public class InMemoryS3Handler extends AbstractHandler {

    private static final String RESPONSE_GET_KEY_NOT_FOUND =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Error>" +
                    "  <Code>NoSuchKey</Code>" +
                    "  <Message>The specified key does not exist.</Message>" +
                    "  <Key>${key}</Key>" +
                    "  <RequestId>6D57E03E89170217</RequestId>" +
                    "  <HostId>AUqRbON70RPGuk/BPsnwEUzWNOZGvEEMzglqD1pYFJVcdZR9huq97PN5v34XCt9n</HostId>" +
                    "</Error>";
    private static final String RESPONSE_NO_SUCH_BUCKET =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<Error>\n" +
                    "  <Code>NoSuchBucket</Code>\n" +
                    "  <Message>The specified bucket does not exist.</Message>\n" +
                    "  <Resource>${bucket}</Resource> \n" +
                    "  <RequestId>4442587FB7D0A2F9</RequestId>\n" +
                    "</Error>";
    private static final String RESPONSE_INTERNAL_ERROR =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<Error>\n" +
                    "  <Code>InternalError</Code>\n" +
                    "  <Message>We encountered an internal error. Please try again.</Message>\n" +
                    "  <Resource>${bucket}</Resource> \n" +
                    "  <RequestId>4442587FB7D0A2F9</RequestId>\n" +
                    "</Error>";

    private static final String RESPONSE_GET_BUCKET_LOCATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<LocationConstraint xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"></LocationConstraint>";

    private static final String RESPONSE_GET_ALL_BUCKETS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01\">\n" +
                    "  <Owner>\n" +
                    "    <ID>bcaf1ffd86f461ca5fb16fd081034f</ID>\n" +
                    "    <DisplayName>webfile</DisplayName>\n" +
                    "  </Owner>\n" +
                    "  <Buckets>\n" +
                    "    ${buckets}\n" +
                    "  </Buckets>\n" +
                    "</ListAllMyBucketsResult>";
    private static final String ELEM_BUCKET =
            "    <Bucket>\n" +
                    "      <Name>${name}</Name>\n" +
                    "      <CreationDate>${date}</CreationDate>\n" +
                    "    </Bucket>";

    private static final String RESPONSE_GET_BUCKET =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n" +
                    "  <Name>${bucket}</Name>\n" +
                    "  <Prefix>${prefix}</Prefix>\n" +
                    "  <Marker>${marker}</Marker>\n" +
                    "  <MaxKeys>${max-keys}</MaxKeys>\n" +
                    "  <IsTruncated>${is-truncated}</IsTruncated>\n" +
                    "${commonPrefixes}" +
                    "${contents}" +
                    "</ListBucketResult>";
    private static final String ELEM_CONTENTS =
            "  <Contents>\n" +
                    "    <Key>${key}</Key>\n" +
                    "    <LastModified>${last-modified}</LastModified>\n" +
                    "    <ETag>&quot;${etag}&quot;</ETag>\n" +
                    "    <Size>${size}</Size>\n" +
                    "    <StorageClass>STANDARD</StorageClass>\n" +
                    "    <Owner>\n" +
                    "      <ID>bcaf161ca5fb16fd081034f</ID>\n" +
                    "      <DisplayName>webfile</DisplayName>\n" +
                    "     </Owner>\n" +
                    "  </Contents>\n";

    private static final String ELEM_COMMON_PREFIXES =
            "  <CommonPrefixes>\n" +
                    "   <Prefix>${commonPrefix}</Prefix>\n" +
                    "  </CommonPrefixes>\n";

    private static final DateTimeFormatter DATE_TIME_FORMAT_ISO = ISODateTimeFormat.dateTime();
    private static final DateTimeFormatter DATE_TIME_FORMAT_HTTP =
            DateTimeFormat.forPattern("EE, dd MMM yyyy HH:mm:ss 'GMT'").withLocale(Locale.ENGLISH)
                    .withZone(DateTimeZone.UTC);
    private static final String SERVER_START_DATE_TIME = DATE_TIME_FORMAT_ISO.print(DateTime.now());

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryS3Handler.class);
    private static final String IPADDRESS_PATTERN =
            "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
    private static GetRequestHandler[] GET_HANDLERS = {
            InMemoryS3Handler::handleGetListBuckets,
            InMemoryS3Handler::handleGetBucketLocation,
            InMemoryS3Handler::handleGetBucket,
            InMemoryS3Handler::handleGetObject,
    };
    private final HashMapS3Storage s3MemoryStorage;
    private Pattern pattern;

    InMemoryS3Handler(HashMapS3Storage s3MemoryStorage) {
        this.s3MemoryStorage = s3MemoryStorage;
        pattern = Pattern.compile(IPADDRESS_PATTERN);
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        Matcher matcher = pattern.matcher(request.getServerName());
        String bucket, key;
        String url = URLDecoder.decode(request.getRequestURI().substring(1), "UTF-8");

        if (matcher.matches()) {
            bucket = url.split("/")[0];
            if (url.length() > bucket.length() + 1) {
                key = url.substring(bucket.length() + 1);
            } else {
                key = "";
            }
        } else {
            bucket = request.getServerName()
                    .replace("s3.amazonaws.com", "")
                    .replace("localhost", "")
                    .split("\\.")[0];

            key = url;
        }

        LOG.info("Bucket {}, handling {} request: {}", bucket, request.getMethod(), key);

        if ("PUT".equals(request.getMethod())) {
            handlePut(request, response, key, bucket);
        } else if ("GET".equals(request.getMethod())) {
            handleGet(response, key, bucket, request);
        } else if ("HEAD".equals(request.getMethod())) {
            handleHead(response, key, bucket, request);
        } else {
            response.setStatus(SC_METHOD_NOT_ALLOWED);
        }
        baseRequest.setHandled(true);
    }

    private String getBase64EncodedMD5Hash(byte[] bytes) {
        return DigestUtils.md5Hex(bytes);
    }

    private void handlePut(HttpServletRequest request, HttpServletResponse response, String key, String bucket)
            throws IOException {
        byte[] content = null;

        if (request.getHeader("x-amz-decoded-content-length") != null) {
            AWSChunkReader reader = new AWSChunkReader(request.getInputStream(), 2048);

            content = new byte[] {};
            while (reader.hasNext()) {
                reader.next(); // header - ignore
                byte[] chunk = reader.next();
                content = ArrayUtils.addAll(content, chunk);
                if (LOG.isTraceEnabled()) {
                    LOG.trace(Arrays.toString(content));
                }
            }
        }

        if (content == null) {
            content = IOUtils.toByteArray(request.getInputStream());
        }

        s3MemoryStorage.put(bucket, key, content);
        String contentMd5Hash = getBase64EncodedMD5Hash(content);
        response.addHeader(Headers.ETAG, contentMd5Hash);

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void handleGet(HttpServletResponse response, String key, String bucket, HttpServletRequest request)
            throws IOException, ServletException {

        for (GetRequestHandler handler : GET_HANDLERS) {
            try {
                if (handler.handle(this, response, key, bucket, request)) {
                    return;
                }
            } catch (IOException e) {
                throw e;
            } catch (Throwable throwable) {
                throw new ServletException(throwable);
            }
        }
        sendResponseInternalError(response);
    }

    private void handleHead(HttpServletResponse response, String key, String bucket, HttpServletRequest request)
            throws IOException {
        Preconditions.checkArgument(!isNullOrEmpty(key), "missing key");
        Preconditions.checkArgument(!isNullOrEmpty(bucket), "missing bucket");
        S3MockObject s3Object = s3MemoryStorage.get(bucket, key);
        if (s3Object == null) {
            sendResponseKeyNotFound(response, key);
            return;
        }
        String contentType =
                defaultString(emptyToNull(request.getParameter("response-content-type")), "application/octet-stream");
        String contentLanguage = emptyToNull(request.getParameter("response-content-language"));
        String expires = emptyToNull(request.getParameter("response-expires"));
        String cacheControl = emptyToNull(request.getParameter("response-cache-control"));
        String contentDisposition = emptyToNull(request.getParameter("response-content-disposition"));
        String contentEncoding = emptyToNull(request.getParameter("response-content-encoding"));
        response.setContentType(contentType);
        response.setHeader("ETag", String.format("\"%s\"", getBase64EncodedMD5Hash(s3Object.content())));
        response.setHeader("Server", "AmazonS3");
        response.setHeader("Last-Modified", DATE_TIME_FORMAT_HTTP.print(s3Object.lastModified()));
        if (contentLanguage != null) {
            response.setHeader("Content-Language", contentLanguage);
        }
        if (expires != null) {
            response.setHeader("Expires", expires);
        }
        if (cacheControl != null) {
            response.setHeader("Cache-Control", cacheControl);
        }
        if (contentDisposition != null) {
            response.setHeader("Content-Disposition", contentDisposition);
        }
        if (contentEncoding != null) {
            response.setHeader("Content-Encoding", contentEncoding);
        }
        sendResponseWithOk(response, new byte[] {});
    }

    private boolean handleGetObject(HttpServletResponse response, String key, String bucket, HttpServletRequest request)
            throws IOException {
        if (!isNullOrEmpty(key) && !isNullOrEmpty(bucket)) {
            S3MockObject s3Object = s3MemoryStorage.get(bucket, key);
            if (s3Object != null) {
                String contentType = defaultString(emptyToNull(request.getParameter("response-content-type")),
                        "application/octet-stream");
                String contentLanguage = emptyToNull(request.getParameter("response-content-language"));
                String expires = emptyToNull(request.getParameter("response-expires"));
                String cacheControl = emptyToNull(request.getParameter("response-cache-control"));
                String contentDisposition = emptyToNull(request.getParameter("response-content-disposition"));
                String contentEncoding = emptyToNull(request.getParameter("response-content-encoding"));
                response.setContentType(contentType);
                response.setHeader("ETag", String.format("\"%s\"", getBase64EncodedMD5Hash(s3Object.content())));
                response.setHeader("Server", "AmazonS3");
                response.setHeader("Last-Modified", DATE_TIME_FORMAT_HTTP.print(s3Object.lastModified()));
                if (contentLanguage != null) {
                    response.setHeader("Content-Language", contentLanguage);
                }
                if (expires != null) {
                    response.setHeader("Expires", expires);
                }
                if (cacheControl != null) {
                    response.setHeader("Cache-Control", cacheControl);
                }
                if (contentDisposition != null) {
                    response.setHeader("Content-Disposition", contentDisposition);
                }
                if (contentEncoding != null) {
                    response.setHeader("Content-Encoding", contentEncoding);
                }
                sendResponseWithOk(response, s3Object.content());
            } else {
                sendResponseKeyNotFound(response, key);
            }
            return true;
        }
        return false;
    }

    private boolean handleGetBucket(HttpServletResponse response, String key, String bucket, HttpServletRequest request)
            throws IOException {
        if (isNullOrEmpty(key) && !isNullOrEmpty(bucket)) {
            if (s3MemoryStorage.listBuckets().contains(bucket)) {
                String prefix = nullToEmpty(request.getParameter("prefix"));
                String delimeter = defaultString(emptyToNull(request.getParameter("delimiter")), "/");
                String marker = nullToEmpty(request.getParameter("marker"));

                //Amazon in client (probably) above 1.10 expects encoded signs like '+' (not URLEncoded full
                // response, because it also will encode '/')
                boolean escape = false;
                String userAgent = request.getHeader("User-Agent");
                if (StringUtils.contains(userAgent, "aws-sdk-java")) {
                    String version = userAgent.substring(userAgent.indexOf("/") + 1, userAgent.indexOf(" "));
                    String[] split = version.split("\\.");
                    if (Integer.parseInt(split[0]) > 1 || Integer.parseInt(split[1]) >= 10) {
                        escape = true;
                    }
                }
                final boolean finalEscape = escape;

                int maxKeys = Integer.parseInt(defaultString(emptyToNull(request.getParameter("max-keys")), "1000"));
                Stream<Map.Entry<String, S3MockObject>> entryStream =
                        s3MemoryStorage.listBucket(bucket).entrySet().stream()
                                .sorted(comparing(Map.Entry::getKey));
                if (!isNullOrEmpty(marker)) {
                    entryStream = entryStream.filter(e -> e.getKey().compareTo(marker) > 0);
                }
                if (!isNullOrEmpty(prefix)) {
                    entryStream = entryStream.filter(e -> e.getKey().startsWith(prefix));
                }
                List<Map.Entry<String, S3MockObject>> entries =
                        entryStream.limit(maxKeys + 1).collect(Collectors.toList());
                StringBuilder contents = new StringBuilder();
                for (int idx = 0; idx < Math.min(maxKeys, entries.size()); idx++) {
                    Map.Entry<String, S3MockObject> entry = entries.get(idx);
                    contents.append(ELEM_CONTENTS
                            .replace("${key}", encodeSpecialCharters(entry.getKey(), escape))
                            .replace("${last-modified}", DATE_TIME_FORMAT_ISO.print(entry.getValue().lastModified()))
                            .replace("${etag}", getBase64EncodedMD5Hash(entry.getValue().content()))
                            .replace("${size}", Integer.toString(entry.getValue().content().length)));
                }
                StringBuilder commonPrefixes = new StringBuilder();
                if (!isNullOrEmpty(prefix)) {
                    entries.stream()
                            .map(entry -> entry.getKey().replace(prefix, ""))
                            .map(temp -> temp.substring(0, temp.indexOf(delimeter) + 1))
                            .distinct()
                            .forEach(each -> commonPrefixes.append(ELEM_COMMON_PREFIXES
                                    .replace("${commonPrefix}", encodeSpecialCharters(prefix + each, finalEscape))));
                }
                sendResponseWithOk(response, RESPONSE_GET_BUCKET
                        .replace("${bucket}", bucket)
                        .replace("${prefix}", encodeSpecialCharters(prefix, escape))
                        .replace("${marker}", encodeSpecialCharters(marker, escape))
                        .replace("${max-keys}", Integer.toString(maxKeys))
                        .replace("${is-truncated}", Boolean.toString(entries.size() > maxKeys))
                        .replace("${commonPrefixes}", commonPrefixes.toString())
                        .replace("${contents}", contents.toString())
                        .getBytes(Charsets.UTF_8));
            } else {
                sendResponseNoSuchBucket(response, bucket);
            }
            return true;
        }
        return false;
    }

    private String encodeSpecialCharters(String s, boolean enable) {
        if (enable) {
            return s.replace("+", "%2B")
                    .replace("*", "%2A");
        }
        return s;
    }

    private boolean handleGetBucketLocation(HttpServletResponse response, String key, String bucket,
            HttpServletRequest request) throws IOException {
        if (isNullOrEmpty(key) && !isNullOrEmpty(bucket) && request.getParameterMap().get("location") != null) {
            sendResponseWithOk(response, RESPONSE_GET_BUCKET_LOCATION.getBytes(Charsets.UTF_8));
            return true;
        }
        return false;
    }

    private boolean handleGetListBuckets(HttpServletResponse response, String key, String bucket,
            HttpServletRequest request) throws IOException {
        if (isNullOrEmpty(key) && isNullOrEmpty(bucket)) {
            StringBuilder result = new StringBuilder();
            for (String bucketName : s3MemoryStorage.listBuckets()) {
                result.append(ELEM_BUCKET.replace("${name}", bucketName).replace("${date}", SERVER_START_DATE_TIME));
            }
            sendResponseWithOk(response,
                    RESPONSE_GET_ALL_BUCKETS.replace("${buckets}", result.toString()).getBytes(Charsets.UTF_8));
            return true;
        }
        return false;
    }

    private void sendResponseWithOk(HttpServletResponse response, byte[] content) throws IOException {
        response.setContentLength(content.length);
        IOUtils.write(content, response.getOutputStream());
        response.setStatus(SC_OK);
    }

    private void sendResponseKeyNotFound(HttpServletResponse response, String key) throws IOException {
        LOG.warn("Key {} not found", key);
        IOUtils.write(RESPONSE_GET_KEY_NOT_FOUND.replace("${key}", key), response.getOutputStream());
        response.setStatus(SC_NOT_FOUND);
    }

    private void sendResponseNoSuchBucket(HttpServletResponse response, String bucket) throws IOException {
        LOG.warn("Bucket {} not found", bucket);
        IOUtils.write(RESPONSE_NO_SUCH_BUCKET.replace("${bucket}", bucket), response.getOutputStream());
        response.setStatus(SC_NOT_FOUND);
    }

    private void sendResponseInternalError(HttpServletResponse response) throws IOException {
        IOUtils.write(RESPONSE_INTERNAL_ERROR, response.getOutputStream());
        response.setStatus(SC_INTERNAL_SERVER_ERROR);
    }

    @FunctionalInterface
    private interface GetRequestHandler {

        boolean handle(InMemoryS3Handler instance, HttpServletResponse response,
                       String key, String bucket,
                       HttpServletRequest request) throws Throwable;
    }
}
