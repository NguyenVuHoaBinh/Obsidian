package viettel.dac.prototype.llm.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.execution.model.AnalysisResult;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.llm.model.ConversationContext;

@Service
public class ChatService {

    @Autowired private IntentAnalyzer intentAnalyzer;
    @Autowired private ToolPlanner toolPlanner;
    @Autowired private ResponseGenerator responseGenerator;
    @Autowired private ConversationContextService contextService;

    public ChatResponse handleMessage(String conversationId, String userMessage) {
        // Get or create conversation context
        ConversationContext context = conversationId != null ?
                contextService.getContext(conversationId) :
                contextService.createNewContext();

        // Analyze user message
        AnalysisResult analysis = intentAnalyzer.analyze(userMessage, context);

        // Create and execute plan
        ExecutionPlan plan = toolPlanner.createPlan(analysis);
        ExecutionResult result = toolPlanner.executePlan(plan);

        // Update context
        context.addMessage(userMessage);
        context.addExecutionResult(result);
        contextService.saveContext(context);

        // Generate response
        String llmResponse = responseGenerator.generateResponse(context, result);

        return new ChatResponse(
                llmResponse,
                context.getConversationId(),
                result.getSummary().getFailed() > 0
        );
    }
}

