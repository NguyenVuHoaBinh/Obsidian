package viettel.dac.prototype.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.tool.enums.ParameterType;
import viettel.dac.prototype.tool.model.Parameter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterDTO {
    @NotBlank(message = "Parameter name is required")
    @Size(min = 1, max = 100, message = "Parameter name must be between 1 and 100 characters")
    private String name;

    @NotNull(message = "Parameter type is required")
    private ParameterType type;

    private boolean required = false;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private String defaultValue;

    // Convert Entity to DTO
    public static ParameterDTO fromEntity(Parameter parameter) {
        return new ParameterDTO(
                parameter.getName(),
                parameter.getType(),
                parameter.isRequired(),
                parameter.getDescription(),
                parameter.getDefaultValue()
        );
    }

    // Convert DTO to Entity
    public Parameter toEntity() {
        Parameter parameter = new Parameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setRequired(required);
        parameter.setDescription(description);
        parameter.setDefaultValue(defaultValue);
        return parameter;
    }
}