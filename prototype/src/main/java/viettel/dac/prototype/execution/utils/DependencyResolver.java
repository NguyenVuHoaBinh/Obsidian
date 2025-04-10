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
        List<String> intentNames = intents.stream()
                .map(intent -> intent.getIntent() + "#" + intent.getIntentId().substring(0, 8))
                .collect(Collectors.toList());
        log.trace("Intent list: {}", String.join(", ", intentNames));

        // Check for missing dependencies before building the graph
        checkMissingDependencies(intents);

        // Build a dependency graph using intent IDs as keys
        Map<String, Set<String>> dependencyGraph = buildDependencyGraph(intents);
        log.trace("Dependency graph built: {}", dependencyGraph);

        // Perform topological sorting
        List<Intent> result = topologicalSort(intents, dependencyGraph);

        log.debug("Resolved execution order: {}",
                result.stream()
                        .map(intent -> intent.getIntent() + "#" + intent.getIntentId().substring(0, 8) +
                                " (" + intent.getParameterSummary() + ")")
                        .collect(Collectors.joining(", ")));

        return result;
    }

    /**
     * Checks for missing dependencies with special handling for OR relationships
     */
    private void checkMissingDependencies(List<Intent> intents) {
        // Collect all tool names from intents - we can have multiple intents with the same name
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

    /**
     * Builds a dependency graph using intent IDs as keys instead of intent names.
     * This allows multiple instances of the same tool to be included in the execution order.
     */
    private Map<String, Set<String>> buildDependencyGraph(List<Intent> intents) {
        Map<String, Set<String>> graph = new HashMap<>();
        Map<String, Intent> intentMap = new HashMap<>();
        Map<String, List<Intent>> intentsByName = new HashMap<>();

        // Initialize graph with empty dependency sets for each intent
        // and build maps for efficient lookup
        for (Intent intent : intents) {
            String intentId = intent.getIntentId();
            graph.put(intentId, new HashSet<>());
            intentMap.put(intentId, intent);

            // Group intents by name for dependency resolution
            intentsByName.computeIfAbsent(intent.getIntent(), k -> new ArrayList<>())
                    .add(intent);
        }

        // Populate dependencies for each intent
        for (Intent intent : intents) {
            String intentId = intent.getIntentId();
            String toolName = intent.getIntent();

            Tool tool = toolRepository.findByName(toolName)
                    .orElseThrow(() -> {
                        log.error("Tool not found: {}", toolName);
                        return new ToolNotFoundException(toolName);
                    });

            List<Dependency> dependencies = dependencyRepository.findByTool(tool);

            for (Dependency dependency : dependencies) {
                String dependencyName = dependency.getDependsOn().getName();

                // Find all intents with this dependency name
                List<Intent> dependencyIntents = intentsByName.get(dependencyName);
                if (dependencyIntents != null && !dependencyIntents.isEmpty()) {
                    // If there are multiple instances of the dependency, choose the first one
                    // This is a simplification - in a more advanced implementation, you might
                    // want to consider parameter compatibility or other factors
                    String dependencyId = dependencyIntents.get(0).getIntentId();
                    graph.get(intentId).add(dependencyId);
                    log.trace("Added dependency: {} -> {}",
                            intent.getDisplayName(),
                            dependencyIntents.get(0).getDisplayName());
                }
            }
        }

        return graph;
    }

    /**
     * Performs a topological sort of the intents based on their dependencies.
     * Uses intent IDs to handle multiple instances of the same tool.
     * Does not check for circular dependencies.
     */
    private List<Intent> topologicalSort(List<Intent> intents, Map<String, Set<String>> dependencyGraph) {
        // Create a map for intent lookup by ID
        Map<String, Intent> intentMap = intents.stream()
                .collect(Collectors.toMap(Intent::getIntentId, intent -> intent));

        // Calculate in-degree for each node (number of dependencies)
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : dependencyGraph.keySet()) {
            inDegree.put(node, 0);
        }
        for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
            for (String dependency : entry.getValue()) {
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

        List<String> sortedNodeIds = new ArrayList<>();

        // Process the queue
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedNodeIds.add(current);

            // For each node in the graph
            for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
                String dependent = entry.getKey();
                Set<String> dependencies = entry.getValue();

                // If it depends on the current node
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

        // If we detect a circular dependency, still return what we've got so far
        // plus any remaining nodes (this is instead of throwing an exception)
        if (sortedNodeIds.size() < dependencyGraph.size()) {
            log.warn("Possible circular dependency detected in the execution graph. " +
                    "Continuing with partial ordering.");

            // Add any remaining nodes that weren't included in the sort
            Set<String> processedNodes = new HashSet<>(sortedNodeIds);
            for (String nodeId : dependencyGraph.keySet()) {
                if (!processedNodes.contains(nodeId)) {
                    sortedNodeIds.add(nodeId);
                    log.debug("Added unprocessed node to result: {}",
                            intentMap.get(nodeId).getDisplayName());
                }
            }
        }

        // Map sorted nodes back to intents
        return sortedNodeIds.stream()
                .map(intentMap::get)
                .collect(Collectors.toList());
    }
}