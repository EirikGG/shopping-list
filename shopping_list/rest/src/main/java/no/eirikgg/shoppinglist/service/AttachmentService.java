package no.eirikgg.shoppinglist.service;

import no.eirikgg.shoppinglist.datamodel.Attachment;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.db.repository.AttachmentRepository;
import no.eirikgg.shoppinglist.db.repository.ItemRepository;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.errors.InvalidInputException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Files on items. Each is stored in the files folder under a random name, so
// an uploaded name can never point outside it; the database keeps the name it
// was uploaded with. In the add-on the folder is /data/files, which Home
// Assistant keeps across updates and backs up.
public class AttachmentService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    // Home Assistant's ingress refuses requests over about 16 MB, so a larger
    // file could not be uploaded through it anyway.
    public static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    private static final int MAX_FILE_NAME_LENGTH = 256;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    // A type such as "image/jpeg", with nothing after it.
    private static final Pattern CONTENT_TYPE = Pattern.compile("^[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+$");
    // Being written. Never served, and removed if left behind.
    private static final String PART_SUFFIX = ".part";

    private Db db;
    private Path folder;
    private AttachmentRepository attachmentRepository = new AttachmentRepository();
    private ItemRepository itemRepository = new ItemRepository();

    public AttachmentService(Db db, Path folder) {
        this.db = db;
        this.folder = folder;
    }

    // An attachment and where its file is.
    public record StoredFile(Attachment attachment, Path path) {}

    // Stores the file and adds it to the item. Refused if it is over
    // MAX_FILE_BYTES, counted as it is read rather than trusting the size sent.
    public Attachment upload(Person person, int itemId, String fileName, String contentType, InputStream content)
            throws ResourceNotFoundException, InvalidInputException {
        try (Connection conn = db.getConnection()) {
            if (itemRepository.getItem(conn, itemId).isEmpty()) {
                throw new ResourceNotFoundException("Item not found");
            }

            Files.createDirectories(folder);
            String storageKey = UUID.randomUUID().toString();
            Path part = folder.resolve(storageKey + PART_SUFFIX);
            Path file = folder.resolve(storageKey);

            long size;
            try {
                size = copyAtMost(content, part, MAX_FILE_BYTES);
                Files.move(part, file, StandardCopyOption.ATOMIC_MOVE);
            }
            finally {
                Files.deleteIfExists(part);
            }

            try {
                int attachmentId = attachmentRepository.createAttachment(
                        conn, person.id(), itemId,
                        cleanFileName(fileName), cleanContentType(contentType), size, storageKey
                );
                return attachmentRepository.getAttachment(conn, attachmentId).orElseThrow();
            }
            catch (SQLException | RuntimeException e) {
                Files.deleteIfExists(file);
                throw e;
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Empty if the attachment is deleted, or its file is missing.
    public Optional<StoredFile> open(int attachmentId) {
        try (Connection conn = db.getConnection()) {
            return attachmentRepository.getAttachment(conn, attachmentId)
                    .map(attachment -> new StoredFile(attachment, folder.resolve(attachment.storageKey())))
                    .filter(stored -> Files.isRegularFile(stored.path()));
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // The file is removed straight away; the row is kept, marked deleted.
    public void delete(int attachmentId) throws ResourceNotFoundException {
        try (Connection conn = db.getConnection()) {
            Attachment attachment = attachmentRepository.getAttachment(conn, attachmentId).orElseThrow(
                    () -> new ResourceNotFoundException("Attachment not found")
            );
            attachmentRepository.deleteAttachment(conn, attachmentId);
            Files.deleteIfExists(folder.resolve(attachment.storageKey()));
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Removes every file no attachment uses any more: those of deleted items
    // and lists, and uploads that never finished. Run at startup, before any
    // upload can be under way. Returns how many were removed.
    public int removeUnusedFiles() {
        if (!Files.isDirectory(folder)) {
            return 0;
        }

        try (Connection conn = db.getConnection(); Stream<Path> files = Files.list(folder)) {
            Set<String> inUse = attachmentRepository.getStorageKeysInUse(conn);
            int removed = 0;
            for (Path file : files.toList()) {
                if (Files.isRegularFile(file) && !inUse.contains(file.getFileName().toString())) {
                    Files.delete(file);
                    removed++;
                }
            }
            if (removed > 0) {
                log.info("Removed {} files no longer in use from {}", removed, folder);
            }
            return removed;
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Copies until the end, or fails as soon as more than max bytes come.
    private static long copyAtMost(InputStream in, Path target, long max) throws IOException, InvalidInputException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > max) {
                    throw new InvalidInputException(Error.FILE_TOO_LARGE, "File is larger than " + max + " bytes");
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    // Only the name, without any folder a browser may have sent, and without
    // control characters, which could break the header it is sent back in.
    static String cleanFileName(String fileName) {
        String name = null == fileName ? "" : fileName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(0, MAX_FILE_NAME_LENGTH) : name;
    }

    private static String cleanContentType(String contentType) {
        if (null == contentType) {
            return DEFAULT_CONTENT_TYPE;
        }
        String type = contentType.trim().toLowerCase();
        return CONTENT_TYPE.matcher(type).matches() ? type : DEFAULT_CONTENT_TYPE;
    }
}
