package no.eirikgg.shoppinglist.web.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.datamodel.Store;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import no.eirikgg.shoppinglist.service.StoreService;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

public class StoreController {

    public static final String PATH_STORE = "/store";
    public static final String PATH_STORE_ITEM_MAPPING = "/store-item-mapping";

    private StoreService storeService;

    public StoreController(Db db) {
        this.storeService = new StoreService(db);
    }

    public static void setup(JavalinConfig config, Db db) {
        StoreController sc = new StoreController(db);
        config.routes.post(PATH_STORE, sc::createStore);
        config.routes.get(PATH_STORE, sc::getStore);
        config.routes.patch(PATH_STORE, sc::patchStore);
        config.routes.delete(PATH_STORE, sc::deleteStore);

        config.routes.post(PATH_STORE_ITEM_MAPPING, sc::mapStoreAndItem);
    }


    record PostStoreRequest(String name) {}
    record PostStoreResponse(int storeId) {}
    private void createStore(Context ctx) {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PostStoreRequest request = ctx.bodyAsClass(PostStoreRequest.class);
        Store store = storeService.createStore(session, request.name());
        Integer storeId = store.getId().orElseThrow();
        PostStoreResponse responseBody = new PostStoreResponse(storeId);
        ctx.status(HttpStatus.CREATED).json(responseBody);
    }


    record GetStoreRequest(int storeId) {}
    record GetStoreResponse(int storeId, String name, LocalDateTime created) {}
    private void getStore(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        GetStoreRequest request = ctx.bodyAsClass(GetStoreRequest.class);
        Store store = storeService.getStore(session, request.storeId());

        ctx.status(HttpStatus.OK).json(new GetStoreResponse(
                store.getId().orElseThrow(),
                store.getName(),
                store.getCreated()
        ));
    }


    record PatchStoreRequest(int storeId, String newName) {}
    private void patchStore(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PatchStoreRequest request = ctx.bodyAsClass(PatchStoreRequest.class);
        int storeId = request.storeId();
        String newName = request.newName();

        storeService.patchStore(session, storeId, newName);
    }


    record DeleteStoreRequest(int storeId) {}
    private void deleteStore(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        DeleteStoreRequest request = ctx.bodyAsClass(DeleteStoreRequest.class);
        int storeId = request.storeId();

        storeService.deleteStore(session, storeId);
    }



    record MapStoreAndItemRequest(int storeId, int itemId) {}
    private void mapStoreAndItem(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        MapStoreAndItemRequest request = ctx.bodyAsClass(MapStoreAndItemRequest.class);
        int storeId = request.storeId();
        int itemId = request.itemId();

        storeService.mapStoreAndItem(session, storeId, itemId);
    }
}
