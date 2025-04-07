package viettel.dac.prototype.llm.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.llm.client.LLMClient;
import viettel.dac.prototype.llm.model.ConversationContext;

@Service
public class ResponseGenerator {

    @Autowired private LLMClient llmClient;

    private static final String RESPONSE_PROMPT = """
        Generate a helpful response considering:
        - User Message: %s
        - Execution Results: %s
        - Conversation History: %s
        - Errors: %s
        
        Respond in natural, friendly language.
        """;

    public String generateResponse(ConversationContext context, ExecutionResult result) {
        String prompt = String.format(RESPONSE_PROMPT,
                context.getLatestMessage(),
                result.getSummary(),
                context.getMessageHistory(),
                result.getErrorSummary()
        );

        return llmClient.generate(prompt);
    }
}

