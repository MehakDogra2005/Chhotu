package com.dukaansaathi.recordings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.audit.AuditRepository;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.recordings.RecordingRepository.Recording;
import com.dukaansaathi.voice.GnaniClient;
import com.dukaansaathi.voice.UsageLimit;
import com.dukaansaathi.voice.VoiceVocabulary;

/**
 * Uploading voice notes and call recordings. The audio is kept on this server for KEEP_DAYS. When the Gnani speech
 * service is set up, a short recording is turned into text on upload; otherwise (or when that fails) the shopkeeper types
 * what it says. Nothing in a recording changes the udhaar until it goes through Saathi and is confirmed.
 */
@Service
public class RecordingService {
    /** hasAudio is false for sample recordings, whose audio files are not on this server. */
    public record Item(String recordingId, String type, String fileName, Integer durationSeconds, String transcript,
                       String status, java.time.LocalDateTime uploadedAt, boolean hasAudio) {}

    public record Audio(byte[] bytes, String contentType) {}

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
    private static final int KEEP_DAYS = 90;
    private static final int MAX_AUTO_BYTES = 3 * 1024 * 1024; // about a minute of audio; the length the browser reports is not trusted
    private static final int AUTO_PER_MINUTE = 10;
    private static final int MAX_TRANSCRIPT = 1000;
    private static final Set<String> TYPES = Set.of("VOICE_NOTE", "CALL_RECORDING");
    private static final Map<String, String> AUDIO_TYPES = Map.of("m4a", "audio/mp4", "mp3", "audio/mpeg", "wav", "audio/wav",
            "ogg", "audio/ogg", "opus", "audio/ogg", "webm", "audio/webm", "aac", "audio/aac", "amr", "audio/amr", "3gp", "audio/3gpp");

    private final RecordingRepository repo;
    private final AuditRepository audit;
    private final AppClock clock;
    private final GnaniClient gnani;
    private final VoiceVocabulary vocabulary;
    private final UsageLimit limit;
    private final Path folder;

    public RecordingService(RecordingRepository repo, AuditRepository audit, AppClock clock, GnaniClient gnani,
                            VoiceVocabulary vocabulary, UsageLimit limit, @Value("${dukaan.recordings-dir:./data/recordings}") String folder) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
        this.gnani = gnani;
        this.vocabulary = vocabulary;
        this.limit = limit;
        this.folder = Path.of(folder).toAbsolutePath();
    }

    public List<Item> latest(String shopId, int limit) {
        return repo.latest(shopId, limit).stream().map(this::toItem).toList();
    }

    /** A call recording needs the shopkeeper to confirm the customer knew the call was recorded. */
    public Item upload(ShopContext who, MultipartFile file, String type, boolean consent, Integer durationSeconds) {
        if (!TYPES.contains(type)) throw bad("Unknown recording type");
        if (file == null || file.isEmpty()) throw bad("Choose an audio file");
        String ext = extension(file.getOriginalFilename());
        if (!AUDIO_TYPES.containsKey(ext)) throw bad("This is not an audio file we can keep. Use m4a, mp3, wav, ogg, opus, webm, aac, amr or 3gp");
        if (type.equals("CALL_RECORDING") && !consent) throw bad("Confirm that the customer knew the call was recorded");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String hash = sha256(bytes);
        repo.uploadedBefore(who.shopId(), hash).ifPresent((day) -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This recording was already uploaded on " + day);
        });

        String id = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        try {
            Files.createDirectories(folder);
            Files.write(folder.resolve(id + "." + ext), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String name = cleanName(file.getOriginalFilename());
        Integer seconds = durationSeconds == null || durationSeconds < 0 ? null : durationSeconds;
        repo.add(id, who.shopId(), type, name, hash, seconds, consent, who.userId(), clock.today().atTime(LocalTime.now()), clock.today().plusDays(KEEP_DAYS));
        audit.record(who.shopId(), who.userId(), "audio_recording", id, "CREATE", null, Map.of("type", type, "file", name), "UI");
        transcribeNow(who, id, bytes, name);
        return toItem(get(who, id));
    }

    /** What was said in the recording, typed by the shopkeeper (until a speech-to-text service is added). */
    public Item saveTranscript(ShopContext who, String recordingId, String text) {
        get(who, recordingId);
        if (text == null || text.isBlank()) throw bad("Type what was said in the recording");
        if (text.length() > MAX_TRANSCRIPT) throw bad("Keep it under " + MAX_TRANSCRIPT + " letters");
        repo.saveTranscript(who.shopId(), recordingId, text.trim(), "Typed by shopkeeper");
        return toItem(get(who, recordingId));
    }

    /** Reads a short recording with the speech service. If that fails the upload still stands and the text can be typed. */
    private void transcribeNow(ShopContext who, String id, byte[] audio, String fileName) {
        if (!gnani.configured() || audio.length > MAX_AUTO_BYTES) return;
        try {
            limit.check(who.shopId(), "auto", AUTO_PER_MINUTE); // uploads must not be a way round the limits on the paid service
            String text = gnani.transcribe(audio, fileName, vocabulary.forShop(who.shopId()));
            if (!text.isBlank()) repo.saveTranscript(who.shopId(), id, text.length() > MAX_TRANSCRIPT ? text.substring(0, MAX_TRANSCRIPT) : text, "Gnani Prisma");
        } catch (RuntimeException e) {
            log.warn("Could not read recording {} automatically: {}", id, e.getMessage());
        }
    }

    public Audio audio(ShopContext who, String recordingId) {
        get(who, recordingId);
        Path file = audioFile(recordingId);
        if (file == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The audio of this recording is not stored on this server");
        try {
            return new Audio(Files.readAllBytes(file), AUDIO_TYPES.get(extension(file.getFileName().toString())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Recording get(ShopContext who, String recordingId) {
        return repo.find(who.shopId(), recordingId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recording not found"));
    }

    private Item toItem(Recording r) {
        return new Item(r.recordingId(), r.type(), r.fileName(), r.durationSeconds(), r.transcript(), r.status(), r.uploadedAt(), audioFile(r.recordingId()) != null);
    }

    private Path audioFile(String recordingId) {
        for (String ext : AUDIO_TYPES.keySet()) {
            Path file = folder.resolve(recordingId + "." + ext);
            if (Files.isRegularFile(file)) return file;
        }
        return null;
    }

    private static String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /** Only the file's own name, without folders, and not too long. */
    private static String cleanName(String fileName) {
        String name = fileName == null ? "recording" : fileName.replaceAll(".*[\\\\/]", "").replaceAll("[^\\p{L}\\p{N} ._()-]", "_");
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
