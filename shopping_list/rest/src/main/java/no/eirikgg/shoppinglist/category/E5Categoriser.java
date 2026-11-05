package no.eirikgg.shoppinglist.category;

import com.fasterxml.jackson.core.type.TypeReference;
import no.eirikgg.shoppinglist.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

// Sorts an item into a store section by comparing its name with common items
// whose section is known (categories/catalogue.json). Each section scores the
// mean similarity of its few closest items; the best section is chosen if it
// scores high enough, and the item is uncategorised otherwise. Names that are
// not groceries at all, like "call the dentist", can still land in a section.
public final class E5Categoriser implements Categoriser, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(E5Categoriser.class);

    private static final String CATALOGUE = "/categories/catalogue.json";
    // How many of a section's closest items make up its score.
    private static final int TOP = 3;
    // e5 similarities between short texts are mostly 0.80-0.95, so this is set
    // from CategoriserEvaluationTest rather than by feel: groceries the
    // catalogue does not have score from about 0.84, and a margin over the next
    // section only left more groceries uncategorised.
    static final float MIN_SCORE = 0.84f;
    // Names already seen, so lists of the same things cost nothing.
    private static final int CACHE_SIZE = 2000;

    private final E5Embedder embedder;
    private final List<Category> entryCategories = new ArrayList<>();
    private final List<String> entryNames = new ArrayList<>();
    private final float[][] entryVectors;
    private final Map<String, Category> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Category> eldest) {
                    return size() > CACHE_SIZE;
                }
            }
    );

    // Embeds the whole catalogue, which takes some seconds on a small
    // computer; see loadInBackground.
    public E5Categoriser(Path modelDir) throws Exception {
        embedder = new E5Embedder(modelDir);
        for (Map.Entry<Category, List<String>> section : readCatalogue().entrySet()) {
            for (String name : section.getValue()) {
                entryCategories.add(section.getKey());
                entryNames.add(name);
            }
        }
        entryVectors = new float[entryNames.size()][];
        // One at a time, as item names are: the quantized model scales its
        // numbers by everything in a batch, padding too, so a name embedded in
        // a batch comes out slightly different.
        for (int i = 0; i < entryNames.size(); i++) {
            entryVectors[i] = embedder.embed(entryNames.get(i));
        }
    }

    // Ready straight away, but categorises nothing until the model is loaded.
    // If the model is missing or fails to load, items are never categorised,
    // and the app works as before. onReady is given the categoriser once it is
    // loaded.
    public static Categoriser loadInBackground(Path modelDir, Consumer<Categoriser> onReady) {
        LazyCategoriser lazy = new LazyCategoriser();
        if (!Files.isRegularFile(modelDir.resolve(E5Embedder.MODEL_FILE))) {
            log.warn("No model in {}, so items are not sorted into sections", modelDir.toAbsolutePath());
            return lazy;
        }
        Thread loader = new Thread(() -> {
            long start = System.nanoTime();
            try {
                lazy.loaded = new E5Categoriser(modelDir);
                log.info("Loaded the section model in {} ms", (System.nanoTime() - start) / 1_000_000);
                onReady.accept(lazy);
            }
            catch (Throwable e) {
                log.error("Could not load the section model from {}; items are not sorted into sections",
                        modelDir.toAbsolutePath(), e);
            }
        }, "category-model");
        loader.setDaemon(true);
        loader.start();
        return lazy;
    }

    private static final class LazyCategoriser implements Categoriser {
        private volatile E5Categoriser loaded;

        @Override
        public Optional<Category> categorise(String name) {
            E5Categoriser categoriser = loaded;
            return null == categoriser ? Optional.empty() : categoriser.categorise(name);
        }
    }

    @Override
    public Optional<Category> categorise(String name) {
        String key = null == name ? "" : name.strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return Optional.of(Category.UNCATEGORISED);
        }
        Category cached = cache.get(key);
        if (null != cached) {
            return Optional.of(cached);
        }
        try {
            Category category = choose(scores(key));
            cache.put(key, category);
            return Optional.of(category);
        }
        catch (Exception e) {
            log.warn("Could not categorise an item", e);
            return Optional.empty();
        }
    }

    // How well the name matches each section, best first.
    List<Map.Entry<Category, Float>> scores(String name) throws Exception {
        float[] vector = embedder.embed(name);
        Map<Category, List<Float>> similarities = new EnumMap<>(Category.class);
        for (int i = 0; i < entryVectors.length; i++) {
            similarities.computeIfAbsent(entryCategories.get(i), c -> new ArrayList<>())
                    .add(E5Embedder.dot(vector, entryVectors[i]));
        }
        List<Map.Entry<Category, Float>> scores = new ArrayList<>();
        for (Map.Entry<Category, List<Float>> section : similarities.entrySet()) {
            float[] best = new float[section.getValue().size()];
            for (int i = 0; i < best.length; i++) {
                best[i] = section.getValue().get(i);
            }
            Arrays.sort(best);
            float sum = 0;
            int count = Math.min(TOP, best.length);
            for (int i = 0; i < count; i++) {
                sum += best[best.length - 1 - i];
            }
            scores.add(Map.entry(section.getKey(), sum / count));
        }
        scores.sort(Map.Entry.<Category, Float>comparingByValue().reversed());
        return scores;
    }

    static Category choose(List<Map.Entry<Category, Float>> scores) {
        return scores.get(0).getValue() >= MIN_SCORE ? scores.get(0).getKey() : Category.UNCATEGORISED;
    }

    static Map<Category, List<String>> readCatalogue() {
        try (InputStream in = E5Categoriser.class.getResourceAsStream(CATALOGUE)) {
            Map<String, List<String>> byKey = Json.MAPPER.readValue(in, new TypeReference<>() {});
            Map<Category, List<String>> catalogue = new EnumMap<>(Category.class);
            byKey.forEach((key, names) -> catalogue.put(
                    Category.fromKey(key).orElseThrow(() -> new IllegalStateException("Unknown section " + key)),
                    names
            ));
            return catalogue;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws Exception {
        embedder.close();
    }
}
