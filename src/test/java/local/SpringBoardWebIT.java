package local;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Exercises the packaged WAR, JSP compiler, servlet filters, H2 SQL and physical files over HTTP. */
public class SpringBoardWebIT {
    @ClassRule public static final TemporaryFolder temporary = new TemporaryFolder();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CSRF = Pattern.compile("<meta\\s+name=\"csrf-token\"\\s+content=\"([A-Za-z0-9_-]{43})\"");
    private static final Pattern ARTICLE = Pattern.compile("class=\"article-link\"\\s+href=\"[^\"]*/board/get\\?bno=(\\d+)");
    private static final Pattern REPLY_COUNT = Pattern.compile("id=\"reply-count\"[^>]*>\\s*(\\d+)\\s*</span>");
    private static final Map<String, String> originalProperties = new LinkedHashMap<>();
    private static BoardServer server;
    private static String base;
    private static Path uploads;
    private HttpClient client;
    private String token;

    @BeforeClass
    public static void startApplication() throws Exception {
        Path root = temporary.newFolder("application").toPath();
        uploads = root.resolve("uploads");
        Path database = Files.createDirectories(root.resolve("database")).resolve("spring-board");
        rememberAndSet("springboard.db.profile", "h2");
        rememberAndSet("springboard.db.url", "jdbc:h2:file:" + database.toString().replace('\\', '/')
                + ";MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_ON_EXIT=FALSE");
        rememberAndSet("springboard.db.user", "sa");
        rememberAndSet("springboard.db.password", "");
        rememberAndSet("springboard.dataDir", root.resolve("database").toString());
        rememberAndSet("springboard.uploadDir", uploads.toString());
        try {
            server = BoardServer.start(Path.of("target/spring-board.war").toFile(), root.resolve("runtime").toFile(), 0);
            base = "http://127.0.0.1:" + server.getPort() + "/spring-board";
        } catch (Exception exception) {
            restoreProperties();
            throw exception;
        }
    }

    @AfterClass
    public static void stopApplication() throws Exception {
        try {
            if (server != null) server.close();
        } finally {
            restoreProperties();
        }
    }

    @Before
    public void openSession() throws Exception {
        client = newClient();
        HttpResponse<byte[]> response = get("/board/register");
        status(200, response);
        token = csrf(text(response));
        assertTrue("Session cookie is created", response.headers().allValues("Set-Cookie").stream()
                .anyMatch(cookie -> cookie.contains("JSESSIONID=") && cookie.toLowerCase().contains("httponly")));
    }

    @Test
    public void koreanBoardCrudEscapesMarkupAndPreservesSearchParameters() throws Exception {
        String marker = marker();
        String title = "한국어 " + marker + " <b>제목</b>";
        String content = "첫 줄\n두 번째 줄 <script>window.bad=true;</script>";
        long bno = createBoard(title, content, "한글 작성자", Map.of());

        HttpResponse<byte[]> detail = get("/board/get?bno=" + bno);
        status(200, detail);
        assertTrue(text(detail).contains(escape(title)));
        assertTrue(text(detail).contains(escape(content)));
        assertFalse(text(detail).contains("<script>window.bad=true;</script>"));

        String updatedTitle = "수정된 " + marker;
        HttpResponse<byte[]> changed = form("/board/modify", fields(
                "bno", bno, "title", updatedTitle, "content", "수정된 한글 내용", "writer", "변조된 작성자",
                "pageNum", 2, "amount", 2, "type", "T", "keyword", marker), true);
        status(302, changed);
        String location = changed.headers().firstValue("Location").orElseThrow();
        assertTrue(location.contains("pageNum=2"));
        assertTrue(location.contains("amount=2"));
        assertTrue(location.contains("keyword=" + marker));
        HttpResponse<byte[]> updated = get("/board/get?bno=" + bno);
        status(200, updated);
        assertTrue(text(updated).contains(updatedTitle));
        assertTrue(text(updated).contains("수정된 한글 내용"));
        assertTrue(text(updated).contains("한글 작성자"));
        assertFalse(text(updated).contains("변조된 작성자"));

        HttpResponse<byte[]> results = get(search(marker, 1, 10));
        status(200, results);
        assertEquals(List.of(bno), articleIds(text(results)));
        status(302, form("/board/remove", fields("bno", bno), true));
        status(404, get("/board/get?bno=" + bno));
        assertTrue(articleIds(text(get(search(marker, 1, 10)))).isEmpty());
    }

    @Test
    public void filteredPaginationUsesStableNewestFirstOrderAndClampsHugePages() throws Exception {
        String marker = marker();
        long first = createBoard(marker + " 첫 번째", "검색 페이징 내용", "작성자", Map.of());
        long second = createBoard(marker + " 두 번째", "검색 페이징 내용", "작성자", Map.of());
        long third = createBoard(marker + " 세 번째", "검색 페이징 내용", "작성자", Map.of());
        assertEquals(List.of(third, second), articleIds(text(get(search(marker, 1, 2)))));
        assertEquals(List.of(first), articleIds(text(get(search(marker, 2, 2)))));
        HttpResponse<byte[]> huge = get(search(marker, 1_000_000_000, 2));
        status(200, huge);
        assertEquals(List.of(first), articleIds(text(huge)));
        assertTrue(text(huge).contains("aria-current=\"page\">2</span>"));
    }

    @Test
    public void replyCrudUpdatesBoardCountAndKeepsTheOriginalAuthor() throws Exception {
        long bno = createBoard(marker() + " 댓글", "댓글 검증 글", "원글 작성자", Map.of());
        status(200, json("POST", "/replies/new", Map.of("bno", bno, "reply", "첫 한글 댓글", "replyer", "댓글 작성자")));
        JsonNode page = jsonBody(get("/replies/pages/" + bno + "/1"));
        assertEquals(1, page.get("replyCnt").asInt());
        assertEquals("첫 한글 댓글", page.get("list").get(0).get("reply").asText());
        long rno = page.get("list").get(0).get("rno").asLong();
        assertEquals(1, boardReplyCount(bno));

        status(200, json("PUT", "/replies/" + rno, Map.of("reply", "고친 댓글", "replyer", "변조 작성자", "bno", bno + 1)));
        JsonNode reply = jsonBody(get("/replies/" + rno));
        assertEquals("고친 댓글", reply.get("reply").asText());
        assertEquals("댓글 작성자", reply.get("replyer").asText());
        assertEquals(bno, reply.get("bno").asLong());
        status(404, json("POST", "/replies/new", Map.of("bno", Long.MAX_VALUE, "reply", "없는 글", "replyer", "작성자")));
        assertEquals(1, boardReplyCount(bno));

        status(200, json("DELETE", "/replies/" + rno, null));
        status(404, get("/replies/" + rno));
        assertEquals(0, boardReplyCount(bno));
        assertEquals(0, jsonBody(get("/replies/pages/" + bno + "/1")).get("replyCnt").asInt());
    }

    @Test
    public void uploadedAttachmentsPersistDownloadAndDisappearWithTheBoard() throws Exception {
        byte[] document = "UTF-8 한글 첨부파일\n두 번째 줄".getBytes(StandardCharsets.UTF_8);
        byte[] png = image();
        HttpResponse<byte[]> upload = upload(List.of(new Upload("한글자료.txt", document, "text/plain"),
                new Upload("작은이미지.png", png, "image/png")));
        JsonNode metadata = jsonBody(upload);
        assertEquals(2, metadata.size());
        JsonNode textFile = metadata.get(0), imageFile = metadata.get(1);
        assertFalse(textFile.get("image").asBoolean());
        assertTrue(imageFile.get("image").asBoolean());
        String documentPath = storedPath(textFile, false);
        String imagePath = storedPath(imageFile, false);
        String thumbnailPath = storedPath(imageFile, true);
        assertTrue(Files.isRegularFile(uploads.resolve(documentPath)));
        assertTrue(Files.isRegularFile(uploads.resolve(imagePath)));
        assertTrue(Files.isRegularFile(uploads.resolve(thumbnailPath)));

        Map<String, String> attachmentFields = new LinkedHashMap<>();
        addAttachment(attachmentFields, 0, textFile);
        addAttachment(attachmentFields, 1, imageFile);
        long bno = createBoard(marker() + " 첨부", "첨부 저장과 삭제", "작성자", attachmentFields);
        JsonNode saved = jsonBody(get("/board/getAttachList?bno=" + bno));
        assertEquals(2, saved.size());
        assertTrue(hasAttachment(saved, textFile.get("uuid").asText(), false));
        assertTrue(hasAttachment(saved, imageFile.get("uuid").asText(), true));
        HttpResponse<byte[]> downloaded = get("/download?fileName=" + encode(documentPath));
        status(200, downloaded);
        assertArrayEquals(document, downloaded.body());
        assertTrue(downloaded.headers().firstValue("Content-Disposition").orElseThrow().contains("filename*=UTF-8''"));
        assertEquals("nosniff", downloaded.headers().firstValue("X-Content-Type-Options").orElseThrow());
        HttpResponse<byte[]> displayed = get("/display?fileName=" + encode(thumbnailPath));
        status(200, displayed);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(displayed.body())));
        status(403, form("/deleteFile", fields("fileName", documentPath), true));
        assertTrue(Files.exists(uploads.resolve(documentPath)));

        status(200, json("POST", "/replies/new", Map.of("bno", bno, "reply", "삭제할 자식 댓글", "replyer", "작성자")));
        long rno = jsonBody(get("/replies/pages/" + bno + "/1")).get("list").get(0).get("rno").asLong();
        status(302, form("/board/remove", fields("bno", bno), true));
        assertFalse(Files.exists(uploads.resolve(documentPath)));
        assertFalse(Files.exists(uploads.resolve(imagePath)));
        assertFalse(Files.exists(uploads.resolve(thumbnailPath)));
        status(404, get("/replies/" + rno));
        status(404, get("/download?fileName=" + encode(documentPath)));
        status(404, get("/board/getAttachList?bno=" + bno));
    }

    @Test
    public void anotherSessionCannotClaimAPendingUpload() throws Exception {
        JsonNode uploaded = jsonBody(upload(List.of(new Upload("owner.txt", "owner".getBytes(StandardCharsets.UTF_8), "text/plain")))).get(0);
        Map<String, String> attachmentFields = new LinkedHashMap<>();
        addAttachment(attachmentFields, 0, uploaded);
        HttpClient owner = client;
        String ownerToken = token;
        client = newClient();
        token = csrf(text(get("/board/register")));
        Map<String, String> attempt = fields("title", marker() + " 소유권", "content", "내용", "writer", "다른 세션");
        attempt.putAll(attachmentFields);
        status(400, form("/board/register", attempt, true));
        client = owner;
        token = ownerToken;
        long bno = createBoard(marker() + " 소유자", "내용", "업로드 소유자", attachmentFields);
        assertEquals(1, jsonBody(get("/board/getAttachList?bno=" + bno)).size());
        status(302, form("/board/remove", fields("bno", bno), true));
        assertFalse(Files.exists(uploads.resolve(storedPath(uploaded, false))));
    }

    @Test
    public void unsafeRequestsNeedTheSessionTokenAndInvalidHtmlInputUsesTheErrorPage() throws Exception {
        Map<String, String> valid = fields("title", marker(), "content", "내용", "writer", "작성자");
        HttpResponse<byte[]> missing = form("/board/register", valid, false);
        status(403, missing);
        assertTrue(text(missing).contains("페이지를 새로고침해 주세요"));
        HttpResponse<byte[]> invalid = body("POST", "/board/register", "application/x-www-form-urlencoded",
                encodedForm(valid).getBytes(StandardCharsets.UTF_8), "invalid-token");
        status(403, invalid);
        Map<String, String> blank = fields("title", " ", "content", "내용", "writer", "작성자");
        HttpResponse<byte[]> badInput = form("/board/register", blank, true);
        status(400, badInput);
        assertTrue(badInput.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(text(badInput).contains("입력 내용을 확인해 주세요"));
        assertFalse(text(badInput).contains("BadRequestException"));
        assertFalse(text(badInput).contains("org.springframework"));
        status(400, get("/board/get?bno=invalid"));
        status(400, get("/board/list?type=DROP&keyword=anything"));
        status(400, get("/download?fileName=" + encode("../private.txt")));
    }

    private long createBoard(String title, String content, String writer, Map<String, String> extra) throws Exception {
        Map<String, String> values = fields("title", title, "content", content, "writer", writer);
        values.putAll(extra);
        HttpResponse<byte[]> created = form("/board/register", values, true);
        status(302, created);
        assertTrue(created.headers().firstValue("Location").orElseThrow().contains("/board/list"));
        HttpResponse<byte[]> listing = get("/board/list");
        status(200, listing);
        assertTrue(text(listing).contains(escape(title)));
        List<Long> ids = articleIds(text(listing));
        assertFalse(ids.isEmpty());
        return ids.get(0);
    }

    private int boardReplyCount(long bno) throws Exception {
        HttpResponse<byte[]> response = get("/board/get?bno=" + bno);
        status(200, response);
        Matcher count = REPLY_COUNT.matcher(text(response));
        assertTrue("Board JSP contains the stored reply count", count.find());
        return Integer.parseInt(count.group(1));
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
    private HttpResponse<byte[]> form(String path, Map<String, String> values, boolean includeToken) throws Exception {
        return body("POST", path, "application/x-www-form-urlencoded;charset=UTF-8",
                encodedForm(values).getBytes(StandardCharsets.UTF_8), includeToken ? token : null);
    }
    private HttpResponse<byte[]> json(String method, String path, Object value) throws Exception {
        return body(method, path, "application/json;charset=UTF-8",
                value == null ? new byte[0] : JSON.writeValueAsBytes(value), token);
    }
    private HttpResponse<byte[]> body(String method, String path, String contentType, byte[] bytes, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofByteArray(bytes));
        if (csrf != null) request.header("X-CSRF-Token", csrf);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private HttpResponse<byte[]> upload(List<Upload> files) throws Exception {
        String boundary = "SpringBoard" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Upload file : files) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"uploadFile\"; filename=\"" + file.fileName()
                    + "\"\r\nContent-Type: " + file.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(file.bytes());
            body.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return body("POST", "/uploadAjaxAction", "multipart/form-data; boundary=" + boundary, body.toByteArray(), token);
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private static JsonNode jsonBody(HttpResponse<byte[]> response) {
        status(200, response);
        return JSON.readTree(response.body());
    }
    private static String csrf(String html) {
        Matcher match = CSRF.matcher(html);
        assertTrue("JSP publishes a session CSRF token", match.find());
        return match.group(1);
    }
    private static List<Long> articleIds(String html) {
        List<Long> result = new ArrayList<>();
        Matcher links = ARTICLE.matcher(html);
        while (links.find()) result.add(Long.parseLong(links.group(1)));
        return result;
    }
    private static void addAttachment(Map<String, String> fields, int index, JsonNode metadata) {
        String prefix = "attachList[" + index + "].";
        fields.put(prefix + "uuid", metadata.get("uuid").asText());
        fields.put(prefix + "uploadPath", metadata.get("uploadPath").asText());
        fields.put(prefix + "fileName", metadata.get("fileName").asText());
        fields.put(prefix + "fileType", String.valueOf(metadata.get("image").asBoolean()));
    }
    private static boolean hasAttachment(JsonNode array, String uuid, boolean image) {
        for (JsonNode item : array) {
            if (uuid.equals(item.get("uuid").asText()) && image == item.get("fileType").asBoolean()) return true;
        }
        return false;
    }
    private static String storedPath(JsonNode metadata, boolean thumbnail) {
        return metadata.get("uploadPath").asText().replace('\\', '/') + "/" + (thumbnail ? "s_" : "")
                + metadata.get("uuid").asText() + "_" + metadata.get("fileName").asText();
    }
    private static byte[] image() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB), "png", bytes));
        return bytes.toByteArray();
    }
    private static String search(String keyword, int page, int amount) {
        return "/board/list?type=T&keyword=" + encode(keyword) + "&pageNum=" + page + "&amount=" + amount;
    }
    private static String marker() { return "web-" + UUID.randomUUID().toString().substring(0, 8); }
    private static Map<String, String> fields(Object... entries) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) result.put(entries[index].toString(), entries[index + 1].toString());
        return result;
    }
    private static String encodedForm(Map<String, String> fields) {
        return fields.entrySet().stream().map(item -> encode(item.getKey()) + "=" + encode(item.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&#034;").replace("'", "&#039;");
    }
    private static String text(HttpResponse<byte[]> response) { return new String(response.body(), StandardCharsets.UTF_8); }
    private static void status(int expected, HttpResponse<byte[]> response) {
        String body = text(response);
        assertEquals("HTTP response: " + body.substring(0, Math.min(1200, body.length())), expected, response.statusCode());
    }
    private static void rememberAndSet(String name, String value) {
        originalProperties.put(name, System.getProperty(name));
        if (value == null) System.clearProperty(name); else System.setProperty(name, value);
    }
    private static void restoreProperties() {
        originalProperties.forEach((name, value) -> {
            if (value == null) System.clearProperty(name); else System.setProperty(name, value);
        });
        originalProperties.clear();
    }
    private record Upload(String fileName, byte[] bytes, String contentType) { }
}
