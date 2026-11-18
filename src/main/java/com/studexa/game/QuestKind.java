package com.studexa.game;

import jakarta.persistence.EnumeratedValue;

/**
 * Quest kind: daily or weekly.
 */
public enum QuestKind {
    DAILY("daily"),
    WEEKLY("weekly");

    @EnumeratedValue
    private final String databaseValue;

    QuestKind(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
