package burmalda.controller;

import burmalda.dto.SendMessageRequest;
import burmalda.model.Session;
import burmalda.model.Message;
import burmalda.service.SessionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/sessions")
@CrossOrigin
public class SessionController {
    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping
    public List<Session> getSessions() {
        return service.getSessions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Session createSession() {
        return service.createSession();
    }

    @GetMapping("/{id}/messages")
    public List<Message> getMessages(@PathVariable String id) {
        return service.getMessages(id);
    }

    @PostMapping("/{id}/messages")
    public Message sendMessage(@PathVariable String id, @Valid @RequestBody SendMessageRequest request) {
        return service.sendMessage(id, request.content());
    }
}
