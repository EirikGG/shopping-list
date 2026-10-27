package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.ItemLink;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.ItemLinkRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.errors.InvalidInputException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.regex.Pattern;

// Links on items. Only http and https links are kept, so a link can never run
// script in the app when opened.
public class ItemLinkService {
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_TITLE_LENGTH = 256;

    // Starts with a scheme, such as "https:" or "javascript:".
    private static final Pattern HAS_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    private Db db;
    private ItemLinkRepository itemLinkRepository = new ItemLinkRepository();
    private ItemRepository itemRepository = new ItemRepository();

    public ItemLinkService(Db db) {
        this.db = db;
    }

    // title is optional.
    public ItemLink createLink(Person person, int itemId, String url, String title)
            throws ResourceNotFoundException, InvalidInputException {
        String validUrl = validUrl(url);
        try (Connection conn = db.getConnection()) {
            if (itemRepository.getItem(conn, itemId).isEmpty()) {
                throw new ResourceNotFoundException("Item not found");
            }
            int linkId = itemLinkRepository.createLink(conn, person.id(), itemId, validUrl, cleanTitle(title));
            return itemLinkRepository.getLink(conn, linkId).orElseThrow();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteLink(int linkId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            ItemLink link = itemLinkRepository.getLink(conn, linkId).orElseThrow(
                    () -> new ResourceNotFoundException("Link not found")
            );
            if (itemRepository.getItem(conn, link.itemId()).isEmpty()) {
                throw new ResourceNotFoundException("Link not found");
            }
            itemLinkRepository.deleteLink(conn, linkId);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // An http or https address with a host. One typed without a scheme, such
    // as "example.com/recipe", is taken to be https.
    static String validUrl(String url) throws InvalidInputException {
        String trimmed = null == url ? "" : url.trim();
        String withScheme = HAS_SCHEME.matcher(trimmed).find() ? trimmed : "https://" + trimmed;
        if (trimmed.isEmpty() || withScheme.length() > MAX_URL_LENGTH) {
            throw new InvalidInputException(Error.INVALID_URL, "Not a valid link");
        }

        try {
            URI uri = new URI(withScheme);
            String scheme = null == uri.getScheme() ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean web = scheme.equals("http") || scheme.equals("https");
            if (!web || null == uri.getHost() || uri.getHost().isBlank()) {
                throw new InvalidInputException(Error.INVALID_URL, "Only http and https links");
            }
            return withScheme;
        }
        catch (URISyntaxException e) {
            throw new InvalidInputException(Error.INVALID_URL, "Not a valid link");
        }
    }

    // Blank becomes no title; a long one is cut.
    private static String cleanTitle(String title) {
        if (null == title || title.isBlank()) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.length() > MAX_TITLE_LENGTH ? trimmed.substring(0, MAX_TITLE_LENGTH) : trimmed;
    }
}
