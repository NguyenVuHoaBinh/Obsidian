package viettel.dac.prototype.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.execution.model.Intent;

import java.util.List;

/**
 * Domain model for the result of analyzing user intent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentAnalysisResult {
    private String analysisId;
    private List<Intent> intents;
    private double confidence;
    private boolean multiIntent;
    private String rawResponse;
}
