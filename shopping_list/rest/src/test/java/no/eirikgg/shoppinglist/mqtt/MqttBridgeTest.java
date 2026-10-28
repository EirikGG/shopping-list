package no.eirikgg.shoppinglist.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.LiquibaseMigrator;
import no.eirikgg.shoppinglist.db.TestDb;
import no.eirikgg.shoppinglist.service.ItemService;
import no.eirikgg.shoppinglist.service.PersonService;
import no.eirikgg.shoppinglist.service.StoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttBridgeTest {

    private TestDb db;
    private FakeTransport transport;
    private MqttBridge bridge;
    // Changes made in the app, which tell the bridge as the API does.
    private ItemService app;
    private StoreService appStores;
    private Person user;
    private int listId;

    @BeforeEach
    void setUp() throws Exception {
        db = TestDb.createMigratedInstance();
        new LiquibaseMigrator().migrate(db, "db/updates/t0001-create-test-list.sql");
        transport = new FakeTransport();
        bridge = new MqttBridge(db, transport, Runnable::run);
        app = new ItemService(db, bridge);
        appStores = new StoreService(db, bridge);
        user = new PersonService(db).getOrCreate("ha-user", "Kari");
        listId = app.getItemLists().getFirst().getId().orElseThrow();
        bridge.start();
    }

    @AfterEach
    void closeDb() {
        db.close();
    }

    @Test
    void connecting_announcesEveryListToHomeAssistant() throws Exception {
        app.createItem(user, listId, "Milk", 2, List.of());
        transport.clear();

        transport.listener.connected();

        assertTrue(transport.subscriptions.contains("shopping_list/list/+/add"));
        assertTrue(transport.subscriptions.contains("shopping_list/list/+/command"));
        assertTrue(transport.subscriptions.contains("homeassistant/status"));
        assertEquals("online", transport.last("shopping_list/status").payload());

        JsonNode sensor = transport.lastJson("homeassistant/sensor/shopping_list_" + listId + "/open_items/config");
        assertEquals("unit-test-list", sensor.path("device").path("name").asText());
        assertEquals("shopping_list/list/" + listId + "/state", sensor.path("state_topic").asText());
        assertTrue(transport.last("homeassistant/sensor/shopping_list_" + listId + "/open_items/config").retained());

        JsonNode event = transport.lastJson("homeassistant/event/shopping_list_" + listId + "/changes/config");
        assertEquals(
                List.of("item_added", "item_completed", "item_reopened", "item_changed", "item_deleted"),
                Json.MAPPER.convertValue(event.path("event_types"), List.class)
        );
        JsonNode text = transport.lastJson("homeassistant/text/shopping_list_" + listId + "/add_item/config");
        assertEquals("shopping_list/list/" + listId + "/add", text.path("command_topic").asText());

        JsonNode state = transport.lastJson(MqttBridge.stateTopic(listId));
        assertEquals(1, state.path("open").asInt());
        assertEquals(1, state.path("total").asInt());
        assertEquals("Milk", state.path("items").get(0).path("name").asText());
        assertEquals(2, state.path("items").get(0).path("amount").asInt());
    }

    @Test
    void changesInTheApp_arePublishedAsEvents() throws Exception {
        Item milk = app.createItem(user, listId, "Milk", null, List.of());
        int milkId = milk.getId().orElseThrow();
        JsonNode added = transport.lastJson(MqttBridge.eventTopic(listId));
        assertEquals("item_added", added.path("event_type").asText());
        assertEquals("Milk", added.path("item").asText());
        assertEquals("unit-test-list", added.path("list").asText());
        assertEquals("Kari", added.path("by").asText());
        assertEquals("ha-user", added.path("by_user_id").asText());
        assertFalse(transport.last(MqttBridge.eventTopic(listId)).retained());

        app.patchItem(user, milkId, "Milk", null, LocalDateTime.now());
        assertEquals("item_completed", transport.lastJson(MqttBridge.eventTopic(listId)).path("event_type").asText());
        JsonNode state = transport.lastJson(MqttBridge.stateTopic(listId));
        assertEquals(0, state.path("open").asInt());
        assertEquals(1, state.path("total").asInt());

        app.patchItem(user, milkId, "Milk", null, null);
        assertEquals("item_reopened", transport.lastJson(MqttBridge.eventTopic(listId)).path("event_type").asText());

        app.patchItem(user, milkId, "Oat milk", 3, null);
        JsonNode changed = transport.lastJson(MqttBridge.eventTopic(listId));
        assertEquals("item_changed", changed.path("event_type").asText());
        assertEquals("Oat milk", changed.path("item").asText());
        assertEquals(3, changed.path("amount").asInt());

        int storeId = appStores.createStore(user, listId, "Rema").getId().orElseThrow();
        transport.clear();
        appStores.mapStoreAndItem(user, storeId, milkId);
        JsonNode withStore = transport.lastJson(MqttBridge.eventTopic(listId));
        assertEquals("item_changed", withStore.path("event_type").asText());
        assertEquals("Rema", withStore.path("stores").get(0).asText());
        // Giving it the same store again changes nothing.
        transport.clear();
        appStores.mapStoreAndItem(user, storeId, milkId);
        assertTrue(transport.published.isEmpty());

        app.deleteItem(user, milkId);
        JsonNode deleted = transport.lastJson(MqttBridge.eventTopic(listId));
        assertEquals("item_deleted", deleted.path("event_type").asText());
        assertEquals(milkId, deleted.path("item_id").asInt());
        assertEquals(0, transport.lastJson(MqttBridge.stateTopic(listId)).path("total").asInt());
    }

    @Test
    void addItemText_addsTheItem_andClearsTheText() throws Exception {
        transport.listener.messageArrived(MqttBridge.addTopic(listId), "  Bread ");

        List<Item> items = items();
        assertEquals(1, items.size());
        assertEquals("Bread", items.getFirst().getName());
        JsonNode added = transport.lastJson(MqttBridge.eventTopic(listId));
        assertEquals("item_added", added.path("event_type").asText());
        assertEquals("Home Assistant", added.path("by").asText());
        assertTrue(added.path("by_user_id").isNull());
        assertEquals("", transport.last(MqttBridge.addStateTopic(listId)).payload());
    }

    @Test
    void commands_addCompleteReopenAndDelete() throws Exception {
        appStores.createStore(user, listId, "Rema");
        String commands = MqttBridge.commandTopic(listId);

        transport.listener.messageArrived(commands,
                "{\"action\": \"add\", \"name\": \"Eggs\", \"amount\": 12, \"stores\": [\"rema\", \"Kiwi\"]}");
        Item eggs = items().getFirst();
        assertEquals("Eggs", eggs.getName());
        assertEquals(12, eggs.getAmount());
        // Kiwi is not one of the list's stores, so it is left out.
        assertEquals(List.of("Rema"), eggs.getStores().stream().map(store -> store.getName()).toList());

        transport.listener.messageArrived(commands, "{\"action\": \"complete\", \"name\": \"eggs\"}");
        assertTrue(items().getFirst().getCompleted().isPresent());
        assertEquals("item_completed", transport.lastJson(MqttBridge.eventTopic(listId)).path("event_type").asText());

        transport.listener.messageArrived(commands, "{\"action\": \"reopen\", \"name\": \"Eggs\"}");
        assertTrue(items().getFirst().getCompleted().isEmpty());

        transport.listener.messageArrived(commands,
                "{\"action\": \"delete\", \"item_id\": " + eggs.getId().orElseThrow() + "}");
        assertTrue(items().isEmpty());
        assertEquals("item_deleted", transport.lastJson(MqttBridge.eventTopic(listId)).path("event_type").asText());
    }

    // Completing picks the newest open item of that name, not a bought one.
    @Test
    void complete_takesAnOpenItem() throws Exception {
        Item old = app.createItem(user, listId, "Milk", null, List.of());
        app.patchItem(user, old.getId().orElseThrow(), "Milk", null, LocalDateTime.now());
        Item fresh = app.createItem(user, listId, "Milk", null, List.of());

        transport.listener.messageArrived(MqttBridge.commandTopic(listId), "{\"action\": \"complete\", \"name\": \"Milk\"}");

        assertTrue(app.getItem(fresh.getId().orElseThrow()).orElseThrow().getCompleted().isPresent());
    }

    @Test
    void badMessages_areIgnored() throws Exception {
        String commands = MqttBridge.commandTopic(listId);
        transport.listener.messageArrived(commands, "not json");
        transport.listener.messageArrived(commands, "{\"action\": \"explode\"}");
        transport.listener.messageArrived(commands, "{\"action\": \"complete\", \"name\": \"Nothing\"}");
        transport.listener.messageArrived(commands, "{\"action\": \"add\", \"name\": \"Eggs\", \"amount\": 0}");
        transport.listener.messageArrived(MqttBridge.commandTopic(9999), "{\"action\": \"add\", \"name\": \"Eggs\"}");
        transport.listener.messageArrived(MqttBridge.addTopic(listId), "");

        assertTrue(items().isEmpty());
    }

    @Test
    void renamingAndDeletingLists_updatesHomeAssistant() throws Exception {
        ItemList second = app.createItemList(user, "Hardware store");
        int secondId = second.getId().orElseThrow();
        String secondSensor = "homeassistant/sensor/shopping_list_" + secondId + "/open_items/config";
        assertEquals("Hardware store", transport.lastJson(secondSensor).path("device").path("name").asText());

        app.updateItemList(secondId, "Tools");
        assertEquals("Tools", transport.lastJson(secondSensor).path("device").path("name").asText());

        app.deleteItemList(secondId);
        // An empty retained message makes HA remove the entity.
        assertEquals("", transport.last(secondSensor).payload());
        assertTrue(transport.last(secondSensor).retained());
        assertEquals("", transport.last(MqttBridge.stateTopic(secondId)).payload());
    }

    @Test
    void homeAssistantStarting_getsEverythingAgain() {
        transport.clear();
        transport.listener.messageArrived("homeassistant/status", "online");
        assertFalse(transport.published.isEmpty());

        transport.clear();
        transport.listener.messageArrived("homeassistant/status", "offline");
        assertTrue(transport.published.isEmpty());
    }

    @Test
    void closing_saysOffline() {
        bridge.close();
        assertEquals("offline", transport.last("shopping_list/status").payload());
        assertTrue(transport.closed);
    }

    @Test
    void supervisorAnswer_givesTheBroker() throws Exception {
        MqttSettings settings = MqttSettings.fromServiceResponse("""
                {"result": "ok", "data": {"host": "core-mosquitto", "port": 1883, "ssl": false,
                 "protocol": "3.1.1", "username": "addons", "password": "secret", "app": "core_mosquitto"}}
                """);
        assertEquals("tcp://core-mosquitto:1883", settings.serverUri());
        assertEquals("addons", settings.username());
        assertEquals("secret", settings.password());
        assertFalse(settings.toString().contains("secret"));
    }

    private List<Item> items() {
        return app.getItemList(listId).orElseThrow().getItems();
    }


    record Published(String topic, String payload, boolean retained) {}

    static class FakeTransport implements MqttTransport {
        Listener listener;
        final List<Published> published = new ArrayList<>();
        final List<String> subscriptions = new ArrayList<>();
        boolean closed;

        @Override
        public void start(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void subscribe(String topicFilter) {
            subscriptions.add(topicFilter);
        }

        @Override
        public void publish(String topic, String payload, boolean retained) {
            published.add(new Published(topic, payload, retained));
        }

        @Override
        public void close() {
            closed = true;
        }

        void clear() {
            published.clear();
        }

        Published last(String topic) {
            return published.reversed().stream()
                    .filter(message -> message.topic().equals(topic))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Nothing published to " + topic));
        }

        JsonNode lastJson(String topic) throws Exception {
            return Json.MAPPER.readTree(last(topic).payload());
        }
    }
}
