package no.eirikgg.shoppinglist.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import no.eirikgg.shoppinglist.Json;
import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.service.ItemService;
import no.eirikgg.shoppinglist.service.ListChanges;
import no.eirikgg.shoppinglist.service.PersonService;
import no.eirikgg.shoppinglist.service.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The lists in Home Assistant, through MQTT. Every list is a device, found by
// HA through MQTT discovery, with three entities:
//
// - sensor "Open items": how many items are left. Its attributes have the
//   total and the open items, from the retained JSON on shopping_list/list/<id>/state.
// - event "Changes": item_added, item_completed, item_reopened, item_changed
//   and item_deleted, from shopping_list/list/<id>/event. Automations trigger
//   on these.
// - text "Add item": what is typed in it is added to the list.
//
// Automations can change a list by publishing JSON to
// shopping_list/list/<id>/command; see handleCommand. Changes made that way
// are recorded as made by "Home Assistant".
//
// Everything runs on one thread, in order, so a change made through the app
// never waits for the broker.
public class MqttBridge implements ListChanges, MqttTransport.Listener {
    private static final Logger log = LoggerFactory.getLogger(MqttBridge.class);

    public static final String BASE_TOPIC = "shopping_list";
    // "online" while the add-on runs; "offline" makes HA show the entities as
    // unavailable.
    public static final String AVAILABILITY_TOPIC = BASE_TOPIC + "/status";
    static final String ONLINE = "online";
    static final String OFFLINE = "offline";

    static final String DISCOVERY_PREFIX = "homeassistant";
    // HA publishes "online" here when it starts, so discovery is sent again.
    static final String HA_STATUS_TOPIC = DISCOVERY_PREFIX + "/status";

    // The person changes made through MQTT are recorded as.
    static final String MQTT_PERSON_ID = "shopping-list-mqtt";
    static final String MQTT_PERSON_NAME = "Home Assistant";

    private static final Pattern LIST_TOPIC =
            Pattern.compile("^" + BASE_TOPIC + "/list/(\\d+)/(add|command)$");
    // The longest item name the database takes.
    private static final int MAX_NAME_LENGTH = 512;
    // The longest value HA's text entity allows.
    private static final int MAX_TEXT_LENGTH = 255;

    static final List<String> EVENT_TYPES =
            Arrays.stream(ItemEvent.values()).map(ItemEvent::eventType).toList();

    private final MqttTransport transport;
    private final Executor executor;
    private final ItemService itemService;
    private final StoreService storeService;
    private final PersonService personService;
    private Person mqttPerson;

    public MqttBridge(Db db, MqttTransport transport) {
        this(db, transport, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "mqtt");
            thread.setDaemon(true);
            return thread;
        }));
    }

    // Tests run everything straight away with Runnable::run.
    MqttBridge(Db db, MqttTransport transport, Executor executor) {
        this.transport = transport;
        this.executor = executor;
        // Changes made through MQTT come back here, and are published too.
        this.itemService = new ItemService(db, this);
        this.storeService = new StoreService(db, this);
        this.personService = new PersonService(db);
    }

    public void start() {
        transport.start(this);
    }

    // Tells HA the add-on is gone before disconnecting.
    public void close() {
        transport.publish(AVAILABILITY_TOPIC, OFFLINE, true);
        transport.close();
        if (executor instanceof ExecutorService service) {
            service.shutdown();
            try {
                service.awaitTermination(2, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    @Override
    public void connected() {
        run(() -> {
            transport.subscribe(BASE_TOPIC + "/list/+/add");
            transport.subscribe(BASE_TOPIC + "/list/+/command");
            transport.subscribe(HA_STATUS_TOPIC);
            transport.publish(AVAILABILITY_TOPIC, ONLINE, true);
            publishAll();
        });
    }

    @Override
    public void messageArrived(String topic, String payload) {
        run(() -> handleMessage(topic, payload));
    }

    @Override
    public void itemChanged(ItemEvent event, int itemListId, Item item, Person by) {
        run(() -> {
            Optional<ItemList> list = itemService.getItemList(itemListId);
            transport.publish(eventTopic(itemListId), json(eventPayload(event, itemListId, list, item, by)), false);
            list.ifPresent(this::publishState);
        });
    }

    @Override
    public void listsChanged() {
        run(this::publishAll);
    }

    private void run(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            }
            catch (RuntimeException e) {
                log.warn("MQTT: {}", e.toString(), e);
            }
        });
    }


    // Discovery and state for every list, and removes the entities of
    // deleted lists. Retained, so HA has them also when it starts after the
    // add-on.
    private void publishAll() {
        for (ItemList list : itemService.getItemLists()) {
            publishDiscovery(list);
            publishState(list);
        }
        for (int deletedId : itemService.getDeletedItemListIds()) {
            for (String[] entity : ENTITIES) {
                transport.publish(discoveryTopic(entity[0], deletedId, entity[1]), "", true);
            }
            transport.publish(stateTopic(deletedId), "", true);
            transport.publish(addStateTopic(deletedId), "", true);
        }
    }

    // Component and object id of each entity of a list.
    private static final String[][] ENTITIES = {
            {"sensor", "open_items"},
            {"event", "changes"},
            {"text", "add_item"},
    };

    private void publishDiscovery(ItemList list) {
        int id = list.getId().orElseThrow();

        Map<String, Object> sensor = entity(list, "open_items", "Open items", "mdi:cart-outline");
        sensor.put("state_topic", stateTopic(id));
        sensor.put("value_template", "{{ value_json.open }}");
        sensor.put("json_attributes_topic", stateTopic(id));
        sensor.put("unit_of_measurement", "items");
        sensor.put("state_class", "measurement");
        transport.publish(discoveryTopic("sensor", id, "open_items"), json(sensor), true);

        Map<String, Object> event = entity(list, "changes", "Changes", "mdi:cart-arrow-down");
        event.put("state_topic", eventTopic(id));
        event.put("event_types", EVENT_TYPES);
        transport.publish(discoveryTopic("event", id, "changes"), json(event), true);

        Map<String, Object> text = entity(list, "add_item", "Add item", "mdi:cart-plus");
        text.put("command_topic", addTopic(id));
        text.put("state_topic", addStateTopic(id));
        text.put("max", MAX_TEXT_LENGTH);
        transport.publish(discoveryTopic("text", id, "add_item"), json(text), true);
    }

    private static Map<String, Object> entity(ItemList list, String objectId, String name, String icon) {
        int id = list.getId().orElseThrow();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", name);
        config.put("unique_id", "shopping_list_" + id + "_" + objectId);
        config.put("icon", icon);
        config.put("availability_topic", AVAILABILITY_TOPIC);
        config.put("device", Map.of(
                "identifiers", List.of("shopping_list_" + id),
                "name", list.getName(),
                "manufacturer", "Shopping list add-on",
                "model", "Shopping list"
        ));
        config.put("origin", Map.of(
                "name", "Shopping list",
                "support_url", "https://github.com/EirikGG/shopping-list"
        ));
        return config;
    }

    // The sensor's state and attributes.
    private void publishState(ItemList list) {
        int id = list.getId().orElseThrow();
        List<Item> open = list.getItems().stream().filter(item -> item.getCompleted().isEmpty()).toList();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("list_id", id);
        state.put("list", list.getName());
        state.put("open", open.size());
        state.put("total", list.getItems().size());
        state.put("items", open.stream().map(item -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", item.getId().orElseThrow());
            entry.put("name", item.getName());
            entry.put("amount", item.getAmount());
            entry.put("stores", storeNames(item));
            return entry;
        }).toList());
        state.put("command_topic", commandTopic(id));
        transport.publish(stateTopic(id), json(state), true);
    }

    private static Map<String, Object> eventPayload(
            ItemEvent event, int itemListId, Optional<ItemList> list, Item item, Person by
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", event.eventType());
        payload.put("list_id", itemListId);
        payload.put("list", list.map(ItemList::getName).orElse(null));
        payload.put("item_id", item.getId().orElseThrow());
        payload.put("item", item.getName());
        payload.put("amount", item.getAmount());
        payload.put("stores", storeNames(item));
        payload.put("completed", item.getCompleted().isPresent());
        payload.put("by", null == by ? null : by.name());
        // The Home Assistant user, as on person entities' user_id. null for
        // changes made through MQTT.
        payload.put("by_user_id", null == by || MQTT_PERSON_ID.equals(by.haUserId()) ? null : by.haUserId());
        return payload;
    }

    private static List<String> storeNames(Item item) {
        return item.getStores().stream().map(Store::getName).toList();
    }


    private void handleMessage(String topic, String payload) {
        if (HA_STATUS_TOPIC.equals(topic)) {
            if (ONLINE.equals(payload.trim())) {
                log.info("Home Assistant started, sending the lists to it again");
                publishAll();
            }
            return;
        }

        Matcher matcher = LIST_TOPIC.matcher(topic);
        if (!matcher.matches()) {
            return;
        }
        int itemListId = Integer.parseInt(matcher.group(1));
        try {
            if (itemService.getItemList(itemListId).isEmpty()) {
                throw new IllegalArgumentException("there is no list " + itemListId);
            }
            if ("add".equals(matcher.group(2))) {
                handleAdd(itemListId, payload);
            }
            else {
                handleCommand(itemListId, Json.MAPPER.readTree(payload));
            }
        }
        catch (Exception e) {
            log.warn("Ignored the MQTT message on {}: {}", topic, e.getMessage());
        }
    }

    // From the text entity: the name of an item, or JSON as for the command
    // topic, where action may be left out. Clears the text entity after.
    private void handleAdd(int itemListId, String payload) throws Exception {
        String text = payload.trim();
        try {
            if (text.isEmpty()) {
                return;
            }
            if (text.startsWith("{")) {
                JsonNode command = Json.MAPPER.readTree(text);
                if (!command.has("action")) {
                    add(itemListId, command);
                    return;
                }
                handleCommand(itemListId, command);
                return;
            }
            addItem(itemListId, text, null, List.of());
        }
        finally {
            transport.publish(addStateTopic(itemListId), "", true);
        }
    }

    // JSON with an action:
    //   {"action": "add", "name": "Milk", "amount": 2, "stores": ["Rema"]}
    //   {"action": "complete", "name": "Milk"}
    //   {"action": "reopen", "name": "Milk"}
    //   {"action": "delete", "name": "Milk"}
    // amount and stores are optional; stores the list does not have are
    // skipped. Instead of name, item_id picks the item. Names are matched
    // without regard to case: complete takes the newest open item, reopen the
    // last completed one, and delete an open item before a completed one.
    private void handleCommand(int itemListId, JsonNode command) throws Exception {
        String action = command.path("action").asText("").toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> add(itemListId, command);
            case "complete" -> {
                Item item = find(itemListId, command, open());
                itemService.patchItem(person(), id(item), item.getName(), null, LocalDateTime.now());
            }
            case "reopen" -> {
                Item item = find(itemListId, command, open().negate());
                itemService.patchItem(person(), id(item), item.getName(), null, null);
            }
            case "delete" -> {
                Item item = find(itemListId, command, open());
                itemService.deleteItem(person(), id(item));
            }
            default -> throw new IllegalArgumentException(
                    "action must be add, complete, reopen or delete, not \"" + action + "\"");
        }
    }

    private void add(int itemListId, JsonNode command) throws Exception {
        String name = command.path("name").asText("").trim();
        Integer amount = command.hasNonNull("amount") ? command.get("amount").asInt() : null;
        List<String> stores = new ArrayList<>();
        command.path("stores").forEach(store -> stores.add(store.asText()));
        addItem(itemListId, name, amount, stores);
    }

    private void addItem(int itemListId, String name, Integer amount, List<String> storeNames) throws Exception {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("the item has no name");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("the name is longer than " + MAX_NAME_LENGTH + " characters");
        }

        List<Store> listStores = storeService.getStores(itemListId);
        List<Integer> storeIds = new ArrayList<>();
        for (String storeName : storeNames) {
            Optional<Store> store = listStores.stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(storeName.trim()))
                    .findFirst();
            if (store.isPresent()) {
                storeIds.add(store.get().getId().orElseThrow());
            }
            else {
                log.warn("The list {} has no store \"{}\", adding {} without it", itemListId, storeName, name);
            }
        }
        itemService.createItem(person(), itemListId, name, amount, storeIds);
    }

    private static Predicate<Item> open() {
        return item -> item.getCompleted().isEmpty();
    }

    // The item the command names, among those preferred if any of them match.
    // The list has open items newest first, then completed ones last
    // completed first.
    private Item find(int itemListId, JsonNode command, Predicate<Item> preferred) {
        List<Item> items = itemService.getItemList(itemListId).orElseThrow().getItems();
        Predicate<Item> named;
        if (command.hasNonNull("item_id")) {
            int itemId = command.get("item_id").asInt();
            named = item -> item.getId().orElseThrow() == itemId;
        }
        else {
            String name = command.path("name").asText("").trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("give the item's name or item_id");
            }
            named = item -> item.getName().trim().equalsIgnoreCase(name);
        }
        return items.stream().filter(named.and(preferred)).findFirst()
                .or(() -> items.stream().filter(named).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("the list has no such item"));
    }

    private static int id(Item item) {
        return item.getId().orElseThrow();
    }

    private Person person() {
        if (null == mqttPerson) {
            mqttPerson = personService.getOrCreate(MQTT_PERSON_ID, MQTT_PERSON_NAME);
        }
        return mqttPerson;
    }


    static String listTopic(int itemListId) {
        return BASE_TOPIC + "/list/" + itemListId;
    }

    static String stateTopic(int itemListId) {
        return listTopic(itemListId) + "/state";
    }

    static String eventTopic(int itemListId) {
        return listTopic(itemListId) + "/event";
    }

    static String addTopic(int itemListId) {
        return listTopic(itemListId) + "/add";
    }

    static String addStateTopic(int itemListId) {
        return addTopic(itemListId) + "/state";
    }

    static String commandTopic(int itemListId) {
        return listTopic(itemListId) + "/command";
    }

    static String discoveryTopic(String component, int itemListId, String objectId) {
        return DISCOVERY_PREFIX + "/" + component + "/shopping_list_" + itemListId + "/" + objectId + "/config";
    }

    private static String json(Object value) {
        try {
            return Json.MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
