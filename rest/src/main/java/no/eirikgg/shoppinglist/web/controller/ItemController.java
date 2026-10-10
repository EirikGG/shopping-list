package no.eirikgg.shoppinglist.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.shoppinglist.Json;
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
import java.util.Optional;
import java.util.UUID;

public class ItemController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String PATH_ITEM_LIST = "/item-list";
    public static final String PATH_ITEM = "/item";


    private ObjectMapper mapper = Json.MAPPER;

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


        routes.post(PATH_ITEM_LIST, ic::postItemList);
        routes.get(PATH_ITEM_LIST, ic::getItemList);
        routes.patch(PATH_ITEM_LIST, ic::patchItemList);
        routes.delete(PATH_ITEM_LIST, ic::deleteItemList);
    }









    record GetItemRequest(
            int id
    ) {}
    record GetItemResponse(
            int id, String name, LocalDateTime completed
    ) {}
    private void getItem(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        GetItemRequest request = mapper.readValue(ctx.body(), GetItemRequest.class);
        Optional<Item> item = itemService.getItem(sessionId, request.id());
        if (item.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        int itemId = item.orElseThrow().getId().orElseThrow();
        String name = item.orElseThrow().getName();
        LocalDateTime completed = item.orElseThrow().getCompleted().orElse(null);
        GetItemResponse response = new GetItemResponse(itemId, name, completed);
        ctx.status(HttpStatus.OK).json(response);
    }


    record PostItemRequest(
            int itemListId, String name
    ) {}
    record PostItemResponse(
            int id, String name
    ) {}
    private void saveItem(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        PostItemRequest request = mapper.readValue(ctx.body(), PostItemRequest.class);
        Item item = itemService.createItem(sessionId, request.itemListId(), request.name());
        PostItemResponse response = new PostItemResponse(item.getId().orElseThrow(), item.getName());
        ctx.status(HttpStatus.CREATED).json(mapper.writeValueAsString(response));
    }

    record DeleteItemRequest(
            int id
    ) {}
    private void deleteItem(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        DeleteItemRequest request = mapper.readValue(ctx.body(), DeleteItemRequest.class);
        itemService.deleteItem(sessionId, request.id());
    }


    record PatchItemRequest(
            int id, String newName, boolean completed
    ) {}
    record PatchItemResponse(
            int id, String name, LocalDateTime completed
    ) {}
    private void patchItem(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        PatchItemRequest request = mapper.readValue(ctx.body(), PatchItemRequest.class);
        LocalDateTime completedTime = request.completed ? LocalDateTime.now() : null;
        Item item = itemService.patchItem(sessionId, request.id(), request.newName(), completedTime);
        PatchItemResponse response = new PatchItemResponse(
                item.getId().orElseThrow(),
                item.getName(),
                item.getCompleted().orElse(null)
        );
        ctx.status(HttpStatus.OK).json(response);
    }

















    record PostItemListRequest(
        String name
    ) {}
    record PostItemListResponse(
        int id, String name
    ) {}
    private void postItemList(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        PostItemListRequest request = mapper.readValue(ctx.body(), PostItemListRequest.class);
        String name = request.name();
        ItemList itemList = itemService.createItemList(sessionId, name);
        ctx.status(HttpStatus.CREATED).json(new PostItemListResponse(
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

    record DeleteItemListRequest(
            int itemListId
    ) {}
    private void deleteItemList(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        DeleteItemListRequest request = mapper.readValue(ctx.body(), DeleteItemListRequest.class);
        itemService.deleteItemList(sessionId, request.itemListId());
    }

    record PatchItemListRequest(
            int itemListId, String newName
    ) {}
    private void patchItemList(Context ctx) throws JsonProcessingException {
        UUID sessionId = ctx.attribute(AuthenticationFilter.PARAM_SESSION_ID);
        PatchItemListRequest request = mapper.readValue(ctx.body(), PatchItemListRequest.class);

        itemService.updateItemList(sessionId,
                request.itemListId,
                request.newName
        );
    }
}