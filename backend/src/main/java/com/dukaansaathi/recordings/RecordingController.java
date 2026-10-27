package com.dukaansaathi.recordings;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.recordings.RecordingService.Audio;
import com.dukaansaathi.recordings.RecordingService.Item;

@RestController
@RequestMapping("/api/recordings")
public class RecordingController {
    record TranscriptRequest(String text) {}

    private final AuthService auth;
    private final RecordingService recordings;

    public RecordingController(AuthService auth, RecordingService recordings) {
        this.auth = auth;
        this.recordings = recordings;
    }

    /** type is VOICE_NOTE or CALL_RECORDING; durationSeconds is measured by the browser. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Item upload(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                       @RequestParam("file") MultipartFile file, @RequestParam("type") String type,
                       @RequestParam(value = "consent", defaultValue = "false") boolean consent,
                       @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {
        return recordings.upload(auth.requireShop(authHeader), file, type, consent, durationSeconds);
    }

    @PutMapping("/{recordingId}/transcript")
    public Item transcript(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                           @PathVariable String recordingId, @RequestBody TranscriptRequest body) {
        return recordings.saveTranscript(auth.requireShop(authHeader), recordingId, body.text());
    }

    @GetMapping("/{recordingId}/audio")
    public ResponseEntity<byte[]> audio(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                        @PathVariable String recordingId) {
        Audio audio = recordings.audio(auth.requireShop(authHeader), recordingId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(audio.contentType())).body(audio.bytes());
    }
}
