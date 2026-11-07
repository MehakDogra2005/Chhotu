package com.dukaansaathi.assistant;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Google Gemini, used for two small jobs so Munim Ji works in any language:
 * 1. understand(): finds the language of a sentence and rewrites it as a short Hinglish/English command the keyword parser knows.
 * 2. translate(): turns Munim Ji's answer into the language the person spoke.
 * The key is gemini.api-key in backend/config/local.properties (or the GEMINI_API_KEY environment variable).
 * Without a key, or if Gemini fails, both methods return null and Munim Ji simply works as before.
 */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    /**
     * language is its English name, like "Tamil" ("Hinglish" for Hindi in English letters).
     * fits is true when the sentence is one of the requests Munim Ji can do, and then command is it rewritten the way the keyword parser
     * knows; when fits is false, command is just the sentence in simple English.
     */
    public record Understood(String language, String command, boolean fits, String answer) {}

    private static final String REQUESTS = """
            - stock of a product: "Chocolate ka stock kitna hai?"
            - sales, profit or expenses of a day or period: "Aaj ki sale batao", "is mahine profit batao", "kal ka kharcha batao", "is hafte ki kamai"
            - what a customer owes: "Rahul ka udhaar kitna hai?"
            - add stock, or a new item has arrived: "10 Chocolate add karo", "Naya item aaya hai, 20 Parle G"
            - record a payment received, also when the sentence says the customer already paid part of what they owed, or asks to reduce / minus / remove that amount from their udhaar: "Ramesh ne 500 de diye", "Rahul ne 719 mein se 500 de diye, 500 hata do", "Rahul ne 500 ka udhaar chuka diya"
            - give credit (udhaar): "Ramesh ko 300 ka udhaar diya"
            - a customer took goods and did not pay, items listed: "Ramesh ne 2 doodh, 1 pencil aur 5 notebook udhaar mein liye", "Suresh ke naam 3 chocolate udhaar likh do"
            - what goods a customer took on udhaar (the items behind their balance): "Rahul 719 ne kya kya saman udhaar liya hai?", "Rahul ka kya saman baaki hai?"
            - products running low or to reorder: "kaun sa samaan khatam hone wala hai?", "Restocking list bana do"
            - business summary: "business insights"
            - best selling product or best margin: "Kaun sa product sabse zyada bika?", "Kis product pe sabse zyada margin hai?"
            - payment reminders: "Kis-kis ko payment reminder bhejna hai?"
            - old unpaid udhaar: "Kaunse customers ka udhaar 1 mahine se zyada purana hai?"
            - payment register: "Aaj ka payment register dikhao"
            - take a customer order: "Humein order lene hain"
            - check inventory: "Mujhe aaj ki inventory check karni hai"
            - open a page (inventory, customers, advisor, settings, help, conversations, home): "Inventory kholo", "Go to settings"
            """;

    private final RestClient http;
    private final RestClient slowHttp;
    private final ObjectMapper json;
    private final String apiKey;
    private final List<String> models;
    private volatile int preferred = 0; // the model that worked last time goes first, so a busy one is not asked (and waited for) every time

    public GeminiClient(ObjectMapper json, @Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.models:gemini-3.1-flash-lite,gemini-flash-lite-latest,gemini-3.5-flash}") List<String> models) {
        this.json = json;
        // A slow Gemini must not keep the shopkeeper waiting: after a few seconds we give up and answer without it
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").requestFactory(factory).build();
        // Writing names in nine languages takes longer, and nobody waits for it (it runs in the background)
        JdkClientHttpRequestFactory patient = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        patient.setReadTimeout(Duration.ofSeconds(45));
        this.slowHttp = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").requestFactory(patient).build();
        this.apiKey = apiKey.trim();
        this.models = models;
    }

    private static final Map<String, String> CODES = Map.ofEntries(Map.entry("hindi", "hi-IN"), Map.entry("tamil", "ta-IN"), Map.entry("telugu", "te-IN"),
            Map.entry("kannada", "kn-IN"), Map.entry("malayalam", "ml-IN"), Map.entry("marathi", "mr-IN"), Map.entry("bengali", "bn-IN"),
            Map.entry("gujarati", "gu-IN"), Map.entry("punjabi", "pa-IN"), Map.entry("odia", "or-IN"), Map.entry("assamese", "as-IN"));

    /** The Gnani language code for a language name like "Tamil" ("ta-IN"), or null if it is not one we speak. */
    public static String code(String language) {
        return language == null ? null : CODES.get(language.trim().toLowerCase());
    }

    public boolean configured() {
        return !apiKey.isEmpty();
    }

    public Understood understand(String text, String data) {
        String reply = ask("A shopkeeper is talking to a shop assistant app. The app can do only these kinds of requests (examples in Hinglish):\n" + REQUESTS
                + "\nDetect the language of the sentence below and name it in English, like Tamil or Bengali (say Hinglish for Hindi written in English "
                + "letters, and English for English). Set fits to true ONLY if the sentence asks for exactly one of those kinds of requests (any wording or language is fine). "
                + "A sentence that tells the app to record or change something (a payment received, udhaar given, stock added) always has fits true, even if it is "
                + "phrased as a question like \"can you remove 500?\"; never answer it as a question. "
                + "Questions that need counting, comparing, listing or anything else (for example how many products, which supplier, who owes the most) "
                + "are NOT those requests. If fits is true, write command as the sentence rewritten in the same style as the matching example, keeping "
                + "numbers and the names of products and people (written in English letters). Otherwise set fits to false and write command as the "
                + "sentence translated into simple English. When fits is false ALSO write answer: a reply to the sentence using ONLY the SHOP DATA below, written in the "
                + "same language and script as the sentence (English stays English, Hinglish stays Hinglish). Keep it short, about three sentences, plain words, at most "
                + "three names, rupee sign for money, do the arithmetic carefully. If the data does not have the answer, say so politely and do not guess. You cannot change "
                + "anything in the shop. When fits is true leave answer empty. "
                + "Answer as JSON: {\"language\":\"...\",\"fits\":true,\"command\":\"...\",\"answer\":\"...\"}.\n\nSHOP DATA:\n" + data + "\nSentence: " + text, true);
        if (reply == null) return null;
        log.debug("Gemini understood: {}", reply);
        try {
            JsonNode node = json.readTree(reply);
            return new Understood(node.path("language").asText("").trim(), node.path("command").asText("").trim(), node.path("fits").asBoolean(true),
                    directOk(node.path("answer").asText(""), node.path("language").asText("English")) ? node.path("answer").asText("").trim() : "");
        } catch (Exception e) {
            log.warn("Gemini gave an answer that is not JSON");
            return null;
        }
    }

    /**
     * Answers a question from the shop's own numbers (data is ShopSummary text). The answer is in English, or in Hinglish if hinglish is true,
     * so the existing translation step can put it into the person's language. Null if Gemini could not answer.
     */
    public String answer(String question, String data, boolean hinglish) {
        String reply = ask("You are Munim Ji, a friendly assistant for a small shop owner in India. Answer the question using ONLY the shop data below. "
                + "The question may be in any language, but always write your answer in " + (hinglish ? "Hinglish (Hindi in English letters)" : "simple English")
                + ". Keep it short: about three sentences (up to five if they ask for a summary, insights or advice), plain words, and name at most three people or products. Use the rupee sign for money. Do the arithmetic carefully. If the data does not contain the answer, say so politely and do not guess. "
                + "You cannot change anything in the shop from here: if the question asks to add, change or delete something, say that you can only "
                + "answer questions for that wording and suggest a short command like \"10 Chocolate add karo\" or \"Ramesh ne 500 de diye\".\n\n"
                + "SHOP DATA:\n" + data + "\nQUESTION: " + question, false);
        return reply == null || reply.isBlank() ? null : reply.trim();
    }

    private static final Map<String, String> PAGE_LANGUAGES = new java.util.LinkedHashMap<>(Map.of("hi", "Hindi", "pa", "Punjabi", "ta", "Tamil", "te", "Telugu",
            "kn", "Kannada", "ml", "Malayalam", "mr", "Marathi", "bn", "Bengali", "gu", "Gujarati"));

    /**
     * Product and customer names written in the other page languages: {"hi": [...], "ta": [...]}, each list in the order of names.
     * Everyday words are translated ("Milk" becomes the local word), brand and people's names are written in the language's script.
     * A language that did not come back right is left out. Null when Gemini could not answer.
     */
    public Map<String, List<String>> names(List<String> names) {
        String languages = String.join(", ", PAGE_LANGUAGES.entrySet().stream().map((e) -> e.getKey() + " = " + e.getValue()).toList());
        String reply = ask("These are names of products and customers of a small shop in India. Write each name in each of these languages (key = language): " + languages
                + ". Use the normal script of the language. Translate everyday product words into the word shopkeepers use (like Milk, Notebook); write brand names "
                + "and people's names by sound in the language's script. Answer as JSON: {\"hi\": [...], \"pa\": [...], ...} with exactly " + names.size()
                + " strings in every list, in the same order as the names.\nNames: " + toJson(names), true, (t) -> true, slowHttp);
        if (reply == null) return null;
        try {
            JsonNode node = json.readTree(reply);
            Map<String, List<String>> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> language : PAGE_LANGUAGES.entrySet()) {
                JsonNode list = node.path(language.getKey());
                if (!list.isArray() || list.size() != names.size()) continue;
                List<String> items = new java.util.ArrayList<>();
                for (JsonNode item : list) items.add(item.asText("").trim());
                if (items.stream().anyMatch((s) -> s.isEmpty() || s.length() > 300 || !inScript(s, language.getValue()))) continue;
                out.put(language.getKey(), items);
            }
            return out;
        } catch (Exception e) {
            log.warn("Gemini gave names that are not JSON");
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** The language name for a Gnani code ("ta-IN" -> "Tamil"), or null. */
    public static String name(String code) {
        return CODES.entrySet().stream().filter((e) -> e.getValue().equals(code)).map((e) -> Character.toUpperCase(e.getKey().charAt(0)) + e.getKey().substring(1))
                .findFirst().orElse(null);
    }

    private final Map<String, String> translated = new java.util.concurrent.ConcurrentHashMap<>(); // fixed phrases are translated once

    public String translate(String message, String language) {
        String key = language + "|" + message;
        String known = translated.get(key);
        if (known != null) return known;
        String result = translateNow(message, language);
        if (result != null && translated.size() < 2000) translated.put(key, result);
        return result;
    }

    private static final Map<String, Character.UnicodeScript> SCRIPTS = Map.ofEntries(Map.entry("hindi", Character.UnicodeScript.DEVANAGARI),
            Map.entry("marathi", Character.UnicodeScript.DEVANAGARI), Map.entry("tamil", Character.UnicodeScript.TAMIL),
            Map.entry("telugu", Character.UnicodeScript.TELUGU), Map.entry("kannada", Character.UnicodeScript.KANNADA),
            Map.entry("malayalam", Character.UnicodeScript.MALAYALAM), Map.entry("bengali", Character.UnicodeScript.BENGALI),
            Map.entry("assamese", Character.UnicodeScript.BENGALI), Map.entry("gujarati", Character.UnicodeScript.GUJARATI),
            Map.entry("punjabi", Character.UnicodeScript.GURMUKHI), Map.entry("odia", Character.UnicodeScript.ORIYA));

    /** Languages the small model writes badly when it answers straight away: those are answered in English and translated instead. */
    private static final java.util.Set<String> TRANSLATE_INSTEAD = java.util.Set.of("malayalam", "bengali", "marathi");

    /**
     * Can Gemini's direct answer be used as it is? Not for the weak languages, not when it is in the wrong script, and not when it holds
     * letters or digits of another Indian script (a sign of garbled text).
     */
    private static boolean directOk(String answer, String language) {
        if (answer == null || answer.isBlank() || TRANSLATE_INSTEAD.contains(language.toLowerCase()) || !inScript(answer, language)) return false;
        Character.UnicodeScript script = SCRIPTS.get(language.toLowerCase());
        if (script == null) return true;
        return answer.codePoints().filter(Character::isLetterOrDigit).mapToObj(Character.UnicodeScript::of)
                .allMatch((s) -> s == script || s == Character.UnicodeScript.LATIN || s == Character.UnicodeScript.COMMON || s == Character.UnicodeScript.INHERITED);
    }

    /** True when most of the letters are in the language's own script (a small model sometimes answers in the wrong language). */
    private static boolean inScript(String text, String language) {
        Character.UnicodeScript script = SCRIPTS.get(language.toLowerCase());
        if (script == null) return true;
        long letters = text.codePoints().filter(Character::isLetter).count();
        long right = text.codePoints().filter((c) -> Character.isLetter(c) && Character.UnicodeScript.of(c) == script).count();
        return letters > 0 && right * 10 >= letters; // a wrong-language answer has almost none; real ones may hold many English names
    }

    private String translateNow(String message, String language) {
        String reply = ask("Translate this message from a shop assistant into the " + language + " language, written in the normal " + language
                + " script. The message may be in Hinglish (Hindi in English letters): translate its meaning into natural " + language
                + ", do not just write the Hindi words in " + language + " letters. The answer must be in " + language
                + ", not in Hindi or English (unless " + language + " is Hindi). Keep numbers, "
                + "product names, people's names and the rupee sign as they are. Answer with only the translation.\n" + message, false,
                (text) -> inScript(text, language));
        return reply == null || reply.isBlank() ? null : reply.trim();
    }

    private String ask(String prompt, boolean asJson) {
        return ask(prompt, asJson, (text) -> true);
    }

    /** Tries the models one after another (the last good one first) and returns the first answer that passes ok; null if none does. */
    private String ask(String prompt, boolean asJson, java.util.function.Predicate<String> ok) {
        return ask(prompt, asJson, ok, http);
    }

    private String ask(String prompt, boolean asJson, java.util.function.Predicate<String> ok, RestClient http) {
        if (!configured()) return null;
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", asJson ? Map.of("responseMimeType", "application/json", "temperature", 0) : Map.of("temperature", 0));
        for (int n = 0; n < models.size(); n++) {
            int at = (preferred + n) % models.size();
            try {
                JsonNode answer = http.post().uri("/v1beta/models/" + models.get(at) + ":generateContent").header("x-goog-api-key", apiKey)
                        .body(body).retrieve().body(JsonNode.class);
                String text = answer == null ? null : answer.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
                if (text != null && !text.isBlank() && ok.test(text)) {
                    preferred = at;
                    return text;
                }
                log.warn("Gemini model {} gave an unusable answer", models.get(at));
            } catch (Exception e) {
                log.warn("Gemini model {} failed: {}", models.get(at), e.getMessage()); // the message never holds the key (it travels in a header)
            }
        }
        return null;
    }
}
