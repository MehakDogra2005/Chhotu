package com.dukaansaathi.voice;

import java.util.List;
import java.util.Optional;

/**
 * The voices of Gnani's Timbre v3.0 text-to-speech model, as listed in Gnani's "Available Voices" documentation
 * (https://docs.gnani.ai/api/TTS/available-voices.md). Voices are not shared between Gnani models: a voice from another model
 * is rejected by Gnani with 400, so this app uses timbre-v3.0 for every spoken reply and only offers these names.
 * All of them speak Hindi, English and the other supported languages, so choosing a voice never changes the language.
 */
public final class VoiceCatalog {
    /** gender and persona are Gnani's own descriptions, shown on the Settings page. */
    public record Voice(String name, String gender, String persona) {}

    /** The Gnani model that speaks every reply. */
    public static final String MODEL = "timbre-v3.0";

    /** Used until the shopkeeper chooses one. */
    public static final String DEFAULT = "Shivani";

    public static final List<Voice> VOICES = List.of(
            new Voice("Avinash", "Male", "Professional"),
            new Voice("Shivani", "Female", "Warm, empathetic"),
            new Voice("Lakshmi", "Female", "Expressive, energetic"),
            new Voice("Kalpana", "Female", "Warm, empathetic"),
            new Voice("Shubham", "Male", "Expressive, energetic"),
            new Voice("Prakash", "Male", "Professional"),
            new Voice("Devansh", "Male", "Conversational"),
            new Voice("Yashraj", "Male", "Warm, empathetic"),
            new Voice("Chetana", "Female", "Warm, empathetic"),
            new Voice("Vasudha", "Female", "Expressive, energetic"),
            new Voice("Riyaa", "Female", "Confident, authoritative"),
            new Voice("Tarika", "Female", "Warm, empathetic"),
            new Voice("Harshad", "Male", "Professional"));

    private VoiceCatalog() {}

    /** The catalogue's own spelling of a voice name ("shivani" gives "Shivani"), or empty when it is not a Timbre v3.0 voice. */
    public static Optional<String> find(String name) {
        if (name == null) return Optional.empty();
        return VOICES.stream().map(Voice::name).filter((v) -> v.equalsIgnoreCase(name.trim())).findFirst();
    }
}
