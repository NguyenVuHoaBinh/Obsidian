package viettel.dac.prototype.execution.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents the result of analyzing user input by the LLM component.
 * Contains identified intents and parameters to be executed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of the LLM analysis containing detected intents")
public class AnalysisResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Unique identifier for the analysis", example = "a123-b456-c789")
    @NotBlank(message = "Analysis ID is required")
    @Size(max = 50, message = "Analysis ID must not exceed 50 characters")
    private String analysisId;

    @Schema(description = "List of detected intents with their parameters")
    @NotEmpty(message = "At least one intent must be provided")
    @Valid
    private List<Intent> intents;

    @Schema(description = "Flag indicating if multiple intents were detected")
    private boolean multiIntent;

    @Schema(description = "Confidence score of the analysis", example = "0.95")
    @Range(min = 0, max = 1, message = "Confidence must be between 0 and 1")
    private double confidence;

    @Schema(description = "Timestamp when the analysis was performed")
    private LocalDateTime analysisTimestamp;

    /**
     * Checks if the analysis result contains a specific intent.
     *
     * @param intentName The name of the intent to check for
     * @return true if the intent is present, false otherwise
     */
    @JsonIgnore
    public boolean hasIntent(String intentName) {
        return intents.stream()
                .anyMatch(intent -> intent.getIntent().equals(intentName));
    }

    /**
     * Gets the highest confidence intent in the analysis result.
     *
     * @return The intent with the highest confidence or null if no intents are present
     */
    @JsonIgnore
    public Intent getHighestConfidenceIntent() {
        return intents.stream()
                .max(java.util.Comparator.comparingDouble(Intent::getConfidence))
                .orElse(null);
    }

    /**
     * Calculates the average confidence across all intents.
     *
     * @return The average confidence value
     */
    @JsonIgnore
    public double getAverageConfidence() {
        if (intents.isEmpty()) {
            return 0.0;
        }

        return intents.stream()
                .mapToDouble(Intent::getConfidence)
                .average()
                .orElse(0.0);
    }

    /**
     * Default constructor with timestamp initialization.
     *
     * @param analysisId The ID of the analysis
     */
    public AnalysisResult(String analysisId) {
        this.analysisId = analysisId;
        this.analysisTimestamp = LocalDateTime.now();
    }
}