package viettel.dac.prototype.execution.model;

import lombok.Data;

import java.util.List;

@Data
public class ExecutionFeedback {
    private String analysisId;
    private List<ExecutedIntent> executedIntents;
    private boolean isComplete;
    private String errorSummary;
}
