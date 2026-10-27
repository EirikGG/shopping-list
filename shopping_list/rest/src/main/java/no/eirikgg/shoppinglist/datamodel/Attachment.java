package no.eirikgg.shoppinglist.datamodel;

import java.time.LocalDateTime;

// A file on an item. fileName is the name it was uploaded with; the file
// itself is stored under storageKey, a random name, in the files folder.
public record Attachment(
        int id,
        int itemId,
        String fileName,
        String contentType,
        long size,
        String storageKey,
        LocalDateTime created
) {}
