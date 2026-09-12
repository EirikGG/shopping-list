package no.eirikgg.tgg.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LogFilter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static void setup(JavalinConfig config) {
        LogFilter logFilter = new LogFilter();
        config.routes.beforeMatched(logFilter::filter);
    }


    public void filter(Context ctx) {
        String sessionId = ctx.header("Authorization");
        String contextPath = ctx.path();
        HandlerType handlerType = ctx.method();

        if (null != sessionId) {
            log.info("[{}] - {} - {}", handlerType, contextPath, sessionId);
        }
        else {
            log.info("[{}] - {}", handlerType, contextPath);
        }
    }

}
