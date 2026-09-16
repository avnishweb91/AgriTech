package com.example.smarthub.services;

import java.util.Locale;

/** Small deterministic intent parser for Hindi and the supported Bihar dialects. */
public final class VoiceCommandService {
    private VoiceCommandService() {}

    public enum Intent { PRICES, MARKETPLACE, WEATHER, COLD_STORAGE, HELP, UNKNOWN }

    public static Intent parse(String transcript) {
        if (transcript == null) return Intent.UNKNOWN;
        String text = transcript.toLowerCase(Locale.ROOT);
        if (contains(text, "भाव", "दाम", "कीमत", "भाउ", "भावे", "price")) return Intent.PRICES;
        if (contains(text, "बेच", "खरीद", "buyer", "mandi", "मंडी", "बजार", "बाज़ार")) return Intent.MARKETPLACE;
        if (contains(text, "मौसम", "बारिश", "पानी", "weather")) return Intent.WEATHER;
        if (contains(text, "कोल्ड", "ठंडा घर", "भंडार", "storage", "गोदाम")) return Intent.COLD_STORAGE;
        if (contains(text, "मदद", "कैसे", "help")) return Intent.HELP;
        return Intent.UNKNOWN;
    }

    private static boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }
}
