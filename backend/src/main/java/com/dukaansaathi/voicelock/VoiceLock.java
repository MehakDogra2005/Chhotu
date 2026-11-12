package com.dukaansaathi.voicelock;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Voice lock: once a person has recorded their voice, only that voice can speak to Munim Ji. Their voiceprint (256 numbers, see SpeakerModel)
 * is saved; each sentence heard later is turned into a voiceprint too and must be alike enough. Nobody can be sure from a few seconds of
 * sound, so this keeps other people out of the way but it is not a safe lock.
 */
@Service
public class VoiceLock {
    private static final Logger log = LoggerFactory.getLogger(VoiceLock.class);
    public static final int SENTENCES = 5;           // how many sentences the person records
    private static final int AT_LEAST = 3;           // sentences that must be usable
    private static final double SENTENCE_SECONDS = 0.8; // least speech in a recorded sentence
    private static final double WORD_SECONDS = 0.25;    // least speech worth checking later (one short word like "haan")
    private static final double SHORT_SPEECH_SECONDS = 1.5;
    private static final double SHORT_SPEECH_ALLOWANCE = 0.05; // one word like "haan" says little about the voice, so it may be a little less alike

    private final JdbcTemplate jdbc;
    private final SpeakerModel model;
    private final double alikeEnough;

    public VoiceLock(JdbcTemplate jdbc, SpeakerModel model, @Value("${voicelock.alike-enough:0.5}") double alikeEnough) {
        this.jdbc = jdbc;
        this.model = model;
        this.alikeEnough = alikeEnough;
    }

    public boolean isOn(String userId) {
        return savedVoiceprint(userId) != null;
    }

    /** Saves the voice from the recorded sentences (replacing an earlier one). */
    @Transactional
    public void enroll(String userId, List<byte[]> recordings) {
        List<float[]> prints = new ArrayList<>();
        try {
            for (byte[] recording : recordings) {
                try {
                    prints.add(model.voiceprint(recording, SENTENCE_SECONDS));
                } catch (IllegalArgumentException tooShortOrBroken) {
                    log.info("A voice lock recording could not be used: {}", tooShortOrBroken.getMessage());
                }
            }
        } catch (Exception | LinkageError e) {
            log.warn("Voice lock could not run: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Voice lock is not available on this server");
        }
        if (prints.size() < AT_LEAST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "We could not hear enough. Please record the sentences again, a little louder and closer");
        }
        float[] voiceprint = SpeakerModel.average(prints);
        jdbc.update("DELETE FROM voiceprints WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO voiceprints (user_id, voiceprint) VALUES (?, ?)", userId, join(voiceprint));
    }

    public void remove(String userId) {
        jdbc.update("DELETE FROM voiceprints WHERE user_id = ?", userId);
    }

    /**
     * Is this the voice of the person who owns the login? Always true when they have not recorded their voice. If the model cannot run
     * on this server the answer is also true: a broken lock must not shut the shopkeeper out of their own app.
     */
    public boolean isOwner(String userId, byte[] wav) {
        float[] saved = savedVoiceprint(userId);
        if (saved == null) return true;
        try {
            double seconds = model.secondsOfSpeech(wav);
            double needed = seconds < SHORT_SPEECH_SECONDS ? alikeEnough - SHORT_SPEECH_ALLOWANCE : alikeEnough;
            double alike = SpeakerModel.similarity(saved, model.voiceprint(wav, WORD_SECONDS));
            log.info("Voice lock: {} s of speech, alike {} (needs {})", String.format("%.1f", seconds), String.format("%.2f", alike), String.format("%.2f", needed));
            return alike >= needed;
        } catch (IllegalArgumentException tooShortOrBroken) {
            return false; // a click or a breath: nothing to act on
        } catch (Exception | LinkageError e) {
            log.warn("Voice lock could not run, so the sentence is allowed: {}", e.toString());
            return true;
        }
    }

    private float[] savedVoiceprint(String userId) {
        List<String> rows = jdbc.queryForList("SELECT voiceprint FROM voiceprints WHERE user_id = ?", String.class, userId);
        if (rows.isEmpty()) return null;
        String[] numbers = rows.get(0).split(",");
        float[] voiceprint = new float[numbers.length];
        for (int i = 0; i < numbers.length; i++) voiceprint[i] = Float.parseFloat(numbers[i]);
        return voiceprint;
    }

    private static String join(float[] numbers) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < numbers.length; i++) text.append(i == 0 ? "" : ",").append(numbers[i]);
        return text.toString();
    }
}
