package com.dungeoncrawler;

public class Item implements Comparable<Item> {
    private final String name;
    private final int value;

    public Item(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return name; }
    public int getValue() { return value; }

    @Override
    public int compareTo(Item other) {
        return this.name.compareToIgnoreCase(other.name);
    }
}
