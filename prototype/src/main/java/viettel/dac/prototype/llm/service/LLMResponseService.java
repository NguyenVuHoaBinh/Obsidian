package viettel.dac.prototype.llm.service;

import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.execution.enums.ExecutionStatus;
import viettel.dac.prototype.execution.model.ExecutionFeedback;

@Service
public class LLMResponseService {

    private final OpenAIClient openAIClient; // Hypothetical OpenAI client

    public LLMResponseService(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    public String generateUserResponse(ExecutionFeedback feedback) {
        String prompt = buildPrompt(feedback);
        return openAIClient.generate(prompt);
    }

    private String buildPrompt(ExecutionFeedback feedback) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a user-friendly response based on these execution results:\n");

        if (feedback.isComplete()) {
            prompt.append("All tasks completed successfully.\n");
        } else {
            prompt.append("Some tasks failed:\n");
            prompt.append(feedback.getErrorSummary()).append("\n");
        }

        prompt.append("Details:\n");
        feedback.getExecutedIntents().forEach(intent -> {
            prompt.append("- ").append(intent.getIntent()).append(": ");
            if (intent.getStatus() == ExecutionStatus.COMPLETED) {
                prompt.append("Success\n");
                prompt.append("  Result: ").append(intent.getResult()).append("\n");
            } else {
                prompt.append("Failed\n");
                prompt.append("  Error: ").append(intent.getError()).append("\n");
            }
        });

        return prompt.toString();
    }
}
