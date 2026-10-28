package no.eirikgg.shoppinglist.web.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.datamodel.Label;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.NoAccessToResourceException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import no.eirikgg.shoppinglist.service.LabelService;
import no.eirikgg.shoppinglist.service.ListChanges;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class LabelController {

    public static final String PATH_LABEL = "/api/label";
    public static final String PATH_LABEL_LIST = "/api/label-list";
    public static final String PATH_LABEL_ITEM_MAPPING = "/api/label-item-mapping";

    private LabelService labelService;

    public LabelController(Db db, ListChanges changes) {
        this.labelService = new LabelService(db, changes);
    }

    public static void setup(JavalinConfig config, Db db, ListChanges changes) {
        LabelController sc = new LabelController(db, changes);
        config.routes.post(PATH_LABEL, sc::createLabel);
        config.routes.get(PATH_LABEL, sc::getLabel);
        config.routes.patch(PATH_LABEL, sc::patchLabel);
        config.routes.delete(PATH_LABEL, sc::deleteLabel);
        config.routes.get(PATH_LABEL_LIST, sc::getLabelList);

        config.routes.post(PATH_LABEL_ITEM_MAPPING, sc::mapLabelAndItem);
        config.routes.delete(PATH_LABEL_ITEM_MAPPING, sc::unmapLabelFromItem);
    }


    // A label is created on a list, and only used on that list.
    record PostLabelRequest(int itemListId, String name) {}
    record PostLabelResponse(int labelId) {}
    private void createLabel(Context ctx) throws ResourceNotFoundException {
        Person person = ctx.attribute(IngressFilter.PARAM_PERSON);
        PostLabelRequest request = ctx.bodyAsClass(PostLabelRequest.class);
        Label label = labelService.createLabel(person, request.itemListId(), request.name());
        Integer labelId = label.getId().orElseThrow();
        PostLabelResponse responseBody = new PostLabelResponse(labelId);
        ctx.status(HttpStatus.CREATED).json(responseBody);
    }


    record GetLabelRequest(int labelId) {}
    record GetLabelResponse(int labelId, String name, LocalDateTime created) {}
    private void getLabel(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        GetLabelRequest request = ctx.bodyAsClass(GetLabelRequest.class);
        Label label = labelService.getLabel(request.labelId());

        ctx.status(HttpStatus.OK).json(new GetLabelResponse(
                label.getId().orElseThrow(),
                label.getName(),
                label.getCreated()
        ));
    }


    // The labels of a list, sorted by name: GET /label-list?itemListId=1
    public static final String PARAM_ITEM_LIST_ID = "itemListId";
    private void getLabelList(Context ctx) throws ResourceNotFoundException {
        int itemListId = ctx.queryParamAsClass(PARAM_ITEM_LIST_ID, Integer.class).get();
        List<GetLabelResponse> response = labelService.getLabels(itemListId).stream()
                .map(label -> new GetLabelResponse(
                        label.getId().orElseThrow(),
                        label.getName(),
                        label.getCreated()
                ))
                .toList();

        ctx.status(HttpStatus.OK).json(response);
    }


    record PatchLabelRequest(int labelId, String newName) {}
    private void patchLabel(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        PatchLabelRequest request = ctx.bodyAsClass(PatchLabelRequest.class);
        int labelId = request.labelId();
        String newName = request.newName();

        labelService.patchLabel(labelId, newName);
    }


    record DeleteLabelRequest(int labelId) {}
    private void deleteLabel(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        DeleteLabelRequest request = ctx.bodyAsClass(DeleteLabelRequest.class);
        int labelId = request.labelId();

        labelService.deleteLabel(labelId);
    }



    record MapLabelAndItemRequest(int labelId, int itemId) {}
    private void mapLabelAndItem(Context ctx) throws NoAccessToResourceException, ResourceNotFoundException {
        MapLabelAndItemRequest request = ctx.bodyAsClass(MapLabelAndItemRequest.class);
        int labelId = request.labelId();
        int itemId = request.itemId();

        labelService.mapLabelAndItem(ctx.attribute(IngressFilter.PARAM_PERSON), labelId, itemId);
    }


    record UnmapLabelAndItemRequest(int labelId, int itemId) {}
    private void unmapLabelFromItem(Context ctx) throws ResourceNotFoundException {
        UnmapLabelAndItemRequest request = ctx.bodyAsClass(UnmapLabelAndItemRequest.class);

        labelService.unmapLabelFromItem(ctx.attribute(IngressFilter.PARAM_PERSON), request.labelId(), request.itemId());
    }
}
