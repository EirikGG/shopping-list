package no.eirikgg.shoppinglist.web.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.shoppinglist.datamodel.ItemLink;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.InvalidInputException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import no.eirikgg.shoppinglist.service.ItemLinkService;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;

public class ItemLinkController {

    public static final String PATH_ITEM_LINK = "/api/item-link";

    private ItemLinkService itemLinkService;

    public ItemLinkController(Db db) {
        this.itemLinkService = new ItemLinkService(db);
    }

    public static void setup(JavalinConfig config, Db db) {
        ItemLinkController lc = new ItemLinkController(db);
        config.routes.post(PATH_ITEM_LINK, lc::createLink);
        config.routes.delete(PATH_ITEM_LINK, lc::deleteLink);
    }


    // title is optional. A url without a scheme is taken to be https.
    record PostItemLinkRequest(int itemId, String url, String title) {}
    private void createLink(Context ctx) throws ResourceNotFoundException, InvalidInputException {
        Person person = ctx.attribute(IngressFilter.PARAM_PERSON);
        PostItemLinkRequest request = ctx.bodyAsClass(PostItemLinkRequest.class);
        ItemLink link = itemLinkService.createLink(person, request.itemId(), request.url(), request.title());
        ctx.status(HttpStatus.CREATED).json(ItemController.toLinkResponse(link));
    }


    record DeleteItemLinkRequest(int id) {}
    private void deleteLink(Context ctx) throws ResourceNotFoundException {
        DeleteItemLinkRequest request = ctx.bodyAsClass(DeleteItemLinkRequest.class);
        itemLinkService.deleteLink(request.id());
    }
}
