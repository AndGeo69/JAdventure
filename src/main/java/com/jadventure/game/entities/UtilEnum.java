package com.jadventure.game.entities;

public enum UtilEnum {
    HEALTH("health"),
    HEALTH_MAX("healthMax"),
    ARMOR("armor"),
    DAMAGE("damage"),
    LEVEL("level"),
    INTELLIGENCE("intelligence"),
    DEXTERITY("dexterity"),
    ITEMS("items");

    public final String description;

    UtilEnum(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return this.description;
    }
}
