package no.eirikgg.data.sqllite.db;

import java.util.Optional;

public abstract class DbIdentity {
    protected Integer id;

    public DbIdentity(Integer id) {
        this.id = id;
    }

    public Optional<Integer> getId() {
        return Optional.ofNullable(id);
    }
}