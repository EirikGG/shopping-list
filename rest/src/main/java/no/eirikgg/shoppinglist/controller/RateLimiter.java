package no.eirikgg.shoppinglist.controller;

import io.javalin.http.Context;
import io.javalin.http.TooManyRequestsResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class RateLimiter {
    private final int limit;
    private final long periodMillis;

    private final Map<String, Integer> counts = new HashMap<>();
    private long periodStart = System.currentTimeMillis();

    public RateLimiter(int limit, Duration period) {
        this.limit = limit;
        this.periodMillis = period.toMillis();
    }

    public synchronized void check(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - periodStart >= periodMillis) {
            counts.clear();
            periodStart = now;
        }

        String groupingKey = ctx.ip() + ctx.path();
        int count = counts.merge(groupingKey, 1, Integer::sum);
        if (count > limit) {
            throw new TooManyRequestsResponse("Too many requests");
        }
    }
}
