package viettel.dac.prototype.tool.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import viettel.dac.prototype.tool.dto.ToolDTO;
import viettel.dac.prototype.tool.exception.*;
import viettel.dac.prototype.tool.model.Dependency;
import viettel.dac.prototype.tool.model.Parameter;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.DependencyRepository;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpMethod.*;

@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolRepository toolRepository;
    private final DependencyRepository dependencyRepository;

    private final RestTemplate restTemplate = new RestTemplate(); // For sending HTTP requests

    /**
     * Register a new tool.
     *
     * @param toolDTO The tool details to register.
     * @return The registered tool entity.
     */
    @Transactional
    public Tool registerTool(ToolDTO toolDTO) {
        // Check for existing tool
        if (toolRepository.existsByName(toolDTO.getName())) {
            throw new ToolAlreadyExistsException(
                    "Tool '" + toolDTO.getName() + "' already exists"
            );
        }

        // Validate tool definition
        validateToolDefinition(toolDTO);

        // Convert DTO to entity
        Tool tool = convertToEntity(toolDTO);

        // Save tool (without dependencies first)
        Tool savedTool = toolRepository.save(tool);

        // Process dependencies
        if (toolDTO.getDependencies() != null && !toolDTO.getDependencies().isEmpty()) {
            addDependencies(savedTool, toolDTO.getDependencies());
        }

        return savedTool;
    }

    /**
     * Get a tool by its name.
     *
     * @param name The name of the tool.
     * @return The tool entity.
     */
    public Tool getToolByName(String name) {
        return toolRepository.findByName(name)
                .orElseThrow(() -> new ToolNotFoundException("Tool '" + name + "' not found"));
    }

    /**
     * Get all tools with their parameters and dependencies.
     *
     * @return A list of all tools.
     */
    public List<Tool> getAllTools() {
        return toolRepository.findAllWithParameters();
    }

    /**
     * Delete a tool by its name.
     *
     * @param name The name of the tool to delete.
     */
    @Transactional
    public void deleteTool(String name) {
        Tool tool = getToolByName(name);
        toolRepository.delete(tool);
    }

    /**
     * Get dependencies for a specific tool.
     *
     * @param toolName The name of the tool.
     * @return A list of dependency names.
     */
    public List<String> getToolDependencies(String toolName) {
        Tool tool = getToolByName(toolName);
        return dependencyRepository.findByTool(tool).stream()
                .map(d -> d.getDependsOn().getName())
                .collect(Collectors.toList());
    }

    /**
     * Test a registered tool by sending a request to its endpoint.
     *
     * @param name       The name of the tool to test.
     * @param parameters The parameters to send in the request body.
     * @return The response from the tool's endpoint.
     */
    public Map<String, Object> testTool(String name, Map<String, Object> parameters) {
        Tool tool = getToolByName(name);

        // Validate HTTP method and make appropriate request
        HttpMethod httpMethod = HttpMethod.valueOf(tool.getHttpMethod().toUpperCase());
        String endpoint = tool.getEndpoint();

        if (httpMethod.equals(GET)) {
            return restTemplate.getForObject(endpoint, Map.class, parameters);
        } else if (httpMethod.equals(POST)) {
            return restTemplate.postForObject(endpoint, parameters, Map.class);
        } else if (httpMethod.equals(PUT)) {
            restTemplate.put(endpoint, parameters);
            return Map.of("status", "success");
        } else if (httpMethod.equals(DELETE)) {
            restTemplate.delete(endpoint, parameters);
            return Map.of("status", "success");
        }
        throw new UnsupportedOperationException("HTTP method not supported: " + httpMethod);
    }

    // ================= HELPER METHODS ================= //

    /**
     * Convert a ToolDTO to a Tool entity.
     *
     * @param dto The DTO containing the tool details.
     * @return A Tool entity.
     */
    private Tool convertToEntity(ToolDTO dto) {
        Tool tool = new Tool();
        tool.setName(dto.getName());
        tool.setDescription(dto.getDescription());
        tool.setEndpoint(dto.getEndpoint());
        tool.setAuthenticationType(dto.getAuthenticationType());
        tool.setHttpMethod(dto.getHttpMethod());
        tool.setTimeoutMs(dto.getTimeoutMs());

        // Add parameters
        dto.getParameters().forEach(paramDTO -> {
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

    /**
     * Add dependencies to a registered tool.
     *
     * @param tool           The registered Tool entity.
     * @param dependencyNames A list of dependency names.
     */
    private void addDependencies(Tool tool, List<String> dependencyNames) {
        dependencyNames.forEach(depName -> {
            Tool dependentTool = toolRepository.findByName(depName)
                    .orElseThrow(() -> new DependencyNotFoundException(
                            "Dependency '" + depName + "' not found"
                    ));

            // Check for circular dependency
            if (hasCircularDependency(tool.getName(), dependentTool.getName())) {
                throw new CircularDependencyException(
                        "Circular dependency detected between '" +
                                tool.getName() + "' and '" + depName + "'"
                );
            }

            Dependency dependency = new Dependency();
            dependency.setTool(tool);
            dependency.setDependsOn(dependentTool);
            dependencyRepository.save(dependency);
        });
    }

    /**
     * Check if there is a circular dependency chain between tools.
     *
     * @param sourceTool  The source (parent) tool name.
     * @param currentTool The current (child) tool being checked.
     * @return True if there is a circular dependency; otherwise false.
     */
    private boolean hasCircularDependency(String sourceTool, String currentTool) {
        Set<String> visited = new HashSet<>();
        return checkDependencyChain(sourceTool, currentTool, visited);
    }

    /**
     * Recursively check for circular dependencies in the chain.
     *
     * @param original   The original (parent) tool name.
     * @param current    The current (child) tool being checked.
     * @param visitedSet A set of visited tools during recursion.
     * @return True if there is a circular dependency; otherwise false.
     */
    private boolean checkDependencyChain(String original, String current, Set<String> visitedSet) {
        if (original.equals(current)) return true; // Circular dependency detected
        if (visitedSet.contains(current)) return false; // Already visited

        visitedSet.add(current);

        List<Dependency> dependencies = dependencyRepository.findDependenciesByToolName(current);

        for (Dependency dep : dependencies) {
            String nextTool = dep.getDependsOn().getName();
            if (checkDependencyChain(original, nextTool, visitedSet)) {
                return true;
            }
        }

        visitedSet.remove(current); // Backtrack
        return false;
    }

    /**
     * Validate the fields in a ToolDTO object before registration.
     *
     * @param dto The DTO containing the details of the new Tool to register.
     */
    private void validateToolDefinition(ToolDTO dto) {
        // Validate required fields
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new InvalidToolDefinitionException("Tool name is required");
        }

        // Validate parameters
        dto.getParameters().forEach(param -> {
            if (param.getName() == null || param.getName().isBlank()) {
                throw new InvalidToolDefinitionException(
                        "Parameter name cannot be empty for tool '" + dto.getName() + "'"
                );
            }
        });

        // Validate HTTP method
        try {
            HttpMethod.valueOf(dto.getHttpMethod().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidToolDefinitionException(
                    "Invalid HTTP method: " + dto.getHttpMethod()
            );
        }
    }
}


