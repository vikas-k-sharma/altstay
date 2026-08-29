package com.altstay.api.chat;

import com.altstay.api.chat.dto.ChatRequest;
import com.altstay.api.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.answer(request);
    }
}
