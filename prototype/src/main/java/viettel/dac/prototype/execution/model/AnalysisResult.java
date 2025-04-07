package viettel.dac.prototype.execution.model;

import lombok.Data;
import java.util.List;

@Data
public class AnalysisResult {
    private String analysisId;
    private List<Intent> intents;
    private boolean multiIntent;
    private double confidence;
}

