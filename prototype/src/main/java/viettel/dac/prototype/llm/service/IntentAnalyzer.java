package viettel.dac.prototype.llm.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.execution.model.AnalysisResult;
import viettel.dac.prototype.llm.client.LLMClient;
import viettel.dac.prototype.llm.model.ConversationContext;

@Service
public class IntentAnalyzer {

    @Autowired private LLMClient llmClient;

    private static final String INTENT_PROMPT = """
        Analyze the user message and previous context:
        Current Message: %s
        Conversation History: %s
        Previous Execution Results: %s
        
        Extract intents and parameters in JSON format.
        """;

    public AnalysisResult analyze(String message, ConversationContext context) {
        String prompt = String.format(INTENT_PROMPT,
                message,
                context.getMessageHistory(),
                context.getExecutionHistory()
        );

        String llmResponse = llmClient.generate(prompt);
        return parseLlmResponse(llmResponse);
    }

    private AnalysisResult parseLlmResponse(String response) {
        // Implementation-specific parsing logic
        return new AnalysisResult();
    }
}

