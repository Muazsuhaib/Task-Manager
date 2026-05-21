package com.taskmanager.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanager.dto.TaskRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.model.Priority;
import com.taskmanager.model.Status;
import com.taskmanager.model.Task;
import com.taskmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private AnthropicClient anthropicClient;

    public List<TaskResponse> getAllTasks() {
        return taskRepository.findAll()
                .stream()
                .map(TaskResponse::from)
                .collect(Collectors.toList());
    }

    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
        return TaskResponse.from(task);
    }

    public TaskResponse createTask(TaskRequest request) {
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .priority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM)
                .status(request.getStatus() != null ? request.getStatus() : Status.TODO)
                .build();

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    public TaskResponse updateTask(Long id, TaskRequest request) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDueDate(request.getDueDate());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getStatus() != null) task.setStatus(request.getStatus());

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new RuntimeException("Task not found with id: " + id);
        }
        taskRepository.deleteById(id);
    }

    public TaskRequest suggestTask(String description) {
        if (anthropicClient == null) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is not configured.");
        }

        String prompt = """
                You are a task management assistant. A user has described a task in plain language.
                Your job is to parse it into a structured task object.

                User description: "%s"

                Respond with ONLY a valid JSON object — no explanation, no markdown, no code fences.
                Use exactly these fields:
                {
                  "title": "short, clear task title",
                  "description": "expanded description of what needs to be done",
                  "dueDate": "YYYY-MM-DD or null if no date is implied",
                  "priority": "LOW | MEDIUM | HIGH",
                  "status": "TODO | IN_PROGRESS | DONE"
                }

                Guidelines:
                - title should be concise (under 80 characters)
                - Infer priority from urgency words (e.g. "urgent", "ASAP" → HIGH; "whenever" → LOW)
                - Infer dueDate from relative expressions like "before Friday" or "by end of month"; use today's date (%s) as the reference
                - status should almost always be TODO unless the user implies it is already started or done
                - If dueDate cannot be determined, use null (not the string "null")
                """.formatted(description, LocalDate.now());

        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(512L)
                .addUserMessage(prompt)
                .build();

        Message message = anthropicClient.messages().create(params);

        String rawJson = message.content().stream()
                .filter(block -> block.isText())
                .map(block -> block.asText().text())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Claude returned an empty response"));

        return parseTaskRequestFromJson(rawJson.trim());
    }

    private TaskRequest parseTaskRequestFromJson(String json) {
        try {
            String cleaned = json
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode node = objectMapper.readTree(cleaned);

            String title = node.path("title").asText("Untitled Task");
            String description = node.path("description").asText(null);

            LocalDate dueDate = null;
            JsonNode dueDateNode = node.path("dueDate");
            if (!dueDateNode.isNull() && !dueDateNode.isMissingNode() && !dueDateNode.asText().isBlank()) {
                try {
                    dueDate = LocalDate.parse(dueDateNode.asText());
                } catch (Exception e) {
                    log.warn("Could not parse dueDate '{}' from Claude response, leaving null", dueDateNode.asText());
                }
            }

            Priority priority = Priority.MEDIUM;
            try {
                priority = Priority.valueOf(node.path("priority").asText("MEDIUM").toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown priority value from Claude, defaulting to MEDIUM");
            }

            Status status = Status.TODO;
            try {
                status = Status.valueOf(node.path("status").asText("TODO").toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown status value from Claude, defaulting to TODO");
            }

            return TaskRequest.builder()
                    .title(title)
                    .description(description)
                    .dueDate(dueDate)
                    .priority(priority)
                    .status(status)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Claude JSON response: {}", json, e);
            throw new RuntimeException("Failed to parse Claude response into a task: " + e.getMessage());
        }
    }
}
