package viettel.dac.prototype.tool.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import viettel.dac.prototype.tool.enums.ParameterType;

@Entity
@Table(name = "parameter", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tool_id", "name"}, name = "uk_parameter_tool_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Parameter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_id", nullable = false)
    @ToString.Exclude
    private Tool tool;

    @NotBlank(message = "Parameter name is required")
    @Size(min = 1, max = 100, message = "Parameter name must be between 1 and 100 characters")
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull(message = "Parameter type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ParameterType type;

    @Column(name = "required", nullable = false)
    private boolean required = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_value")
    private String defaultValue;
}