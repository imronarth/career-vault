package id.imron.careervault.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Errors raised before a controller runs only behave realistically in a real servlet container. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:errorserver;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "APP_ADMIN_USERNAME=owner",
        "APP_ADMIN_PASSWORD=test-only-password",
        "spring.servlet.multipart.max-file-size=1MB",
        "spring.servlet.multipart.max-request-size=2MB"})
@ActiveProfiles("test")
class ErrorPageServerTest {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        String form = "username=owner&password=test-only-password&_csrf="
                + URLEncoder.encode(csrfFrom("/login"), StandardCharsets.UTF_8);
        HttpResponse<String> response = send(request("/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form)));
        assertThat(response.headers().firstValue("location")).hasValueSatisfying(
                location -> assertThat(location).endsWith("/dashboard"));
        token = csrfFrom("/documents/upload");
    }

    // Over the file limit, over the request limit, and far over Tomcat's default 2 MB swallow size.
    @ParameterizedTest
    @ValueSource(ints = {1536 * 1024, 3 * 1024 * 1024, 20 * 1024 * 1024})
    void oversizeUploadShowsThe413Page(int size) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--B\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + token + "\r\n"
                + "--B\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new byte[size]);
        body.write("\r\n--B--\r\n".getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> response = send(request("/documents/upload")
                .header("Content-Type", "multipart/form-data; boundary=B")
                .POST(BodyPublishers.ofByteArray(body.toByteArray())));

        assertErrorPage(response, 413, "The upload is too large.");
    }

    @Test
    void rejectedCsrfTokenShowsThe403Page() throws Exception {
        HttpResponse<String> response = send(request("/jobs").POST(BodyPublishers.noBody()));

        assertErrorPage(response, 403, "Reload the page");
    }

    @Test
    void unknownUrlShowsThe404Page() throws Exception {
        assertErrorPage(send(request("/css/missing.css")), 404, "does not exist");
    }

    private static void assertErrorPage(HttpResponse<String> response, int status, String message) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).contains("empty-header\">" + status + "<").contains(message);
    }

    private String csrfFrom(String path) throws Exception {
        Matcher matcher = CSRF.matcher(send(request(path)).body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "text/html");
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
