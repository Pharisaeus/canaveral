package pl.codewise.canaveral.mock.http;

import java.util.Objects;

public class HttpStatusCode implements Comparable<HttpStatusCode> {

    public static final HttpStatusCode OK = new HttpStatusCode(200);
    public static final HttpStatusCode ACCEPTED = new HttpStatusCode(202);
    public static final HttpStatusCode NO_CONTENT = new HttpStatusCode(204);
    public static final HttpStatusCode MOVED_PERMANENTLY = new HttpStatusCode(301);
    public static final HttpStatusCode FOUND = new HttpStatusCode(302);
    public static final HttpStatusCode SEE_OTHER = new HttpStatusCode(303);
    public static final HttpStatusCode NOT_MODIFIED = new HttpStatusCode(304);
    public static final HttpStatusCode BAD_REQUEST = new HttpStatusCode(400);
    public static final HttpStatusCode UNAUTHORIZED = new HttpStatusCode(401);
    public static final HttpStatusCode FORBIDDEN = new HttpStatusCode(403);
    public static final HttpStatusCode NOT_FOUND = new HttpStatusCode(404);
    public static final HttpStatusCode EXPECTATION_FAILED = new HttpStatusCode(417);
    public static final HttpStatusCode INTERNAL_SERVER_ERROR = new HttpStatusCode(500);
    public static final HttpStatusCode BAD_GATEWAY = new HttpStatusCode(502);
    public static final HttpStatusCode SERVICE_UNAVAILABLE = new HttpStatusCode(503);

    public static HttpStatusCode of(int value) {
        return new HttpStatusCode(value);
    }

    private final int code;

    private HttpStatusCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public int compareTo(HttpStatusCode that) {
        return this.code - that.code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HttpStatusCode that = (HttpStatusCode) o;
        return code == that.code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}