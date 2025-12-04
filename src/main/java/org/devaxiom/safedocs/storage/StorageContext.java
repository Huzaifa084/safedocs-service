package org.devaxiom.safedocs.storage;

import lombok.Getter;

@Getter
public enum StorageContext {
    PROFILES("profiles"),
    DOCUMENTS("documents"),
    TEMP("temp");

    private final String prefix;

    StorageContext(String prefix) {
        this.prefix = prefix;
    }

}