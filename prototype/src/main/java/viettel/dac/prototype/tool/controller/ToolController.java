package viettel.dac.prototype.tool.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import viettel.dac.prototype.exception.UnifiedGlobalExceptionHandler;
import viettel.dac.prototype.tool.dto.ToolDTO;
import viettel.dac.prototype.tool.service.ToolService;


import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
@Tag(name = "Tool Management", description = "APIs for tool registration and management")
public class ToolController {
    private static final Logger logger = LoggerFactory.getLogger(ToolController.class);

    private final ToolService toolService;

    /**
     * Register a new tool.
     *
     * @param toolDTO The tool details to register.
     * @return The registered tool.
     */
    @Operation(
            summary = "Register a new tool",
            description = "Creates a new tool with the provided configuration and parameters",
            tags = {"Tool Management"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tool successfully registered"),
            @ApiResponse(responseCode = "400", description = "Invalid tool configuration",
                    content = @Content(schema = @Schema(implementation = UnifiedGlobalExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tool with the same name already exists",
                    content = @Content(schema = @Schema(implementation = UnifiedGlobalExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ToolDTO> registerTool(
            @Valid @RequestBody ToolDTO toolDTO) {
        logger.info("Registering new tool: {}", toolDTO.getName());
        ToolDTO registeredTool = toolService.registerTool(toolDTO);
        logger.info("Tool registered successfully: {}", registeredTool.getName());

        return ResponseEntity.created(URI.create("/api/tools/" + registeredTool.getName()))
                .body(registeredTool);
    }

    /**
     * Get all tools with pagination.
     *
     * @param page The page number (zero-based).
     * @param size The page size.
     * @param sortBy The field to sort by.
     * @return A page of tools.
     */
    @Operation(
            summary = "List all tools",
            description = "Retrieves all registered tools with pagination support",
            tags = {"Tool Management"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of tools")
    })
    @GetMapping
    public ResponseEntity<Page<ToolDTO>> getAllTools(
            @Parameter(description = "Page number (zero-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Number of items per page", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Field to sort by", example = "name")
            @RequestParam(defaultValue = "name") String sortBy
    ) {
        logger.info("Getting all tools with pagination: page={}, size={}, sortBy={}", page, size, sortBy);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        Page<ToolDTO> tools = toolService.getAllTools(pageable);
        logger.debug("Retrieved {} tools", tools.getTotalElements());
        return ResponseEntity.ok(tools);
    }

    /**
     * Get a tool by its name.
     *
     * @param name The name of the tool.
     * @return The tool details.
     */
    @Operation(
            summary = "Get tool by name",
            description = "Retrieves the details of a specific tool by its name",
            tags = {"Tool Management"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved tool"),
            @ApiResponse(responseCode = "404", description = "Tool not found",
                    content = @Content(schema = @Schema(implementation = UnifiedGlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping("/{name}")
    public ResponseEntity<ToolDTO> getToolByName(
            @Parameter(description = "Tool name", example = "weather-service", required = true)
            @PathVariable String name) {
        logger.info("Getting tool by name: {}", name);
        ToolDTO tool = toolService.getToolByName(name);
        return ResponseEntity.ok(tool);
    }

    /**
     * Get dependencies for a specific tool.
     *
     * @param name The name of the tool.
     * @return A list of dependency names.
     */
    @Operation(
            summary = "Get tool dependencies",
            description = "Retrieves the dependencies for a specific tool",
            tags = {"Tool Management"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved dependencies"),
            @ApiResponse(responseCode = "404", description = "Tool not found",
                    content = @Content(schema = @Schema(implementation = UnifiedGlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping("/{name}/dependencies")
    public ResponseEntity<List<String>> getToolDependencies(
            @Parameter(description = "Tool name", example = "weather-service", required = true)
            @PathVariable String name) {
        logger.info("Getting dependencies for tool: {}", name);
        List<String> dependencies = toolService.getToolDependencies(name);
        logger.debug("Found {} dependencies for tool: {}", dependencies.size(), name);
        return ResponseEntity.ok(dependencies);
    }

    /**
     * Delete a tool by its name.
     *
     * @param name The name of the tool to delete.
     */
    @Operation(
            summary = "Delete a tool",
            description = "Deletes a tool by its name",
            tags = {"Tool Management"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tool successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Tool not found",
                    content = @Content(schema = @Schema(implementation = UnifiedGlobalExceptionHandler.ErrorResponse.class)))
    })
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteTool(
            @Parameter(description = "Tool name", example = "weather-service", required = true)
            @PathVariable String name) {
        logger.info("Deleting tool: {}", name);
        toolService.deleteTool(name);
        logger.info("Tool deleted successfully: {}", name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test a registered tool by sending a request to its endpoint.
     *
     * @param name       The name of the tool to test.
     * @param parameters The parameters to send in the request body.
     * @return The response from the tool's endpoint.
     */
    @Operation(
            summary = "Test a tool",
            description = "Tests a tool by sending a request to its endpoint with the provided parameters",
            tags = {"Tool Management"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool test successful"),
            @ApiResponse(responseCode = "404", description = "Tool not found",
                    content = @Content(schema = @Schema(implementation = UnifiedGlobalExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Tool execution failed",
                    content = @Content(schema = @Schema(implementation = UnifiedGlobalExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping("/{name}/test")
    public ResponseEntity<Map<String, Object>> testTool(
            @Parameter(description = "Tool name", example = "weather-service", required = true)
            @PathVariable String name,

            @Parameter(description = "Parameters for tool execution")
            @RequestBody Map<String, Object> parameters
    ) {
        logger.info("Testing tool: {} with parameters: {}", name, parameters);
        Map<String, Object> response = toolService.testTool(name, parameters);
        logger.info("Tool test completed successfully: {}", name);
        return ResponseEntity.ok(response);
    }
}