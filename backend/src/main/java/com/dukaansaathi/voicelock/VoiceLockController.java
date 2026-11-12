package com.dukaansaathi.voicelock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.voice.UsageLimit;

/** Settings > Voice lock: record your voice, see whether it is on, remove it. */
@RestController
@RequestMapping("/api/voicelock")
public class VoiceLockController {
    private static final int MAX_RECORDING_BYTES = 2 * 1024 * 1024; // about a minute of 16 kHz sound; a sentence is a few seconds
    private static final int SAVES_PER_MINUTE = 6;

    private final AuthService auth;
    private final VoiceLock voiceLock;
    private final UsageLimit limit;

    public VoiceLockController(AuthService auth, VoiceLock voiceLock, UsageLimit limit) {
        this.auth = auth;
        this.voiceLock = voiceLock;
        this.limit = limit;
    }

    @GetMapping
    public Map<String, Object> status(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        return state(auth.requireShop(authHeader));
    }

    /** The recorded sentences (a WAV file each). Replaces any earlier voice. */
    @PostMapping(value = "/enroll", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> enroll(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                      @RequestParam("audio") List<MultipartFile> audio) {
        ShopContext who = auth.requireShop(authHeader);
        limit.check(who.shopId(), "voicelock", SAVES_PER_MINUTE);
        if (audio.size() > VoiceLock.SENTENCES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send " + VoiceLock.SENTENCES + " sentences");
        List<byte[]> recordings = new ArrayList<>();
        try {
            for (MultipartFile file : audio) {
                if (file.getSize() > MAX_RECORDING_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A recording is too long");
                recordings.add(file.getBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        voiceLock.enroll(who.userId(), recordings);
        return state(who);
    }

    @DeleteMapping
    public Map<String, Object> remove(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        ShopContext who = auth.requireShop(authHeader);
        voiceLock.remove(who.userId());
        return state(who);
    }

    private Map<String, Object> state(ShopContext who) {
        return Map.of("on", voiceLock.isOn(who.userId()), "sentences", VoiceLock.SENTENCES);
    }
}
