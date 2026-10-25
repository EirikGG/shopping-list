package no.eirikgg.shoppinglist.datamodel;

// A person with access to a list.
public record ItemListMember(
        int personId,
        String firstName,
        String lastName,
        String email,
        Role role
) {
    public enum Role {
        // Created the list. Can share it, remove members and delete it.
        OWNER,
        // The list has been shared with them. Can change the items and leave.
        MEMBER
    }
}
