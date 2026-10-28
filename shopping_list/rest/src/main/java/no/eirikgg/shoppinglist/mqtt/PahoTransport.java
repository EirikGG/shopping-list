package no.eirikgg.shoppinglist.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// MQTT through Eclipse Paho. Until the first connection succeeds it tries again
// every RETRY_SECONDS, as the broker may start after the add-on; after that
// Paho reconnects by itself. If the add-on stops without saying so, the
// broker publishes "offline" on the availability topic for it.
public class PahoTransport implements MqttTransport {
    private static final Logger log = LoggerFactory.getLogger(PahoTransport.class);

    private static final String CLIENT_ID = "shopping_list_addon";
    private static final int QOS = 1;
    private static final int RETRY_SECONDS = 15;
    private static final long TIMEOUT_MS = 10_000;

    private final MqttSettings settings;
    private final MqttClient client;
    private final MqttConnectOptions options = new MqttConnectOptions();
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "mqtt-connect");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public PahoTransport(MqttSettings settings, String availabilityTopic) {
        this.settings = settings;
        try {
            client = new MqttClient(settings.serverUri(), CLIENT_ID, new MemoryPersistence());
        }
        catch (MqttException e) {
            throw new IllegalArgumentException("Not a valid MQTT broker address: " + settings.serverUri(), e);
        }
        client.setTimeToWait(TIMEOUT_MS);

        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout((int) (TIMEOUT_MS / 1000));
        if (null != settings.username()) {
            options.setUserName(settings.username());
        }
        if (null != settings.password()) {
            options.setPassword(settings.password().toCharArray());
        }
        options.setWill(availabilityTopic, MqttBridge.OFFLINE.getBytes(StandardCharsets.UTF_8), QOS, true);
    }

    @Override
    public void start(Listener listener) {
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverUri) {
                log.info(reconnect ? "Reconnected to the MQTT broker at {}" : "Connected to the MQTT broker at {}",
                        serverUri);
                listener.connected();
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("Lost the connection to the MQTT broker, reconnecting: {}", String.valueOf(cause));
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                listener.messageArrived(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });
        connector.execute(this::connect);
    }

    private void connect() {
        if (closed) {
            return;
        }
        try {
            client.connect(options);
        }
        catch (MqttException e) {
            log.warn("Could not connect to the MQTT broker at {}, trying again in {} s: {}",
                    settings.serverUri(), RETRY_SECONDS, e.toString());
            connector.schedule(this::connect, RETRY_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public void subscribe(String topicFilter) {
        try {
            client.subscribe(topicFilter, QOS);
        }
        catch (MqttException e) {
            log.warn("Could not subscribe to {}: {}", topicFilter, e.toString());
        }
    }

    @Override
    public void publish(String topic, String payload, boolean retained) {
        if (!client.isConnected()) {
            log.debug("Not connected to MQTT, dropped a message to {}", topic);
            return;
        }
        try {
            client.publish(topic, payload.getBytes(StandardCharsets.UTF_8), QOS, retained);
        }
        catch (MqttException e) {
            log.warn("Could not publish to {}: {}", topic, e.toString());
        }
    }

    @Override
    public void close() {
        closed = true;
        connector.shutdownNow();
        try {
            if (client.isConnected()) {
                client.disconnect(TIMEOUT_MS);
            }
            client.close();
        }
        catch (MqttException e) {
            log.debug("Could not close the MQTT connection cleanly: {}", e.toString());
        }
    }
}
