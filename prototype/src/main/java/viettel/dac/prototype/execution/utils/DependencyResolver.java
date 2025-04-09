package viettel.dac.prototype.execution.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import viettel.dac.prototype.execution.exception.MissingDependencyException;
import viettel.dac.prototype.execution.exception.ToolNotFoundException;
import viettel.dac.prototype.execution.model.Intent;
import viettel.dac.prototype.tool.model.Dependency;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.DependencyRepository;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class DependencyResolver {

    private final ToolRepository toolRepository;
    private final DependencyRepository dependencyRepository;

    @Cacheable(value = "dependencyGraphs", keyGenerator = "intentListKeyGenerator")
    public List<Intent> resolveExecutionOrder(List<Intent> intents) {
        if (intents == null || intents.isEmpty()) {
            log.warn("No intents provided for dependency resolution");
            return Collections.emptyList();
        }

        log.debug("Resolving execution order for {} intents", intents.size());
        log.trace("Intent list: {}", intents.stream().map(Intent::getIntent).collect(Collectors.joining(", ")));

        // Check for missing dependencies before building the graph
        checkMissingDependencies(intents);

        // Build a dependency graph
        Map<String, Set<String>> dependencyGraph = buildDependencyGraph(intents);
        log.trace("Dependency graph built: {}", dependencyGraph);

        // Perform topological sorting
        List<Intent> result = topologicalSort(intents, dependencyGraph);
        log.debug("Resolved execution order: {}",
                result.stream().map(Intent::getIntent).collect(Collectors.joining(", ")));

        return result;
    }

    /**
     * Important method that checks for missing dependencies with special handling for OR relationships
     */
    private void checkMissingDependencies(List<Intent> intents) {
        // Collect all tool names from intents
        Set<String> availableToolNames = intents.stream()
                .map(Intent::getIntent)
                .collect(Collectors.toSet());

        log.debug("Available tools in execution: {}", availableToolNames);

        // Map to collect missing dependencies for each tool
        Map<String, List<String>> missingDependencies = new HashMap<>();
        Map<String, MissingDependencyException.ToolMetadata> missingToolsMetadata = new HashMap<>();

        // Define special OR dependency relationships
        Map<String, List<List<String>>> orDependencies = new HashMap<>();

        // Add select_payment_method's OR relationship specifically
        List<List<String>> paymentMethodOrGroups = new ArrayList<>();
        paymentMethodOrGroups.add(Arrays.asList("add_product_to_order", "create_quick_order"));
        orDependencies.put("select_payment_method", paymentMethodOrGroups);

        // Process each intent
        for (Intent intent : intents) {
            String toolName = intent.getIntent();

            // Skip tools with no dependencies
            Tool tool = toolRepository.findByName(toolName)
                    .orElseThrow(() -> new ToolNotFoundException(toolName));

            List<Dependency> dependencies = dependencyRepository.findByTool(tool);
            if (dependencies.isEmpty()) {
                log.debug("Tool '{}' has no dependencies, skipping", toolName);
                continue;
            }

            // Get all dependency names for this tool
            List<String> dependencyNames = dependencies.stream()
                    .map(dep -> dep.getDependsOn().getName())
                    .collect(Collectors.toList());

            log.debug("Tool '{}' has dependencies: {}", toolName, dependencyNames);

            // Check if this tool has OR dependency relationships
            if (orDependencies.containsKey(toolName)) {
                // Handle OR dependency groups
                for (List<String> orGroup : orDependencies.get(toolName)) {
                    boolean hasAnyDependency = false;
                    List<String> missingFromGroup = new ArrayList<>();

                    // Check if any of the OR dependencies are available
                    for (String depName : orGroup) {
                        if (availableToolNames.contains(depName)) {
                            hasAnyDependency = true;
                            log.debug("Tool '{}' has OR dependency '{}' satisfied", toolName, depName);
                            break;
                        } else {
                            missingFromGroup.add(depName);
                        }
                    }

                    // If none of the OR dependencies are available, report them as missing
                    if (!hasAnyDependency && !missingFromGroup.isEmpty()) {
                        missingDependencies.put(toolName, missingFromGroup);
                        collectMissingToolMetadata(missingFromGroup, missingToolsMetadata);
                        log.warn("Tool '{}' is missing all OR dependencies: {}",
                                toolName, missingFromGroup);
                    }
                }
            } else {
                // Handle standard AND dependencies
                List<String> missingFromTool = dependencyNames.stream()
                        .filter(depName -> !availableToolNames.contains(depName))
                        .collect(Collectors.toList());

                if (!missingFromTool.isEmpty()) {
                    missingDependencies.put(toolName, missingFromTool);
                    collectMissingToolMetadata(missingFromTool, missingToolsMetadata);
                    log.warn("Tool '{}' is missing dependencies: {}",
                            toolName, missingFromTool);
                }
            }
        }

        // If any dependencies are missing, throw an exception
        if (!missingDependencies.isEmpty()) {
            log.error("Missing dependencies detected: {}", missingDependencies);
            throw new MissingDependencyException(missingDependencies, missingToolsMetadata);
        }
    }

    private void collectMissingToolMetadata(List<String> missingTools,
                                            Map<String, MissingDependencyException.ToolMetadata> missingToolsMetadata) {
        for (String toolName : missingTools) {
            if (!missingToolsMetadata.containsKey(toolName)) {
                Tool tool = toolRepository.findByName(toolName).orElse(null);

                if (tool != null) {
                    // Collect parameter information
                    List<MissingDependencyException.ParameterInfo> paramInfos =
                            tool.getParameters().stream()
                                    .map(param -> new MissingDependencyException.ParameterInfo(
                                            param.getName(),
                                            param.getType().toString(),
                                            param.isRequired(),
                                            param.getDescription(),
                                            param.getDefaultValue()
                                    ))
                                    .collect(Collectors.toList());

                    // Collect dependency information
                    List<String> dependencies = dependencyRepository.findByTool(tool)
                            .stream()
                            .map(dep -> dep.getDependsOn().getName())
                            .collect(Collectors.toList());

                    // Create tool metadata
                    MissingDependencyException.ToolMetadata metadata =
                            new MissingDependencyException.ToolMetadata(
                                    tool.getName(),
                                    tool.getDescription(),
                                    paramInfos,
                                    dependencies
                            );

                    missingToolsMetadata.put(toolName, metadata);
                }
            }
        }
    }

    private Map<String, Set<String>> buildDependencyGraph(List<Intent> intents) {
        Map<String, Set<String>> graph = new HashMap<>();
        Set<String> allToolNames = new HashSet<>();

        // Initialize graph with empty dependency sets for each intent
        for (Intent intent : intents) {
            String toolName = intent.getIntent();
            graph.put(toolName, new HashSet<>());
            allToolNames.add(toolName);
        }

        // Populate dependencies for each tool
        for (String toolName : allToolNames) {
            Tool tool = toolRepository.findByName(toolName)
                    .orElseThrow(() -> {
                        log.error("Tool not found: {}", toolName);
                        return new ToolNotFoundException(toolName);
                    });

            List<Dependency> dependencies = dependencyRepository.findByTool(tool);

            for (Dependency dependency : dependencies) {
                String dependencyName = dependency.getDependsOn().getName();

                // Only include dependencies that are in the current intent list
                if (allToolNames.contains(dependencyName)) {
                    graph.get(toolName).add(dependencyName);
                    log.trace("Added dependency: {} -> {}", toolName, dependencyName);
                }
            }
        }

        return graph;
    }

    private List<Intent> topologicalSort(List<Intent> intents, Map<String, Set<String>> dependencyGraph) {
        // Create a map for intent lookup by name
        Map<String, Intent> intentMap = intents.stream()
                .collect(Collectors.toMap(Intent::getIntent, intent -> intent));

        // Calculate in-degree for each node (number of dependencies)
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : dependencyGraph.keySet()) {
            inDegree.put(node, 0);
        }
        for (Set<String> dependencies : dependencyGraph.values()) {
            for (String dependency : dependencies) {
                inDegree.put(dependency, inDegree.getOrDefault(dependency, 0) + 1);
            }
        }

        // Queue of nodes with no dependencies (in-degree of 0)
        Queue<String> queue = new LinkedList<>();
        for (String node : inDegree.keySet()) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        List<String> sortedNodes = new ArrayList<>();

        // Process the queue
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedNodes.add(current);

            // For each node that depends on the current node
            for (String dependent : dependencyGraph.keySet()) {
                Set<String> dependencies = dependencyGraph.get(dependent);
                if (dependencies.contains(current)) {
                    // Reduce its in-degree
                    inDegree.put(dependent, inDegree.get(dependent) - 1);

                    // If it has no more dependencies, add it to the queue
                    if (inDegree.get(dependent) == 0) {
                        queue.add(dependent);
                    }
                }
            }
        }

        // Map sorted nodes back to intents
        return sortedNodes.stream()
                .map(intentMap::get)
                .collect(Collectors.toList());
    }
}