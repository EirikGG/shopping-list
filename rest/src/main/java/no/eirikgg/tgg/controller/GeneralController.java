package no.eirikgg.tgg.controller;

import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.tgg.db.Db;

import java.util.HashMap;
import java.util.Map;

public class GeneralController {

    public static final String PING_PATH = "/ping";
    public static final String NOOP_PATH = "/noop";


    private Db db;

    public GeneralController(Db db) {
        this.db = db;
    }

    public void pingDb(Context ctx) {
        HttpStatus status = db.dbConnectionOk() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;

        Map<String, String> response = new HashMap<>();
        response.put("status", status.toString());

        ctx.contentType(ContentType.APPLICATION_JSON);
        ctx.status(status);
        ctx.json(response);
    }

    public void noop(Context ctx) {
        ctx.contentType(ContentType.APPLICATION_JSON);
        ctx.status(HttpStatus.OK);
    }
}
