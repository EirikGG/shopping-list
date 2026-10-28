package no.eirikgg.shoppinglist.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import no.eirikgg.shoppinglist.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

// Where Home Assistant's MQTT broker is, and how to log in to it. serverUri is
// like tcp://core-mosquitto:1883. username and password may be null.
public record MqttSettings(String serverUri, String username, String password) {
    private static final Logger log = LoggerFactory.getLogger(MqttSettings.class);

    // The broker HA knows of, usually the Mosquitto add-on. The add-on may ask
    // for it as config.yaml has services: mqtt:want.
    static final String SUPERVISOR_MQTT_SERVICE = "http://supervisor/services/mqtt";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // MQTT.URL, MQTT.USER and MQTT.PASSWORD if MQTT.URL is set, as in
    // development. Otherwise the broker the Supervisor gives the add-on. Empty
    // if there is none, and then nothing is sent to Home Assistant.
    public static Optional<MqttSettings> find() {
        String url = System.getProperty("MQTT.URL");
        if (null != url && !url.isBlank()) {
            log.info("Using the MQTT broker at {}", url.trim());
            return Optional.of(new MqttSettings(
                    url.trim(), System.getProperty("MQTT.USER"), System.getProperty("MQTT.PASSWORD")
            ));
        }

        // Only set when running as an add-on.
        String token = System.getenv("SUPERVISOR_TOKEN");
        if (null == token || token.isBlank()) {
            log.info("No MQTT broker set, so changes are not sent to Home Assistant");
            return Optional.empty();
        }
        return fromSupervisor(token);
    }

    private static Optional<MqttSettings> fromSupervisor(String token) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(SUPERVISOR_MQTT_SERVICE))
                .header("Authorization", "Bearer " + token)
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (200 != response.statusCode()) {
                log.info(
                        "Home Assistant has no MQTT broker (status {}), so changes are not sent to it",
                        response.statusCode()
                );
                return Optional.empty();
            }
            MqttSettings settings = fromServiceResponse(response.body());
            log.info("Using Home Assistant's MQTT broker at {}", settings.serverUri());
            return Optional.of(settings);
        }
        catch (IOException e) {
            log.warn("Could not ask the Supervisor for the MQTT broker, so changes are not sent: {}", e.toString());
            return Optional.empty();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    // The Supervisor's answer: {"result": "ok", "data": {"host", "port",
    // "ssl", "username", "password", ...}}.
    static MqttSettings fromServiceResponse(String body) throws IOException {
        JsonNode data = Json.MAPPER.readTree(body).path("data");
        String host = data.path("host").asText("");
        if (host.isBlank()) {
            throw new IOException("The Supervisor gave no MQTT host");
        }
        String scheme = data.path("ssl").asBoolean(false) ? "ssl" : "tcp";
        return new MqttSettings(
                scheme + "://" + host + ":" + data.path("port").asInt(1883),
                textOrNull(data, "username"),
                textOrNull(data, "password")
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return null == value || value.isNull() ? null : value.asText();
    }

    // Leaves out the password, so it is never logged.
    @Override
    public String toString() {
        return "MqttSettings[serverUri=" + serverUri + ", username=" + username + "]";
    }
}
