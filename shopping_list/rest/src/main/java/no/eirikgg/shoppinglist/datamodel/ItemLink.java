package no.eirikgg.shoppinglist.datamodel;

import java.time.LocalDateTime;

// A link on an item. title is optional.
public record ItemLink(
        int id,
        int itemId,
        String url,
        String title,
        LocalDateTime created
) {}
