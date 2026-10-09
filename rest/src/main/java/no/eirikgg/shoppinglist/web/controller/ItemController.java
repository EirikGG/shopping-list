package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.shoppinglist.datamodel.Item;
import no.eirikgg.shoppinglist.datamodel.ItemList;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.service.AuthenticationService;
import no.eirikgg.shoppinglist.service.ItemService;
import no.eirikgg.shoppinglist.web.filter.AuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class ItemController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String PATH_ITEM_LIST = "/list";
    public static final String PATH_ITEM = "/item";

    public static final String PARAM_ITEM_LIST_NAME = "item-list-name";
    public static final String PARAM_ITEM_LIST_ID = "item-list-id";

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


        routes.post(PATH_ITEM, ic::saveItem);
        routes.get(PATH_ITEM, ic::getItem);
        routes.patch(PATH_ITEM, ic::patchItem);
        routes.delete(PATH_ITEM, ic::deleteItem);


        routes.post(PATH_ITEM_LIST, ic::saveItemList);
        routes.get(PATH_ITEM_LIST, ic::getItemList);
        routes.patch(PATH_ITEM_LIST, ic::patchItemList);
        routes.delete(PATH_ITEM_LIST, ic::deleteItemList);
    }



    record GetItemResponse(
            int id, String name, LocalDateTime completed
    ) {}
    private void getItem(Context ctx) {}
    private void saveItem(Context ctx) {}
    private void deleteItem(Context ctx) {}
    private void patchItem(Context ctx) {}






    record SaveItemListResponse(
        int id, String name
    ) {}
    private void saveItemList(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        JsonNode body = mapper.readTree(ctx.body());
        String name = body.get(PARAM_ITEM_LIST_NAME).asText();
        ItemList itemList = itemService.createItemList(sessionId, name);
        ctx.status(HttpStatus.OK).json(new SaveItemListResponse(
                itemList.getId().orElseThrow(),
                itemList.getName()
        ));
    }

    record GetItemListResponse(
        int id, String name, List<GetItemResponse> items
    ) {}
    private void getItemList(Context ctx) {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        List<ItemList> itemLists = itemService.getItemLists(sessionId);

        List<GetItemListResponse> response = new LinkedList<>();
        for (ItemList itemList : itemLists) {
            List<GetItemResponse> items = itemList.getItems().stream()
                .map(i -> new GetItemResponse(
                    i.getId().orElseThrow(),
                    i.getName(),
                    i.getCompleted().orElse(null)
                )
            ).toList();

            response.add(new GetItemListResponse(
                    itemList.getId().orElseThrow(),
                    itemList.getName(),
                    items
            ));
        }

        ctx.status(HttpStatus.OK).json(response);
    }

    private void deleteItemList(Context ctx) {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        int itemListId;
        try {
            itemListId = Integer.parseInt(ctx.queryParam(PARAM_ITEM_LIST_ID));
        }
        catch (Exception e) {
            throw new BadRequestResponse("Not valid item list id");
        }
        itemService.deleteItemList(sessionId, itemListId);
    }

    private void patchItemList(Context ctx) {

    }
}