package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Header;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.TestConfig;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.Main;
import no.eirikgg.shoppinglist.datamodel.ItemListMember;
import no.eirikgg.shoppinglist.db.EmbeddedPostgresDb;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.user.EmailSenderFake;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemListShareControllerTest {

    private final Javalin app;
    private final TestConfig testConfig = new TestConfig(true, false);
    private final ObjectMapper mapper = Json.MAPPER;

    private final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final String OTHER_EMAIL = "second-unit-test-user@test.com";

    public ItemListShareControllerTest() {
        EmbeddedPostgresDb db = EmbeddedPostgresDb.createMigratedInstance();
        LiquibaseMigrator migrator = new LiquibaseMigrator();
        migrator.migrate(db, "db/updates/t0001-create-test-user-and-login.sql");
        migrator.migrate(db, "db/updates/t0003-create-second-test-user.sql");
        app = Main.setupJavalinApp(db, new EmailSenderFake());
    }

    @Test
    void sharedList_canBeUsedByBoth_andLeft() {
        JavalinTest.test(app, testConfig, (server, client) -> {
            int listId = createList(client, OWNER, "shared-list");
            assertTrue(findList(client, OTHER, listId).isEmpty());

            // Only registered emails can be shared with, and case does not matter.
            assertEquals(404, send(client, OWNER, "POST", ItemController.PATH_ITEM_LIST_MEMBER,
                    new ItemController.PostItemListMemberRequest(listId, "nobody@test.com")).statusCode());
            HttpResponse<String> shared = send(client, OWNER, "POST", ItemController.PATH_ITEM_LIST_MEMBER,
                    new ItemController.PostItemListMemberRequest(listId, OTHER_EMAIL.toUpperCase()));
            assertEquals(201, shared.statusCode());
            int otherPersonId = mapper.readValue(
                    shared.body(), ItemController.ItemListMemberResponse.class
            ).personId();

            ItemController.GetItemListResponse otherView = findList(client, OTHER, listId).orElseThrow();
            assertEquals(ItemListMember.Role.MEMBER, otherView.role());
            assertEquals(2, otherView.members().size());
            assertEquals(ItemListMember.Role.OWNER, otherView.members().getFirst().role());
            assertTrue(otherView.members().stream().anyMatch(m -> m.you() && m.personId() == otherPersonId));

            // The member can add items, which the owner sees.
            assertEquals(201, send(client, OTHER, "POST", ItemController.PATH_ITEM,
                    new ItemController.PostItemRequest(listId, "added-by-member", null)).statusCode());
            assertEquals("added-by-member",
                    findList(client, OWNER, listId).orElseThrow().items().getFirst().name());

            // Only the owner can share and delete, and the owner cannot leave.
            assertEquals(401, send(client, OTHER, "POST", ItemController.PATH_ITEM_LIST_MEMBER,
                    new ItemController.PostItemListMemberRequest(listId, "unit-test-user@test.com")).statusCode());
            assertEquals(401, send(client, OTHER, "DELETE", ItemController.PATH_ITEM_LIST,
                    new ItemController.DeleteItemListRequest(listId)).statusCode());
            int ownerPersonId = otherView.members().getFirst().personId();
            assertEquals(401, send(client, OWNER, "DELETE", ItemController.PATH_ITEM_LIST_MEMBER,
                    new ItemController.DeleteItemListMemberRequest(listId, ownerPersonId)).statusCode());

            // The member leaves, and loses access.
            assertEquals(200, send(client, OTHER, "DELETE", ItemController.PATH_ITEM_LIST_MEMBER,
                    new ItemController.DeleteItemListMemberRequest(listId, otherPersonId)).statusCode());
            assertTrue(findList(client, OTHER, listId).isEmpty());
            assertEquals(404, send(client, OTHER, "POST", ItemController.PATH_ITEM,
                    new ItemController.PostItemRequest(listId, "no-longer-allowed", null)).statusCode());

            // Shared again, then removed by the owner.
            send(client, OWNER, "POST", ItemController.PATH_ITEM_LIST_MEMBER,
                    new ItemController.PostItemListMemberRequest(listId, OTHER_EMAIL));
            assertFalse(findList(client, OTHER, listId).isEmpty());
            assertEquals(200, send(client, OWNER, "DELETE", ItemController.PATH_ITEM_LIST_MEMBER,
                    new ItemController.DeleteItemListMemberRequest(listId, otherPersonId)).statusCode());
            assertTrue(findList(client, OTHER, listId).isEmpty());
        });
    }

    private int createList(HttpClient client, UUID session, String name) throws IOException, InterruptedException {
        HttpResponse<String> response = send(client, session, "POST", ItemController.PATH_ITEM_LIST,
                new ItemController.PostItemListRequest(name));
        assertEquals(201, response.statusCode());
        return mapper.readValue(response.body(), ItemController.PostItemListResponse.class).id();
    }

    private Optional<ItemController.GetItemListResponse> findList(HttpClient client, UUID session, int listId)
            throws IOException, InterruptedException
    {
        HttpResponse<String> response = send(client, session, "GET", ItemController.PATH_ITEM_LIST, null);
        assertEquals(200, response.statusCode());
        List<ItemController.GetItemListResponse> lists = mapper.readValue(response.body(), new TypeReference<>() {});
        return lists.stream().filter(list -> list.id() == listId).findFirst();
    }

    private HttpResponse<String> send(HttpClient client, UUID session, String method, String path, Object body)
            throws IOException, InterruptedException
    {
        HttpRequest.BodyPublisher publisher = null == body
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(client.getOrigin() + path))
                .header(Header.AUTHORIZATION, session.toString())
                .method(method, publisher)
                .build();
        return java.net.http.HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
