package no.eirikgg.shoppinglist.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import no.eirikgg.shoppinglist.db.Db;

public class ItemController {

    public static final String PATH_ITEM_LIST = "/list";
    public static final String PATH_ITEM = "/item";


    private Db db;

    public ItemController(Db db) {
        this.db = db;
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
    private void saveItemList(Context ctx) {}
    private void deleteItemList(Context ctx) {}
    private void patchItemList(Context ctx) {}
}