package viettel.dac.prototype.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.tool.model.Parameter;
import viettel.dac.prototype.tool.model.Tool;

import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolDTO {
    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotNull
    private List<ParameterDTO> parameters;

    @NotBlank
    private String endpoint;

    private String authenticationType;

    @NotBlank
    private String httpMethod;

    @Positive
    private Integer timeoutMs = 5000;

    private List<String> dependencies;

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
        parameters.forEach(paramDTO -> {
            Parameter param = new Parameter();
            param.setName(paramDTO.getName());
            param.setType(paramDTO.getType());
            param.setRequired(paramDTO.isRequired());
            param.setDescription(paramDTO.getDescription());
            param.setDefaultValue(paramDTO.getDefaultValue());
            tool.addParameter(param);
        });

        return tool;
    }

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
}

