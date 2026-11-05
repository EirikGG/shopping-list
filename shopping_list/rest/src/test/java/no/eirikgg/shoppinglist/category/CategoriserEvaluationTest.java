package no.eirikgg.shoppinglist.category;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static no.eirikgg.shoppinglist.category.Category.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// How well the real model sorts names that are not in the catalogue, as
// people write them: brands, typos, amounts, both languages. Prints every
// score, which is what MIN_SCORE is set from. Only runs with the
// model, given as -DMODEL.DIR or MODEL_DIR; the Dockerfile downloads it.
class CategoriserEvaluationTest {

    private static final Map<String, Category> EXPECTED = new LinkedHashMap<>();

    static {
        expect(PRODUCE, "bananer", "4 epler", "grønne epler", "isberg", "cherrytomat", "agurk", "rød paprika",
                "brokkoli", "potet", "nypoteter", "gulrot", "løk", "hvitløk", "avokadoer", "sitroner", "persille",
                "blåbær", "pear", "red onion", "baby spinach", "sweet potato");
        expect(BAKERY, "grovbrød", "kneippbrød", "rundstykker", "pølsebrød", "lomper", "tortilla", "knekkebrød",
                "baguetter", "sourdough", "burger buns", "boller");
        expect(DAIRY, "lettmelk", "tine lettmelk", "melk", "matfløte", "rømme", "yoghurt", "smør", "norvegia",
                "jarlsberg", "brunost", "egg", "12 egg", "revet ost", "skyr", "cheddar cheese", "milk", "butter");
        expect(MEAT_FISH, "kjøttdeig", "kyllingfilet", "kyllingkjøttdeig", "bacon", "grillpølser", "laks",
                "torskefilet", "fiskekaker", "reker", "skinke", "pålegg skinke", "chicken", "ground beef",
                "salmon fillet");
        expect(FROZEN, "grandiosa", "frossenpizza", "is", "frosne erter", "pommes frites", "frozen pizza",
                "ice cream", "fiskepinner frosne");
        expect(DRY_GOODS, "spaghetti", "pasta", "ris", "havregryn", "hvetemel", "sukker", "hakkede tomater",
                "kikerter", "tacoskjell", "kokosmelk", "nudler", "cornflakes", "rice", "flour");
        expect(SPICES_SAUCES, "salt", "pepper", "ketchup", "sennep", "majones", "olivenolje", "soyasaus",
                "tacokrydder", "pesto", "oregano", "soy sauce", "olive oil");
        expect(SNACKS, "potetgull", "smågodt", "sjokolade", "freia melkesjokolade", "kvikk lunsj", "peanøtter",
                "kjeks", "popcorn", "chips", "candy");
        expect(DRINKS, "cola", "pepsi max", "farris", "appelsinjuice", "kaffe", "te", "øl", "brus", "coffee",
                "orange juice", "red wine");
        expect(HOUSEHOLD, "dopapir", "tørkerull", "oppvaskmiddel", "zalo", "vaskemiddel", "søppelposer",
                "bakepapir", "batterier", "lyspære til gangen", "toilet paper", "dishwasher tabs");
        expect(PERSONAL_CARE, "sjampo", "tannkrem", "tannbørste", "deodorant", "dusjsåpe", "solkrem", "plaster",
                "paracet", "shampoo", "toothpaste");
        expect(BABY_PET, "bleier", "våtservietter", "kattemat", "hundemat", "kattesand", "diapers", "dog food");
        expect(UNCATEGORISED, "ring mamma", "betale strømregning", "bursdagsgave til Ola", "hente pakke",
                "bok", "call the dentist");
    }

    private static void expect(Category category, String... names) {
        for (String name : names) {
            EXPECTED.put(name, category);
        }
    }

    private static E5Categoriser categoriser;

    @BeforeAll
    static void load() throws Exception {
        String dir = System.getProperty("MODEL.DIR", System.getenv("MODEL_DIR"));
        assumeTrue(null != dir && Files.isRegularFile(Path.of(dir, E5Embedder.MODEL_FILE)),
                "No model; set -DMODEL.DIR to run");
        categoriser = new E5Categoriser(Path.of(dir));
    }

    @AfterAll
    static void close() throws Exception {
        if (null != categoriser) {
            categoriser.close();
        }
    }

    @Test
    void sortsMostNamesRight() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Category> expected : EXPECTED.entrySet()) {
            String name = expected.getKey();
            List<Map.Entry<Category, Float>> scores = categoriser.scores(name.toLowerCase());
            Category got = E5Categoriser.choose(scores);
            String line = String.format("%-26s %-14s %-14s %s %.3f, %s %.3f", name, expected.getValue().key(),
                    got.key(), scores.get(0).getKey().key(), scores.get(0).getValue(),
                    scores.get(1).getKey().key(), scores.get(1).getValue());
            System.out.println((got == expected.getValue() ? "  " : "X ") + line);
            if (got != expected.getValue()) {
                wrong.add(line);
            }
        }
        double accuracy = 1 - (double) wrong.size() / EXPECTED.size();
        System.out.printf("Accuracy %.1f %% (%d of %d wrong)%n", accuracy * 100, wrong.size(), EXPECTED.size());
        assertTrue(accuracy >= 0.85, "Too many wrong:\n" + String.join("\n", wrong));
    }

    @Test
    void catalogueNames_sortIntoTheirOwnSection() {
        assertEquals(DAIRY, categoriser.categorise("Lettmelk").orElseThrow());
        assertEquals(PRODUCE, categoriser.categorise("  bananer ").orElseThrow());
        assertEquals(UNCATEGORISED, categoriser.categorise("").orElseThrow());
    }
}
