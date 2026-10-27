package no.eirikgg.shoppinglist.errors;

public enum Error {

    NO_ACCESS,
    RESOURCE_NOT_FOUND,

    // A link that is not an http or https address.
    INVALID_URL,
    // A file over the size limit, or an upload with no file.
    FILE_TOO_LARGE,
    NO_FILE
}
