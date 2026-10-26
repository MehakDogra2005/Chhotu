package com.dukaansaathi.assistant;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.assistant.AssistantRepository.HistoryItem;
import com.dukaansaathi.auth.AuthService;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    record Say(String text, String via) {}
    record Confirm(String utteranceId, int choice) {}
    record Cancel(String utteranceId) {}

    private final AuthService auth;
    private final AssistantService assistant;

    public AssistantController(AuthService auth, AssistantService assistant) {
        this.auth = auth;
        this.assistant = assistant;
    }

    /** The shopkeeper said or typed something. The reply is an answer, or a change waiting to be confirmed. */
    @PostMapping("/interpret")
    public AssistantService.Reply interpret(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @RequestBody Say body) {
        return assistant.interpret(auth.requireShop(authHeader), body.text(), body.via());
    }

    /** The shopkeeper confirmed one option of an earlier reply. */
    @PostMapping("/execute")
    public AssistantService.Done execute(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @RequestBody Confirm body) {
        return assistant.execute(auth.requireShop(authHeader), body.utteranceId(), body.choice());
    }

    @PostMapping("/cancel")
    public Map<String, String> cancel(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @RequestBody Cancel body) {
        assistant.cancel(auth.requireShop(authHeader), body.utteranceId());
        return Map.of("message", "Cancelled");
    }

    @GetMapping("/history")
    public List<HistoryItem> history(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                     @RequestParam(defaultValue = "50") int limit) {
        return assistant.history(auth.requireShop(authHeader), limit);
    }
}
