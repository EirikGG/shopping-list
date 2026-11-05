package no.eirikgg.shoppinglist.category;

import java.util.Optional;

// Finds the store section an item belongs in from its name.
public interface Categoriser {

    // Empty while the categoriser is not ready, such as while the model is
    // loading, or if it could not be loaded. The item is then categorised
    // later; see CategoryBackfill.
    Optional<Category> categorise(String name);

    // Never ready; items are left without a section.
    Categoriser NONE = name -> Optional.empty();
}
