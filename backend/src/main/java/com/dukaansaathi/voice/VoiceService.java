package com.dukaansaathi.voice;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.auth.UserRepository;

/**
 * Which voice Saathi speaks with. The choice is saved on the person's profile and read here for every spoken reply, so it is the
 * same in every part of the app and it survives logging out and in. The browser never says which voice to use for a reply.
 */
@Service
public class VoiceService {
    /** What each "Play Sample" button says. It is fixed, so every voice can be compared on the same words. */
    static final String SAMPLE_TEXT = "Namaste! Main aapka Dukaan Saathi hoon. Boliye, aaj main aapke liye kya kar sakta hoon?";

    private final UserRepository users;
    private final GnaniClient gnani;
    private final Map<String, byte[]> samples = new ConcurrentHashMap<>(); // voice -> its sample, so a voice is paid for once

    public VoiceService(UserRepository users, GnaniClient gnani) {
        this.users = users;
        this.gnani = gnani;
    }

    /** The voice this person chose, or the default until they choose. */
    public String selected(ShopContext who) {
        return users.findVoice(who.userId()).flatMap(VoiceCatalog::find).orElse(VoiceCatalog.DEFAULT);
    }

    /** Saves the choice. Only names from Gnani's Timbre v3.0 catalogue are accepted. Returns the name as the catalogue spells it. */
    public String choose(ShopContext who, String name) {
        String voice = VoiceCatalog.find(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of Saathi's voices"));
        users.setVoice(who.userId(), voice);
        return voice;
    }

    /** A reply read aloud in the chosen voice. */
    public byte[] speak(ShopContext who, String text) {
        return gnani.speak(SpeechText.forSpeech(text), selected(who));
    }

    /** The sample sentence in any catalogue voice (it does not change the saved choice). */
    public byte[] sample(String name) {
        String voice = VoiceCatalog.find(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown voice"));
        byte[] cached = samples.get(voice);
        if (cached != null) return cached;
        byte[] audio = gnani.speak(SpeechText.forSpeech(SAMPLE_TEXT), voice);
        samples.put(voice, audio);
        return audio;
    }
}
