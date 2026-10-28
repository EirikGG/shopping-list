package no.eirikgg.shoppinglist.mqtt;

// The connection to the MQTT broker, kept apart from MqttBridge so the bridge
// can be tested without one.
public interface MqttTransport {

    interface Listener {
        // Connected, the first time or again after losing the connection.
        // Subscriptions are gone after a reconnect and must be made again.
        void connected();

        void messageArrived(String topic, String payload);
    }

    // Connects in the background, and keeps trying until it can.
    void start(Listener listener);

    void subscribe(String topicFilter);

    // Dropped if not connected; the bridge publishes everything again when it
    // reconnects.
    void publish(String topic, String payload, boolean retained);

    void close();
}
