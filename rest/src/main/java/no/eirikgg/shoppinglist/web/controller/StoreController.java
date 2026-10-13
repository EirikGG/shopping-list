package no.eirikgg.shoppinglist.web.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.service.StoreService;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;

import java.time.LocalDateTime;
import java.util.Optional;

public class StoreController {

    public static final String PATH_STORE = "/store";

    private StoreService storeService;

    public StoreController(Db db) {
        this.storeService = new StoreService(db);
    }

    public static void setup(JavalinConfig config, Db db) {
        StoreController sc = new StoreController(db);
        config.routes.post(PATH_STORE, sc::createStore);
        config.routes.get(PATH_STORE, sc::getStore);
    }


    record PostStoreRequest(String name) {}
    record PostStoreResponse(int storeId) {}
    private void createStore(Context ctx) {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PostStoreRequest request = ctx.bodyAsClass(PostStoreRequest.class);
        Store store = storeService.createStore(session, request.name());
        Integer storeId = store.getId().orElseThrow();
        PostStoreResponse responseBody = new PostStoreResponse(storeId);
        ctx.status(HttpStatus.OK).json(responseBody);
    }


    record GetStoreRequest(int storeId) {}
    record GetStoreResponse(int storeId, String name, LocalDateTime created) {}
    private void getStore(Context ctx) throws NoAccessToResourceException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        GetStoreRequest request = ctx.bodyAsClass(GetStoreRequest.class);
        Optional<Store> store = storeService.getStore(session, request.storeId());

        if (store.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }

        ctx.status(HttpStatus.OK).json(new GetStoreResponse(
                store.orElseThrow().getId().orElseThrow(),
                store.orElseThrow().getName(),
                store.orElseThrow().getCreated()
        ));
    }
}
