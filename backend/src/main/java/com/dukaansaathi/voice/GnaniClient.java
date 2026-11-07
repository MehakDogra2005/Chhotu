package com.dukaansaathi.voice;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Gnani Vachana speech: speech-to-text (Prisma) and text-to-speech (Timbre). The API key is read from the private
 * settings (gnani.api-key in backend/config/local.properties, or the GNANI_API_KEY environment variable) and never
 * leaves the server: the browser only talks to this app.
 */
@Component
public class GnaniClient {
    private static final Logger log = LoggerFactory.getLogger(GnaniClient.class);

    private final RestClient http;
    private final String apiKey;
    private final String sttLanguage;
    private final String ttsLanguage;
    // Gnani can limit an account to one text-to-speech request at a time (a second one is answered 429), so ours wait in line
    private final Semaphore speaking = new Semaphore(1, true);

    public GnaniClient(@Value("${gnani.api-key:}") String apiKey,
                       @Value("${gnani.base-url:https://api.vachana.ai}") String baseUrl,
                       @Value("${gnani.stt-language:en-IN}") String sttLanguage,
                       @Value("${gnani.tts-language:hi-en}") String ttsLanguage) {
        this.apiKey = apiKey.trim();
        this.sttLanguage = sttLanguage;
        this.ttsLanguage = ttsLanguage;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(25));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean configured() {
        return !apiKey.isEmpty();
    }

    /**
     * What the audio says, written the way Munim Ji reads it: Hinglish in English letters, numbers as digits. "" if nothing was
     * heard. boost is a list of single words (letters only, at most 100) the service should listen out for.
     */
    public String transcribe(byte[] audio, String fileName, List<String> boost) {
        return transcribe(audio, fileName, boost, null);
    }

    /** Languages Gnani can listen for. "indic-auto" lets it work the language out itself. */
    private static final java.util.Set<String> LISTEN = java.util.Set.of("as-IN", "bn-IN", "en-IN", "gu-IN", "hi-IN", "kn-IN", "ml-IN", "mr-IN", "pa-IN",
            "ta-IN", "te-IN", "indic-auto");

    /** language is what the person chose ("pa-IN"); null or a language Gnani cannot listen for (Odia) means the default. */
    public String transcribe(byte[] audio, String fileName, List<String> boost, String language) {
        requireKey();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio_file", new NamedBytes(audio, fileName));
        form.add("language_code", language != null && LISTEN.contains(language) ? language : sttLanguage);
        form.add("format", "transcribe");
        if (!boost.isEmpty()) {
            form.add("bias_list", boost.stream().map((w) -> "\"" + w + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]")));
            form.add("bias_score", "1.0");
        }
        try {
            JsonNode reply;
            try {
                reply = listen(form);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() < 500) throw e;
                log.warn("Gnani speech-to-text answered {}: trying once more", e.getStatusCode().value()); // a passing hiccup on their side
                reply = listen(form);
            }
            return reply == null ? "" : reply.path("transcript").asText("").trim();
        } catch (RestClientResponseException e) {
            throw failed("speech-to-text", e);
        } catch (ResourceAccessException e) {
            throw unreachable("speech-to-text", e);
        }
    }

    private JsonNode listen(MultiValueMap<String, Object> form) {
        return http.post().uri("/stt/v3").header("X-API-Key-ID", apiKey).contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class);
    }

    /**
     * The text spoken as a WAV file, in the given voice. voice must be one of VoiceCatalog's Timbre v3.0 names (Gnani rejects a
     * voice that belongs to another model). Pass the text through SpeechText.forSpeech first.
     */
    public byte[] speak(String text, String voice, String language) {
        requireKey();
        log.info("Gnani text-to-speech: model={} voice={}", VoiceCatalog.MODEL, voice);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("text", text);
        request.put("voice", voice);
        request.put("model", VoiceCatalog.MODEL);
        request.put("language", language == null ? ttsLanguage : language);
        request.put("speed", 1.0);
        request.put("audio_config", Map.of("sample_rate", 24000, "num_channels", 1, "sample_width", 2, "encoding", "linear_pcm", "container", "wav"));
        boolean turn = false;
        try {
            turn = speaking.tryAcquire(30, TimeUnit.SECONDS);
            if (!turn) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The voice service is busy. Try again in a moment");
            try {
                return synthesize(request);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() != 429) throw e;
                // Still answered 429 (RATE_LIMITED), for example by another app using the same account: wait a moment and ask once more
                Thread.sleep(1200);
                return synthesize(request);
            }
        } catch (RestClientResponseException e) {
            throw failed("text-to-speech", e);
        } catch (ResourceAccessException e) {
            throw unreachable("text-to-speech", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The voice service did not answer in time");
        } finally {
            if (turn) speaking.release();
        }
    }

    private byte[] synthesize(Map<String, Object> request) {
        return http.post().uri("/api/v1/tts/inference").header("X-API-Key-ID", apiKey)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.parseMediaType("audio/wav"), MediaType.APPLICATION_OCTET_STREAM)
                .body(request).retrieve().body(byte[].class);
    }

    private void requireKey() {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The voice service is not set up");
    }

    // The answer is logged with the status only: the request holds the key and the shopkeeper's audio.
    private static ResponseStatusException failed(String what, RestClientResponseException e) {
        log.warn("Gnani {} answered {}", what, e.getStatusCode().value());
        int status = e.getStatusCode().value();
        if (status == 429) return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The voice service is busy. Try again in a moment");
        if (status == 400) return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The voice service could not read that audio");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The voice service is not working right now");
    }

    private static ResponseStatusException unreachable(String what, ResourceAccessException e) {
        log.warn("Gnani {} could not be reached: {}", what, e.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "The voice service did not answer in time");
    }

    /** Bytes sent as a file part: the part needs a file name so the service knows the audio format. */
    private static final class NamedBytes extends ByteArrayResource {
        private final String fileName;

        NamedBytes(byte[] bytes, String fileName) {
            super(bytes);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
