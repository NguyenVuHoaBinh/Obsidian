package viettel.dac.prototype.execution.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class MissingDependencyException extends RuntimeException {
    private final Map<String, List<String>> missingDependencies;
    private final Map<String, ToolMetadata> missingToolsMetadata;

    public MissingDependencyException(Map<String, List<String>> missingDependencies,
                                      Map<String, ToolMetadata> missingToolsMetadata) {
        super(buildErrorMessage(missingDependencies));
        this.missingDependencies = missingDependencies;
        this.missingToolsMetadata = missingToolsMetadata;
    }

    private static String buildErrorMessage(Map<String, List<String>> missingDependencies) {
        StringBuilder message = new StringBuilder("Missing dependencies detected: ");
        boolean first = true;

        for (Map.Entry<String, List<String>> entry : missingDependencies.entrySet()) {
            if (!first) {
                message.append("; ");
            }
            first = false;

            message.append("Tool '").append(entry.getKey()).append("' requires tools: ");
            message.append(String.join(", ", entry.getValue()));
        }

        return message.toString();
    }

    public record ToolMetadata(String name, String description, List<ParameterInfo> parameters,
                                   List<String> dependencies) {

    }
        public record ParameterInfo(String name, String type, boolean required, String description, String defaultValue) {

    }
}