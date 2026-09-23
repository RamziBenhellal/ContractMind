package com.ramzi.backend.entity;

import java.util.Locale;

public enum AccountType {
    GIROKONTO,
    KREDITKARTE,
    PAYPAL,
    KRYPTO;

    /**
     * Maps a FinTS/bank label such as "Girokonto" onto the persisted enum.
     * Unknown labels default to {@link #GIROKONTO}.
     */
    public static AccountType fromBankLabel(String label) {
        if (label == null || label.isBlank()) {
            return GIROKONTO;
        }
        String normalized = label.trim().toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
        try {
            return AccountType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // fall through to label heuristics
        }
        if (normalized.contains("KREDIT") || normalized.contains("CREDIT") || normalized.contains("CARD")) {
            return KREDITKARTE;
        }
        if (normalized.contains("PAYPAL")) {
            return PAYPAL;
        }
        if (normalized.contains("KRYPTO") || normalized.contains("CRYPTO") || normalized.contains("BITCOIN")) {
            return KRYPTO;
        }
        return GIROKONTO;
    }
}
