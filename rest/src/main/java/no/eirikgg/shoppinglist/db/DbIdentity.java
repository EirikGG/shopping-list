package no.eirikgg.shoppinglist.db;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class DbIdentity {

    @JsonProperty(value = "id",access = JsonProperty.Access.READ_ONLY)
    protected Integer id;

    public DbIdentity(Integer id) {
        this.id = id;
    }

    public Optional<Integer> getId() {
        return Optional.ofNullable(id);
    }

    @JsonGetter("id")
    public Integer getIdAsInteger() {
        return id;
    }
}