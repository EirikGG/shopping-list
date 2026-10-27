package no.eirikgg.shoppinglist.db;

public class Hidden<T> {
    public T value;

    private Hidden(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public static <T> Hidden<T> param(T param) {
        return new Hidden<>(param);
    }
}
