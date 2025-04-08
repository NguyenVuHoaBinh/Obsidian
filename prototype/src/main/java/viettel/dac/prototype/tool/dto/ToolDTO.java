package viettel.dac.prototype.tool.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import viettel.dac.prototype.tool.enums.HttpMethodType;
import viettel.dac.prototype.tool.model.Parameter;
import viettel.dac.prototype.tool.model.Tool;

import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolDTO {
    @NotBlank(message = "Tool name is required")
    @Size(min = 3, max = 100, message = "Tool name must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Tool name can only contain letters, numbers, hyphens, and underscores")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    private String description;

    @NotNull(message = "Parameters list is required")
    private List<ParameterDTO> parameters;

    @NotBlank(message = "Endpoint is required")
    @URL(message = "Endpoint must be a valid URL")
    private String endpoint;

    private String authenticationType;

    @NotNull(message = "HTTP method is required")
    private HttpMethodType httpMethod;

    @Positive(message = "Timeout must be a positive value")
    @Max(value = 120000, message = "Timeout cannot exceed 2 minutes (120000 ms)")
    private Integer timeoutMs = 5000;

    private List<String> dependencies;

    // Convert Entity to DTO
    public static ToolDTO fromEntity(Tool tool) {
        return new ToolDTO(
                tool.getName(),
                tool.getDescription(),
                tool.getParameters().stream()
                        .map(ParameterDTO::fromEntity)
                        .collect(Collectors.toList()),
                tool.getEndpoint(),
                tool.getAuthenticationType(),
                tool.getHttpMethod(),
                tool.getTimeoutMs(),
                tool.getDependencies().stream()
                        .map(dep -> dep.getDependsOn().getName())
                        .collect(Collectors.toList())
        );
    }

    // Convert DTO to Entity
    public Tool toEntity() {
        Tool tool = new Tool();
        tool.setName(name);
        tool.setDescription(description);
        tool.setEndpoint(endpoint);
        tool.setAuthenticationType(authenticationType);
        tool.setHttpMethod(httpMethod);
        tool.setTimeoutMs(timeoutMs);

        // Add parameters
        if (parameters != null) {
            parameters.forEach(paramDTO -> {
                Parameter param = paramDTO.toEntity();
                tool.addParameter(param);
            });
        }

        return tool;
    }
}