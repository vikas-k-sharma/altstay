package com.altstay.api.chat;

import com.altstay.api.config.ConciergeProperties;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ConciergePromptFactory {

    public static final String ESCALATION_TOKEN = "[ESCALATE_TO_MANAGER]";

    private final String templateString;
    private final ConciergeProperties properties;

    public ConciergePromptFactory(
            @Value("classpath:prompts/concierge-system.st") Resource systemPromptResource,
            ConciergeProperties properties) {
        this.properties = properties;
        try {
            this.templateString = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load concierge system prompt template", e);
        }
    }

    public SystemMessage createSystemMessage(String propertyName, String knowledgeBase) {
        String effectivePropertyName = (propertyName != null && !propertyName.isBlank())
                ? propertyName.trim()
                : properties.propertyName();

        PromptTemplate template = new PromptTemplate(templateString);
        String rendered = template.render(Map.of(
                "propertyName", effectivePropertyName,
                "escalationContact", properties.escalationContact(),
                "escalationToken", ESCALATION_TOKEN,
                "knowledgeBase", knowledgeBase
        ));
        return new SystemMessage(rendered);
    }

    public String getEscalationToken() {
        return ESCALATION_TOKEN;
    }
}
