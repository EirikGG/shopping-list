package no.eirikgg.tgg;

import io.javalin.http.Context;
import io.javalin.http.Header;

import java.util.Optional;
import java.util.UUID;

public class Utils {

    public static Optional<UUID> getSessionIdFromRequest(Context ctx) {
        return Optional.ofNullable(ctx)
                .map(context -> context.header(Header.AUTHORIZATION))
                .map(header -> header.replace("Bearer ", ""))
                .map(UUID::fromString);
    }

}
