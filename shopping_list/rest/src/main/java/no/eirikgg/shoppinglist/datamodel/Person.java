package no.eirikgg.shoppinglist.datamodel;

// A Home Assistant user who has opened the app. haUserId is HA's own id for
// the user, which never changes; name is their display name, which can.
public record Person(
        int id,
        String haUserId,
        String name
) {}
