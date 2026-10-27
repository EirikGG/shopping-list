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
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import no.eirikgg.shoppinglist.service.ItemService;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class ItemController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String PATH_ITEM_LIST = "/item-list";
    public static final String PATH_ITEM = "/item";


    private ObjectMapper mapper = Json.MAPPER;

    private ItemService itemService;

    public ItemController(Db db) {
        itemService = new ItemService(db);
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









    record ItemStoreResponse(
            int storeId, String name
    ) {}
    private static List<ItemStoreResponse> toStoreResponses(Item item) {
        return item.getStores().stream()
                .map(store -> new ItemStoreResponse(store.getId().orElseThrow(), store.getName()))
                .toList();
    }


    record GetItemRequest(
            int id
    ) {}
    record GetItemResponse(
            int id, String name, LocalDateTime completed, List<ItemStoreResponse> stores
    ) {}
    private void getItem(Context ctx) throws JsonProcessingException {
        GetItemRequest request = mapper.readValue(ctx.body(), GetItemRequest.class);
        Optional<Item> item = itemService.getItem(request.id());
        if (item.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        int itemId = item.orElseThrow().getId().orElseThrow();
        String name = item.orElseThrow().getName();
        LocalDateTime completed = item.orElseThrow().getCompleted().orElse(null);
        GetItemResponse response = new GetItemResponse(itemId, name, completed, toStoreResponses(item.orElseThrow()));
        ctx.status(HttpStatus.OK).json(response);
    }


    // storeIds is optional.
    record PostItemRequest(
            int itemListId, String name, List<Integer> storeIds
    ) {}
    record PostItemResponse(
            int id, String name, List<ItemStoreResponse> stores
    ) {}
    private void saveItem(Context ctx)
            throws JsonProcessingException, ResourceNotFoundException, NoAccessToResourceException {
        Person person = ctx.attribute(IngressFilter.PARAM_PERSON);
        PostItemRequest request = ctx.bodyAsClass(PostItemRequest.class);
        Item item = itemService.createItem(person, request.itemListId(), request.name(), request.storeIds());
        PostItemResponse response = new PostItemResponse(
                item.getId().orElseThrow(),
                item.getName(),
                toStoreResponses(item)
        );
        ctx.status(HttpStatus.CREATED).json(mapper.writeValueAsString(response));
    }

    record DeleteItemRequest(
            int id
    ) {}
    private void deleteItem(Context ctx) throws JsonProcessingException {
        DeleteItemRequest request = mapper.readValue(ctx.body(), DeleteItemRequest.class);
        itemService.deleteItem(request.id());
    }


    record PatchItemRequest(
            int id, String newName, boolean completed
    ) {}
    record PatchItemResponse(
            int id, String name, LocalDateTime completed, List<ItemStoreResponse> stores
    ) {}
    private void patchItem(Context ctx) throws JsonProcessingException, ResourceNotFoundException {
        PatchItemRequest request = mapper.readValue(ctx.body(), PatchItemRequest.class);
        LocalDateTime completedTime = request.completed ? LocalDateTime.now() : null;
        Item item = itemService.patchItem(request.id(), request.newName(), completedTime);
        PatchItemResponse response = new PatchItemResponse(
                item.getId().orElseThrow(),
                item.getName(),
                item.getCompleted().orElse(null),
                toStoreResponses(item)
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
        Person person = ctx.attribute(IngressFilter.PARAM_PERSON);
        PostItemListRequest request = mapper.readValue(ctx.body(), PostItemListRequest.class);
        String name = request.name();
        ItemList itemList = itemService.createItemList(person, name);
        ctx.status(HttpStatus.CREATED).json(new PostItemListResponse(
                itemList.getId().orElseThrow(),
                itemList.getName()
        ));
    }

    record GetItemListResponse(
        int id, String name, List<GetItemResponse> items
    ) {}
    private void getItemList(Context ctx) {
        List<ItemList> itemLists = itemService.getItemLists();

        List<GetItemListResponse> response = new LinkedList<>();
        for (ItemList itemList : itemLists) {
            List<GetItemResponse> items = itemList.getItems().stream()
                .map(i -> new GetItemResponse(
                    i.getId().orElseThrow(),
                    i.getName(),
                    i.getCompleted().orElse(null),
                    toStoreResponses(i)
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
    private void deleteItemList(Context ctx)
            throws JsonProcessingException, ResourceNotFoundException, NoAccessToResourceException {
        DeleteItemListRequest request = mapper.readValue(ctx.body(), DeleteItemListRequest.class);
        itemService.deleteItemList(request.itemListId());
    }

    record PatchItemListRequest(
            int itemListId, String newName
    ) {}
    private void patchItemList(Context ctx)
            throws JsonProcessingException, ResourceNotFoundException, NoAccessToResourceException {
        PatchItemListRequest request = mapper.readValue(ctx.body(), PatchItemListRequest.class);

        itemService.updateItemList(
                request.itemListId,
                request.newName
        );
    }

}
