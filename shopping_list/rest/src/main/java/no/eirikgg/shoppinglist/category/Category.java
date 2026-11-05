package no.eirikgg.shoppinglist.category;

import java.util.Arrays;
import java.util.Optional;

// The store sections items are sorted into, in the order a shop usually has
// them. The key is what is stored and sent to the web app, which has the
// names in each language.
public enum Category {
    PRODUCE("produce"),
    BAKERY("bakery"),
    DAIRY("dairy"),
    MEAT_FISH("meat_fish"),
    FROZEN("frozen"),
    DRY_GOODS("dry_goods"),
    SPICES_SAUCES("spices_sauces"),
    SNACKS("snacks"),
    DRINKS("drinks"),
    HOUSEHOLD("household"),
    PERSONAL_CARE("personal_care"),
    BABY_PET("baby_pet"),
    // For lists from shops other than grocers.
    TOOLS_HARDWARE("tools_hardware"),
    ELECTRICAL("electrical"),
    FURNITURE_HOME("furniture_home"),
    CLOTHING("clothing"),
    CAR("car"),
    // Nothing in the catalogue was close enough.
    UNCATEGORISED("uncategorised");

    private final String key;

    Category(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Category> fromKey(String key) {
        return Arrays.stream(values()).filter(category -> category.key.equals(key)).findFirst();
    }
}
