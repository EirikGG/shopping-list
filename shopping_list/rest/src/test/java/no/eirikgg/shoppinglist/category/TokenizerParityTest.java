package no.eirikgg.shoppinglist.category;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// UnigramTokenizer gives the same ids as the HuggingFace tokenizer, so the
// model sees what it was trained on. Only runs with the model, given as
// -DMODEL.DIR or MODEL_DIR.
class TokenizerParityTest {

    private static final List<String> TRICKY = List.of(
            "Tine lettmelk", "12 egg", "2 × melk", "crème fraîche", "Æbleskiver", "øl & brus", "Ål",
            "ﬁskekaker", "Ｆｕｌｌｗｉｄｔｈ", "melk  og   brød", "café au lait", "jalapeños", "smør!!!", "50% sjokolade",
            "😀 kake", "日本茶", "Москва", "q-tips", "pepsi-max 1,5l", "▁already marked", "x", "",
            "a".repeat(200)
    );

    @Test
    void sameIdsAsHuggingFace() throws Exception {
        String dir = System.getProperty("MODEL.DIR", System.getenv("MODEL_DIR"));
        assumeTrue(null != dir && Files.isRegularFile(Path.of(dir, E5Embedder.TOKENIZER_FILE)),
                "No model; set -DMODEL.DIR to run");
        Path file = Path.of(dir, E5Embedder.TOKENIZER_FILE);

        List<String> texts = new ArrayList<>(TRICKY);
        E5Categoriser.readCatalogue().values().forEach(texts::addAll);

        UnigramTokenizer ours = UnigramTokenizer.load(file);
        try (HuggingFaceTokenizer theirs = HuggingFaceTokenizer.newInstance(file, Map.of(
                "maxLength", "64", "truncation", "true", "padding", "false"
        ))) {
            for (String text : texts) {
                String input = "query: " + text.strip().toLowerCase(Locale.ROOT);
                assertEquals(
                        Arrays.toString(theirs.encode(input).getIds()),
                        Arrays.toString(ours.encode(input, 64)),
                        "Different ids for \"" + input + "\""
                );
            }
        }
    }
}
