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
import no.eirikgg.shoppinglist.datamodel.ItemListMember;
import no.eirikgg.shoppinglist.datamodel.Session;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
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
    public static final String PATH_ITEM_LIST_MEMBER = "/item-list-member";


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

        routes.post(PATH_ITEM_LIST_MEMBER, ic::postItemListMember);
        routes.delete(PATH_ITEM_LIST_MEMBER, ic::deleteItemListMember);
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
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        GetItemRequest request = mapper.readValue(ctx.body(), GetItemRequest.class);
        Optional<Item> item = itemService.getItem(session, request.id());
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
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PostItemRequest request = ctx.bodyAsClass(PostItemRequest.class);
        Item item = itemService.createItem(session, request.itemListId(), request.name(), request.storeIds());
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
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        DeleteItemRequest request = mapper.readValue(ctx.body(), DeleteItemRequest.class);
        itemService.deleteItem(session, request.id());
    }


    record PatchItemRequest(
            int id, String newName, boolean completed
    ) {}
    record PatchItemResponse(
            int id, String name, LocalDateTime completed, List<ItemStoreResponse> stores
    ) {}
    private void patchItem(Context ctx) throws JsonProcessingException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PatchItemRequest request = mapper.readValue(ctx.body(), PatchItemRequest.class);
        LocalDateTime completedTime = request.completed ? LocalDateTime.now() : null;
        Item item = itemService.patchItem(session, request.id(), request.newName(), completedTime);
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
        int id, String name, ItemListMember.Role role, List<ItemListMemberResponse> members
    ) {}
    private void postItemList(Context ctx) throws JsonProcessingException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PostItemListRequest request = mapper.readValue(ctx.body(), PostItemListRequest.class);
        String name = request.name();
        ItemList itemList = itemService.createItemList(session, name);
        ctx.status(HttpStatus.CREATED).json(new PostItemListResponse(
                itemList.getId().orElseThrow(),
                itemList.getName(),
                itemList.getRole(),
                toMemberResponses(itemList, session)
        ));
    }

    // role is the logged-in user's role in the list.
    record GetItemListResponse(
        int id, String name, ItemListMember.Role role, List<ItemListMemberResponse> members,
        List<GetItemResponse> items
    ) {}
    private void getItemList(Context ctx) {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        List<ItemList> itemLists = itemService.getItemLists(session);

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
                    itemList.getRole(),
                    toMemberResponses(itemList, session),
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
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        DeleteItemListRequest request = mapper.readValue(ctx.body(), DeleteItemListRequest.class);
        itemService.deleteItemList(session, request.itemListId());
    }

    record PatchItemListRequest(
            int itemListId, String newName
    ) {}
    private void patchItemList(Context ctx)
            throws JsonProcessingException, ResourceNotFoundException, NoAccessToResourceException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PatchItemListRequest request = mapper.readValue(ctx.body(), PatchItemListRequest.class);

        itemService.updateItemList(session,
                request.itemListId,
                request.newName
        );
    }



    // you is true for the logged-in user.
    record ItemListMemberResponse(
            int personId, String firstName, String lastName, String email, ItemListMember.Role role,
            boolean you
    ) {}
    private static ItemListMemberResponse toMemberResponse(ItemListMember member, Session session) {
        int personId = session.getUser().getPerson().getId().orElseThrow();
        return new ItemListMemberResponse(
                member.personId(),
                member.firstName(),
                member.lastName(),
                member.email(),
                member.role(),
                member.personId() == personId
        );
    }
    private static List<ItemListMemberResponse> toMemberResponses(ItemList itemList, Session session) {
        return itemList.getMembers().stream().map(member -> toMemberResponse(member, session)).toList();
    }

    // Shares the list with the user who logs in with the email.
    record PostItemListMemberRequest(
            int itemListId, String email
    ) {}
    private void postItemListMember(Context ctx) throws ResourceNotFoundException, NoAccessToResourceException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        PostItemListMemberRequest request = ctx.bodyAsClass(PostItemListMemberRequest.class);
        ItemListMember member = itemService.shareItemList(session, request.itemListId(), request.email());
        ctx.status(HttpStatus.CREATED).json(toMemberResponse(member, session));
    }

    // Removes a member, or leaves the list when personId is the user's own.
    record DeleteItemListMemberRequest(
            int itemListId, int personId
    ) {}
    private void deleteItemListMember(Context ctx) throws ResourceNotFoundException, NoAccessToResourceException {
        Session session = ctx.attribute(AuthenticationFilter.PARAM_SESSION);
        DeleteItemListMemberRequest request = ctx.bodyAsClass(DeleteItemListMemberRequest.class);
        itemService.removeItemListMember(session, request.itemListId(), request.personId());
    }
}
