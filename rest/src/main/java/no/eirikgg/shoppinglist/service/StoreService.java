package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.db.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoreService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private Db db;

    public StoreService(Db db) {
        this.db = db;
    }

    public void createStore() {

    }
}
