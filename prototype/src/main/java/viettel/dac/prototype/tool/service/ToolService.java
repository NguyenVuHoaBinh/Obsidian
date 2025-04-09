package viettel.dac.prototype.tool.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import viettel.dac.prototype.tool.dto.ToolDTO;
import viettel.dac.prototype.tool.enums.HttpMethodType;
import viettel.dac.prototype.tool.exception.*;
import viettel.dac.prototype.tool.model.Dependency;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.DependencyRepository;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolService {
    private static final Logger logger = LoggerFactory.getLogger(ToolService.class);

    private final ToolRepository toolRepository;
    private final DependencyRepository dependencyRepository;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Register a new tool.
     *
     * @param toolDTO The tool details to register.
     * @return The registered tool DTO.
     */
    @Transactional
    public ToolDTO registerTool(ToolDTO toolDTO) {
        logger.info("Registering new tool: {}", toolDTO.getName());

        // Check for existing tool
        if (toolRepository.existsByName(toolDTO.getName())) {
            logger.warn("Tool '{}' already exists", toolDTO.getName());
            throw new ToolAlreadyExistsException(
                    "Tool '" + toolDTO.getName() + "' already exists"
            );
        }

        // Validate tool definition
        validateToolDefinition(toolDTO);

        // Convert DTO to entity
        Tool tool = toolDTO.toEntity();

        // Save tool (without dependencies first)
        Tool savedTool = toolRepository.save(tool);
        logger.debug("Tool saved with ID: {}", savedTool.getId());

        // Process dependencies
        if (toolDTO.getDependencies() != null && !toolDTO.getDependencies().isEmpty()) {
            try {
                addDependencies(savedTool, toolDTO.getDependencies());
                logger.debug("Added {} dependencies to tool '{}'", toolDTO.getDependencies().size(), savedTool.getName());
            } catch (Exception e) {
                logger.error("Error adding dependencies to tool '{}': {}", savedTool.getName(), e.getMessage(), e);
                throw e;
            }
        }

        logger.info("Tool registered successfully: {}", savedTool.getName());
        return ToolDTO.fromEntity(toolRepository.findById(savedTool.getId()).orElse(savedTool));
    }

    /**
     * Get a tool by its name.
     *
     * @param name The name of the tool.
     * @return The tool DTO.
     */
    public ToolDTO getToolByName(String name) {
        logger.info("Getting tool by name: {}", name);
        Tool tool = toolRepository.findByName(name)
                .orElseThrow(() -> {
                    logger.warn("Tool '{}' not found", name);
                    return new ToolNotFoundException("Tool '" + name + "' not found");
                });

        logger.debug("Tool '{}' found", name);
        return ToolDTO.fromEntity(tool);
    }

    /**
     * Get all tools with their parameters and dependencies.
     *
     * @return A list of all tool DTOs.
     */
    public List<ToolDTO> getAllTools() {
        logger.info("Getting all tools");
        List<Tool> tools = toolRepository.findAllWithParameters();

        logger.debug("Found {} tools", tools.size());
        return tools.stream()
                .map(ToolDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Get all tools with pagination.
     *
     * @param pageable The pagination information.
     * @return A page of tool DTOs.
     */
    public Page<ToolDTO> getAllTools(Pageable pageable) {
        logger.info("Getting all tools with pagination: {}", pageable);
        Page<Tool> toolsPage = toolRepository.findAll(pageable);

        logger.debug("Found {} tools (page {} of {})",
                toolsPage.getNumberOfElements(),
                toolsPage.getNumber() + 1,
                toolsPage.getTotalPages());

        return toolsPage.map(ToolDTO::fromEntity);
    }

    /**
     * Delete a tool by its name.
     *
     * @param name The name of the tool to delete.
     */
    @Transactional
    public void deleteTool(String name) {
        logger.info("Deleting tool: {}", name);
        Tool tool = toolRepository.findByName(name)
                .orElseThrow(() -> {
                    logger.warn("Tool '{}' not found for deletion", name);
                    return new ToolNotFoundException("Tool '" + name + "' not found");
                });

        toolRepository.delete(tool);
        logger.info("Tool '{}' deleted successfully", name);
    }

    /**
     * Get dependencies for a specific tool.
     *
     * @param toolName The name of the tool.
     * @return A list of dependency names.
     */
    public List<String> getToolDependencies(String toolName) {
        logger.info("Getting dependencies for tool: {}", toolName);
        Tool tool = toolRepository.findByName(toolName)
                .orElseThrow(() -> {
                    logger.warn("Tool '{}' not found when getting dependencies", toolName);
                    return new ToolNotFoundException("Tool '" + toolName + "' not found");
                });

        List<String> dependencies = dependencyRepository.findByTool(tool).stream()
                .map(d -> d.getDependsOn().getName())
                .collect(Collectors.toList());

        logger.debug("Found {} dependencies for tool '{}'", dependencies.size(), toolName);
        return dependencies;
    }

    /**
     * Test a registered tool by sending a request to its endpoint.
     *
     * @param name       The name of the tool to test.
     * @param parameters The parameters to send in the request body.
     * @return The response from the tool's endpoint.
     * @throws ToolExecutionException if the request fails
     */
    public Map<String, Object> testTool(String name, Map<String, Object> parameters) {
        logger.info("Testing tool: {} with parameters: {}", name, parameters);
        Tool tool = toolRepository.findByName(name)
                .orElseThrow(() -> {
                    logger.warn("Tool '{}' not found for testing", name);
                    return new ToolNotFoundException("Tool '" + name + "' not found");
                });

        try {
            // Validate HTTP method and make appropriate request
            HttpMethodType httpMethod = tool.getHttpMethod();
            String endpoint = tool.getEndpoint();
            Map<String, Object> result;

            logger.debug("Executing tool '{}' with HTTP method: {} to endpoint: {}",
                    name, httpMethod, endpoint);

            switch (httpMethod) {
                case GET:
                    result = restTemplate.getForObject(endpoint, Map.class, parameters);
                    break;
                case POST:
                    result = restTemplate.postForObject(endpoint, parameters, Map.class);
                    break;
                case PUT:
                    restTemplate.put(endpoint, parameters);
                    result = Map.of("status", "success");
                    break;
                case DELETE:
                    restTemplate.delete(endpoint, parameters);
                    result = Map.of("status", "success");
                    break;
                default:
                    logger.error("Unsupported HTTP method: {}", httpMethod);
                    throw new UnsupportedOperationException("HTTP method not supported: " + httpMethod);
            }

            logger.info("Tool execution successful: {}", name);
            return result != null ? result : Map.of("status", "success");

        } catch (RestClientException e) {
            logger.error("Tool execution failed: {}", e.getMessage(), e);
            throw new ToolExecutionException("Failed to execute tool: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during tool execution: {}", e.getMessage(), e);
            throw new ToolExecutionException("Unexpected error during tool execution: " + e.getMessage(), e);
        }
    }

    // ================= HELPER METHODS ================= //

    /**
     * Add dependencies to a registered tool.
     *
     * @param tool           The registered Tool entity.
     * @param dependencyNames A list of dependency names.
     */
    private void addDependencies(Tool tool, List<String> dependencyNames) {
        logger.debug("Adding dependencies to tool '{}': {}", tool.getName(), dependencyNames);

        for (String depName : dependencyNames) {
            Tool dependentTool = toolRepository.findByName(depName)
                    .orElseThrow(() -> {
                        logger.warn("Dependency '{}' not found for tool '{}'", depName, tool.getName());
                        return new DependencyNotFoundException(
                                "Dependency '" + depName + "' not found"
                        );
                    });

            // Check for circular dependency
            if (hasCircularDependency(tool.getName(), dependentTool.getName())) {
                logger.warn("Circular dependency detected between '{}' and '{}'", tool.getName(), depName);
                throw new CircularDependencyException(
                        "Circular dependency detected between '" +
                                tool.getName() + "' and '" + depName + "'"
                );
            }

            Dependency dependency = new Dependency();
            dependency.setTool(tool);
            dependency.setDependsOn(dependentTool);
            dependencyRepository.save(dependency);
            logger.debug("Added dependency '{}' to tool '{}'", depName, tool.getName());
        }
    }

    /**
     * Check if there is a circular dependency chain between tools.
     *
     * @param sourceTool  The source (parent) tool name.
     * @param currentTool The current (child) tool being checked.
     * @return True if there is a circular dependency; otherwise false.
     */
    private boolean hasCircularDependency(String sourceTool, String currentTool) {
        logger.debug("Checking for circular dependency between '{}' and '{}'", sourceTool, currentTool);
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
        if (original.equals(current)) {
            logger.debug("Circular dependency detected: '{}' -> '{}'", current, original);
            return true; // Circular dependency detected
        }

        if (visitedSet.contains(current)) {
            return false; // Already visited
        }

        visitedSet.add(current);
        logger.trace("Visiting tool: '{}'", current);

        List<Dependency> dependencies = dependencyRepository.findDependenciesByToolName(current);

        for (Dependency dep : dependencies) {
            String nextTool = dep.getDependsOn().getName();
            logger.trace("Checking dependency: '{}' -> '{}'", current, nextTool);

            if (checkDependencyChain(original, nextTool, visitedSet)) {
                return true;
            }
        }

        visitedSet.remove(current); // Backtrack
        logger.trace("Backtracking from tool: '{}'", current);
        return false;
    }

    /**
     * Validate the fields in a ToolDTO object before registration.
     *
     * @param dto The DTO containing the details of the new Tool to register.
     */
    private void validateToolDefinition(ToolDTO dto) {
        logger.debug("Validating tool definition for: {}", dto.getName());

        // Validate required fields
        if (dto.getName() == null || dto.getName().isBlank()) {
            logger.warn("Tool name is required");
            throw new InvalidToolDefinitionException("Tool name is required");
        }

        // Validate parameters
        if (dto.getParameters() != null) {
            for (int i = 0; i < dto.getParameters().size(); i++) {
                var param = dto.getParameters().get(i);
                if (param.getName() == null || param.getName().isBlank()) {
                    logger.warn("Parameter name cannot be empty for tool '{}' at index {}", dto.getName(), i);
                    throw new InvalidToolDefinitionException(
                            "Parameter name cannot be empty for tool '" + dto.getName() + "'"
                    );
                }
            }
        }

        // Validate HTTP method
        try {
            if (dto.getHttpMethod() == null) {
                logger.warn("HTTP method is required for tool '{}'", dto.getName());
                throw new InvalidToolDefinitionException(
                        "HTTP method is required for tool '" + dto.getName() + "'"
                );
            }
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid HTTP method for tool '{}': {}", dto.getName(), dto.getHttpMethod());
            throw new InvalidToolDefinitionException(
                    "Invalid HTTP method: " + dto.getHttpMethod()
            );
        }

        logger.debug("Tool definition validation passed for: {}", dto.getName());
    }

    /**
     * Gets a simplified representation of all tools with their parameters and dependencies.
     * Optimized for inclusion in LLM prompts.
     *
     * @return A simplified list of tools with essential information
     */
    public List<Map<String, Object>> getToolsForPrompt() {
        logger.info("Getting simplified tool information for prompt generation");
        List<Tool> tools = toolRepository.findAllWithParameters();

        return tools.stream()
                .map(tool -> {
                    Map<String, Object> toolInfo = new HashMap<>();

                    // Basic tool information
                    toolInfo.put("name", tool.getName());
                    toolInfo.put("description", tool.getDescription());

                    // Format parameters
                    List<Map<String, Object>> parameters = tool.getParameters().stream()
                            .map(param -> {
                                Map<String, Object> paramInfo = new HashMap<>();
                                paramInfo.put("name", param.getName());
                                paramInfo.put("type", param.getType().toString());
                                paramInfo.put("required", param.isRequired());
                                paramInfo.put("description", param.getDescription());

                                if (param.getDefaultValue() != null && !param.getDefaultValue().isEmpty()) {
                                    paramInfo.put("defaultValue", param.getDefaultValue());
                                }

                                return paramInfo;
                            })
                            .collect(Collectors.toList());

                    toolInfo.put("parameters", parameters);

                    // Format dependencies
                    List<String> dependencies = tool.getDependencies().stream()
                            .map(dep -> dep.getDependsOn().getName())
                            .collect(Collectors.toList());

                    if (!dependencies.isEmpty()) {
                        toolInfo.put("dependencies", dependencies);
                    }

                    return toolInfo;
                })
                .collect(Collectors.toList());
    }
}