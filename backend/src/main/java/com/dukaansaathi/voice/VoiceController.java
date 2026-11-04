package com.dukaansaathi.voice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.auth.ShopContext;

/** Speech for the browser: turns a recorded sentence into text, and a reply into audio. Both need a login. */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    record SpeakRequest(String text) {}

    private static final int MAX_AUDIO_BYTES = 4 * 1024 * 1024;  // 60 seconds of 16 kHz speech is about 2 MB
    private static final int MAX_TEXT = 1500;
    private static final int LISTENS_PER_MINUTE = 40;
    private static final int SPEAKS_PER_MINUTE = 60;

    private final AuthService auth;
    private final GnaniClient gnani;
    private final UsageLimit limit;
    private final VoiceVocabulary vocabulary;

    public VoiceController(AuthService auth, GnaniClient gnani, UsageLimit limit, VoiceVocabulary vocabulary) {
        this.auth = auth;
        this.gnani = gnani;
        this.limit = limit;
        this.vocabulary = vocabulary;
    }

    /** gnani is false when no key is set; the browser then uses its own speech instead. */
    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        auth.requireShop(authHeader);
        return Map.of("gnani", gnani.configured());
    }

    /** The audio (a WAV file of one sentence) as text. An empty text means nothing was heard. */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> transcribe(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                          @RequestParam("audio") MultipartFile audio) {
        ShopContext who = auth.requireShop(authHeader);
        if (audio.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No audio was received");
        if (audio.getSize() > MAX_AUDIO_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "That recording is too long. Keep it under a minute");
        limit.check(who.shopId(), "listen", LISTENS_PER_MINUTE);
        try {
            return Map.of("text", gnani.transcribe(audio.getBytes(), "speech.wav", vocabulary.forShop(who.shopId())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The reply read aloud, as a WAV file. */
    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                        @RequestBody SpeakRequest body) {
        ShopContext who = auth.requireShop(authHeader);
        if (body.text() == null || body.text().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to say");
        if (body.text().length() > MAX_TEXT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is too long to read out");
        limit.check(who.shopId(), "speak", SPEAKS_PER_MINUTE);
        byte[] audio = gnani.speak(SpeechText.forSpeech(body.text()));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(audio);
    }
}
