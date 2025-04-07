package viettel.dac.prototype.tool.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import viettel.dac.prototype.tool.dto.ToolDTO;
import viettel.dac.prototype.tool.service.ToolService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    /**
     * Register a new tool.
     *
     * @param toolDTO The tool details to register.
     * @return The registered tool.
     */
    @PostMapping
    public ResponseEntity<ToolDTO> registerTool(@Valid @RequestBody ToolDTO toolDTO) {
        ToolDTO registeredTool = ToolDTO.fromEntity(toolService.registerTool(toolDTO));
        return ResponseEntity.created(URI.create("/api/tools/" + registeredTool.getName()))
                .body(registeredTool);
    }

    /**
     * Get all tools.
     *
     * @return A list of all tools.
     */
    @GetMapping
    public ResponseEntity<List<ToolDTO>> getAllTools() {
        List<ToolDTO> tools = toolService.getAllTools().stream()
                .map(ToolDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tools);
    }

    /**
     * Get a tool by its name.
     *
     * @param name The name of the tool.
     * @return The tool details.
     */
    @GetMapping("/{name}")
    public ResponseEntity<ToolDTO> getToolByName(@PathVariable String name) {
        ToolDTO tool = ToolDTO.fromEntity(toolService.getToolByName(name));
        return ResponseEntity.ok(tool);
    }

    /**
     * Get dependencies for a specific tool.
     *
     * @param name The name of the tool.
     * @return A list of dependency names.
     */
    @GetMapping("/{name}/dependencies")
    public ResponseEntity<List<String>> getToolDependencies(@PathVariable String name) {
        List<String> dependencies = toolService.getToolDependencies(name);
        return ResponseEntity.ok(dependencies);
    }

    /**
     * Delete a tool by its name.
     *
     * @param name The name of the tool to delete.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteTool(@PathVariable String name) {
        toolService.deleteTool(name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test a registered tool by sending a request to its endpoint.
     *
     * @param name       The name of the tool to test.
     * @param parameters The parameters to send in the request body.
     * @return The response from the tool's endpoint.
     */
    @PostMapping("/{name}/test")
    public ResponseEntity<Map<String, Object>> testTool(
            @PathVariable String name,
            @RequestBody Map<String, Object> parameters
    ) {
        Map<String, Object> response = toolService.testTool(name, parameters);
        return ResponseEntity.ok(response);
    }
}

