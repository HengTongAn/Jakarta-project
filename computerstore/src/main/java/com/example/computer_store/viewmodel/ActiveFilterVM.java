package com.example.computer_store.viewmodel;

/**
 * Chip shown for an active catalogue filter.
 */
public class ActiveFilterVM {

    private final String label;
    private final String url;

    public ActiveFilterVM(String label, String url) {
        this.label = label;
        this.url = url;
    }

    public String getLabel() {
        return label;
    }

    public String getUrl() {
        return url;
    }
}
