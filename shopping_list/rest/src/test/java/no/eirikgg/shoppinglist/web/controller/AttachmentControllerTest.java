package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.TestApp;
import no.eirikgg.shoppinglist.db.DbUtils;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.TestDb;
import no.eirikgg.shoppinglist.service.AttachmentService;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentControllerTest {

    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};

    private final TestDb db = TestDb.createMigratedInstance();
    private final TestConfig testConfig = new TestConfig(true, false);
    private final ObjectMapper mapper = Json.MAPPER;
    private final Javalin app;
    private final int itemListId;

    AttachmentControllerTest() throws SQLException {
        new LiquibaseMigrator().migrate(db, "db/updates/t0001-create-test-list.sql");
        try (Connection conn = db.getConnection()) {
            itemListId = new DbUtils().selectSingle(conn,
                    "select item_list_id from item_list where name = ?",
                    "unit-test-list",
                    rs -> rs.getInt("item_list_id").orElseThrow()
            ).orElseThrow();
        }
        app = TestApp.create(db);
    }

    @AfterEach
    void closeDb() {
        db.close();
    }

    @Test
    void anImage_isStoredShownAndListedOnTheItem() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int itemId = createItem(client, "milk");

            HttpResponse<String> upload = upload(client, itemId, "photo.jpg", "image/jpeg", IMAGE);
            assertEquals(201, upload.statusCode());
            ItemController.AttachmentResponse attachment =
                    mapper.readValue(upload.body(), ItemController.AttachmentResponse.class);
            assertEquals("photo.jpg", attachment.fileName());
            assertEquals("image/jpeg", attachment.contentType());
            assertEquals(IMAGE.length, attachment.size());

            HttpResponse<byte[]> file = download(client, attachment.id(), "");
            assertEquals(200, file.statusCode());
            assertArrayEquals(IMAGE, file.body());
            assertEquals("image/jpeg", file.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(file.headers().firstValue("Content-Disposition").orElseThrow().startsWith("inline;"));
            assertEquals("nosniff", file.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertTrue(file.headers().firstValue("Content-Security-Policy").orElseThrow().contains("sandbox"));

            HttpResponse<byte[]> asDownload = download(client, attachment.id(), "?download=true");
            assertTrue(asDownload.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;"));

            ItemController.GetItemResponse item = getItem(client, itemId);
            assertEquals(List.of(attachment), item.attachments());
        });
    }

    // HTML and SVG can hold script, so they are only ever downloaded.
    @Test
    void htmlAndSvg_areNeverShownInThePage() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int itemId = createItem(client, "milk");
            for (String[] file : new String[][] {{"page.html", "text/html"}, {"logo.svg", "image/svg+xml"}}) {
                byte[] content = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
                int id = mapper.readValue(
                        upload(client, itemId, file[0], file[1], content).body(),
                        ItemController.AttachmentResponse.class
                ).id();

                HttpResponse<byte[]> served = download(client, id, "");
                assertEquals("application/octet-stream", served.headers().firstValue("Content-Type").orElseThrow());
                assertTrue(served.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;"));
            }
        });
    }

    @Test
    void aFileOverTheLimit_isRefused_andNothingIsKept() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int itemId = createItem(client, "milk");
            byte[] tooLarge = new byte[(int) AttachmentService.MAX_FILE_BYTES + 1];

            HttpResponse<String> upload = upload(client, itemId, "big.bin", "application/octet-stream", tooLarge);
            assertEquals(413, upload.statusCode());
            assertTrue(upload.body().contains("FILE_TOO_LARGE"));
            assertEquals(List.of(), filesOnDisk());
            assertEquals(List.of(), getItem(client, itemId).attachments());
        });
    }

    @Test
    void deleting_removesTheFile() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int itemId = createItem(client, "milk");
            int id = mapper.readValue(
                    upload(client, itemId, "photo.jpg", "image/jpeg", IMAGE).body(),
                    ItemController.AttachmentResponse.class
            ).id();
            assertEquals(1, filesOnDisk().size());

            assertEquals(200, send(client, "DELETE", AttachmentController.PATH_ATTACHMENT, "{\"id\":" + id + "}"));
            assertEquals(404, download(client, id, "").statusCode());
            assertEquals(List.of(), filesOnDisk());
            assertEquals(List.of(), getItem(client, itemId).attachments());
        });
    }

    // The file is stored under a random name; the name sent is only shown.
    @Test
    void aNameWithFolders_cannotPlaceTheFileOutsideTheFolder() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int itemId = createItem(client, "milk");
            ItemController.AttachmentResponse attachment = mapper.readValue(
                    upload(client, itemId, "../../evil.txt", "text/plain", IMAGE).body(),
                    ItemController.AttachmentResponse.class
            );

            assertEquals("evil.txt", attachment.fileName());
            List<Path> files = filesOnDisk();
            assertEquals(1, files.size());
            assertEquals(db.getFilesFolder(), files.get(0).getParent());
            assertTrue(Files.notExists(db.getFilesFolder().resolve("../../evil.txt").normalize()));
        });
    }

    @Test
    void uploadingToAMissingItem_isNotFound() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            assertEquals(404, upload(client, 999_999, "photo.jpg", "image/jpeg", IMAGE).statusCode());
        });
    }

    // Files of deleted items, and uploads that never finished, are removed
    // when the add-on starts.
    @Test
    void unusedFiles_areRemovedAtStartup() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int keptItem = createItem(client, "kept");
            int deletedItem = createItem(client, "deleted");
            upload(client, keptItem, "kept.jpg", "image/jpeg", IMAGE);
            upload(client, deletedItem, "gone.jpg", "image/jpeg", IMAGE);
            Files.writeString(db.getFilesFolder().resolve("unfinished.part"), "half");
            assertEquals(200, send(client, "DELETE", ItemController.PATH_ITEM, "{\"id\":" + deletedItem + "}"));

            int removed = new AttachmentService(db, db.getFilesFolder()).removeUnusedFiles();
            assertEquals(2, removed);
            assertEquals(1, filesOnDisk().size());
            int keptId = getItem(client, keptItem).attachments().get(0).id();
            assertEquals(200, download(client, keptId, "").statusCode());
        });
    }

    @Test
    void contentDisposition_keepsNamesOutsideAscii() {
        assertEquals(
                "attachment; filename=\"kvittering __.pdf\"; filename*=UTF-8''kvittering%20%C3%A6%C3%B8.pdf",
                AttachmentController.contentDisposition(false, "kvittering æø.pdf")
        );
        assertEquals(
                "inline; filename=\"a_b_.jpg\"; filename*=UTF-8''a%22b%5C.jpg",
                AttachmentController.contentDisposition(true, "a\"b\\.jpg")
        );
    }


    private List<Path> filesOnDisk() throws IOException {
        if (Files.notExists(db.getFilesFolder())) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(db.getFilesFolder())) {
            return files.toList();
        }
    }

    private int createItem(HttpClient client, String name) throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(new ItemController.PostItemRequest(itemListId, name, null, null))
                ))
                .build();
        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());
        return mapper.readValue(response.body(), ItemController.PostItemResponse.class).id();
    }

    private ItemController.GetItemResponse getItem(HttpClient client, int itemId)
            throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + ItemController.PATH_ITEM))
                .method("GET", HttpRequest.BodyPublishers.ofString("{\"id\":" + itemId + "}"))
                .build();
        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return mapper.readValue(response.body(), ItemController.GetItemResponse.class);
    }

    private int send(HttpClient client, String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + path))
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }

    private HttpResponse<byte[]> download(HttpClient client, int attachmentId, String query)
            throws IOException, InterruptedException {
        String path = AttachmentController.PATH_ATTACHMENT_FILE.replace("{id}", Integer.toString(attachmentId));
        HttpRequest request = TestApp.asUser(HttpRequest.newBuilder())
                .uri(URI.create(client.getOrigin() + path + query))
                .GET()
                .build();
        return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    // A multipart form, as the web app sends it: the item id and one file.
    private HttpResponse<String> upload(
            HttpClient client, int itemId, String fileName, String contentType, byte[] content
    ) throws IOException, InterruptedException {
        String boundary = "test-boundary-" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + AttachmentController.FORM_ITEM_ID + "\"\r\n\r\n"
                + itemId + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + AttachmentController.FORM_FILE + "\"; filename=\""
                + fileName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(client.getOrigin() + AttachmentController.PATH_ATTACHMENT))
                .header(IngressFilter.HEADER_USER_ID, TestApp.USER_ID)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
