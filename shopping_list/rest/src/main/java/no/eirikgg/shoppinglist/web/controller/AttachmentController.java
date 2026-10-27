package no.eirikgg.shoppinglist.web.controller;

import io.javalin.config.JavalinConfig;
import io.javalin.config.SizeUnit;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;
import no.eirikgg.shoppinglist.datamodel.Attachment;
import no.eirikgg.shoppinglist.datamodel.Person;
import no.eirikgg.shoppinglist.db.Db;
import no.eirikgg.shoppinglist.errors.Error;
import no.eirikgg.shoppinglist.errors.InvalidInputException;
import no.eirikgg.shoppinglist.errors.ResourceNotFoundException;
import no.eirikgg.shoppinglist.service.AttachmentService;
import no.eirikgg.shoppinglist.web.filter.IngressFilter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class AttachmentController {

    public static final String PATH_ATTACHMENT = "/api/attachment";
    // The file itself. A path rather than a body, so it works as an image's src.
    public static final String PATH_ATTACHMENT_FILE = "/api/attachment/{id}";

    public static final String FORM_ITEM_ID = "itemId";
    public static final String FORM_FILE = "file";
    // ?download=true sends even an image as a download.
    public static final String PARAM_DOWNLOAD = "download";

    // Shown in the page. Anything else, SVG and HTML especially, is only ever
    // downloaded, so an uploaded file cannot run script in the app.
    static final Set<String> INLINE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/avif"
    );

    private AttachmentService attachmentService;

    public AttachmentController(Db db, Path filesFolder) {
        this.attachmentService = new AttachmentService(db, filesFolder);
    }

    public static void setup(JavalinConfig config, Db db, Path filesFolder) {
        AttachmentController ac = new AttachmentController(db, filesFolder);

        // Above the file limit, so the service refuses a large file with its
        // own error. Ingress stops anything much bigger before it gets here.
        config.jetty.multipartConfig.maxFileSize(64, SizeUnit.MB);
        config.jetty.multipartConfig.maxTotalRequestSize(64, SizeUnit.MB);
        config.jetty.multipartConfig.maxInMemoryFileSize(1, SizeUnit.MB);

        config.routes.post(PATH_ATTACHMENT, ac::upload);
        config.routes.get(PATH_ATTACHMENT_FILE, ac::download);
        config.routes.delete(PATH_ATTACHMENT, ac::delete);
    }


    // A multipart form with itemId and one file.
    private void upload(Context ctx) throws ResourceNotFoundException, InvalidInputException, IOException {
        Person person = ctx.attribute(IngressFilter.PARAM_PERSON);
        int itemId = ctx.formParamAsClass(FORM_ITEM_ID, Integer.class).get();
        UploadedFile file = ctx.uploadedFile(FORM_FILE);
        if (null == file) {
            throw new InvalidInputException(Error.NO_FILE, "No file in the upload");
        }

        Attachment attachment;
        try (InputStream content = file.content()) {
            attachment = attachmentService.upload(person, itemId, file.filename(), file.contentType(), content);
        }
        ctx.status(HttpStatus.CREATED).json(ItemController.toAttachmentResponse(attachment));
    }


    private void download(Context ctx) throws IOException {
        int attachmentId = ctx.pathParamAsClass("id", Integer.class).get();
        AttachmentService.StoredFile stored = attachmentService.open(attachmentId).orElseThrow(
                () -> new NotFoundResponse("Attachment not found")
        );
        Attachment attachment = stored.attachment();

        boolean inline = INLINE_TYPES.contains(attachment.contentType())
                && !"true".equals(ctx.queryParam(PARAM_DOWNLOAD));
        ctx.contentType(inline ? attachment.contentType() : "application/octet-stream");
        ctx.header("Content-Disposition", contentDisposition(inline, attachment.fileName()));
        // The browser keeps to the type above, and nothing in the file runs.
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox");
        // A file never changes: a new upload gets a new id.
        ctx.header("Cache-Control", "private, max-age=31536000, immutable");
        ctx.header("Content-Length", Long.toString(Files.size(stored.path())));
        ctx.result(Files.newInputStream(stored.path()));
    }


    record DeleteAttachmentRequest(int id) {}
    private void delete(Context ctx) throws ResourceNotFoundException {
        DeleteAttachmentRequest request = ctx.bodyAsClass(DeleteAttachmentRequest.class);
        attachmentService.delete(request.id());
    }


    // The name as it was uploaded, in a form every browser reads: plain ASCII
    // in filename, the real name in filename*.
    static String contentDisposition(boolean inline, String fileName) {
        String ascii = fileName.replaceAll("[^\\x20-\\x7E]|[\"\\\\]", "_");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "%s; filename=\"%s\"; filename*=UTF-8''%s".formatted(inline ? "inline" : "attachment", ascii, encoded);
    }
}
