package no.eirikgg.shoppinglist.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.UserNotLoggedInException;
import no.eirikgg.shoppinglist.service.AuthenticationService;
import no.eirikgg.shoppinglist.service.ItemService;

import java.util.UUID;

public class ItemController {

    public static final String PATH_ITEM_LIST = "/list";
    public static final String PATH_ITEM = "/item";

    public static final String PARAM_ITEM_LIST_NAME = "item-list-name";

    private ObjectMapper mapper = new ObjectMapper();

    private ItemService itemService;
    private AuthenticationService authenticationService;

    public ItemController(Db db) {
        itemService = new ItemService(db);
        authenticationService = new AuthenticationService(db);
    }

    public static void setup(JavalinConfig config, Db db) {
        ItemController ic = new ItemController(db);
        RoutesConfig routes = config.routes;


        routes.get(PATH_ITEM, ic::getItem);
        routes.post(PATH_ITEM, ic::saveItem);
        routes.delete(PATH_ITEM, ic::deleteItem);
        routes.patch(PATH_ITEM, ic::patchItem);


        routes.get(PATH_ITEM_LIST, ic::getItemList);
        routes.post(PATH_ITEM_LIST, ic::saveItemList);
        routes.delete(PATH_ITEM_LIST, ic::deleteItemList);
        routes.patch(PATH_ITEM_LIST, ic::patchItemList);
    }



    private void getItem(Context ctx) {}
    private void saveItem(Context ctx) {}
    private void deleteItem(Context ctx) {}
    private void patchItem(Context ctx) {}



    private void getItemList(Context ctx) {}
    private void saveItemList(Context ctx) throws JsonProcessingException {
        UUID sessionId = AuthenticationService.getSessionIdFromRequest(ctx).orElseThrow(
                () -> new UserNotLoggedInException("Must be logged in to create list")
        );
        JsonNode body = mapper.readTree(ctx.body());
        String name = body.get(PARAM_ITEM_LIST_NAME).asText();
        itemService.createItemList(sessionId, name);
    }
    private void deleteItemList(Context ctx) {}
    private void patchItemList(Context ctx) {}
}