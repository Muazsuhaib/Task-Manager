package com.taskmanager.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.services.blocking.MessageService;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmanager.dto.TaskRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.model.Priority;
import com.taskmanager.model.Status;
import com.taskmanager.model.Task;
import com.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock private TaskRepository taskRepository;
    @Mock private AnthropicClient anthropicClient;
    @Mock private MessageService messagesApi;
    private ObjectMapper objectMapper;
    @InjectMocks private TaskService taskService;
    private Task sampleTask;

    @BeforeEach void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ReflectionTestUtils.setField(taskService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(taskService, "anthropicClient", anthropicClient);
        sampleTask = Task.builder().id(1L).title("Write unit tests").description("Cover the service layer thoroughly").dueDate(LocalDate.of(2025, 6, 1)).priority(Priority.HIGH).status(Status.TODO).build();
        ReflectionTestUtils.setField(sampleTask, "createdAt", LocalDateTime.now());
    }

    @Test void getAllTasks_returnsAllTasks() {
        when(taskRepository.findAll()).thenReturn(List.of(sampleTask));
        List<TaskResponse> result = taskService.getAllTasks();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Write unit tests");
        verify(taskRepository, times(1)).findAll();
    }
    @Test void getTaskById_returnsTask_whenExists() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        TaskResponse result = taskService.getTaskById(1L);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
    }
    @Test void getTaskById_throwsException_whenNotFound() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.getTaskById(99L)).isInstanceOf(RuntimeException.class).hasMessageContaining("99");
    }
    @Test void createTask_savesAndReturnsTask() {
        TaskRequest request = TaskRequest.builder().title("New task").description("A fresh task").dueDate(LocalDate.of(2025, 7, 1)).priority(Priority.LOW).status(Status.TODO).build();
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            ReflectionTestUtils.setField(t, "id", 2L);
            ReflectionTestUtils.setField(t, "createdAt", LocalDateTime.now());
            return t;
        });
        TaskResponse result = taskService.createTask(request);
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getTitle()).isEqualTo("New task");
        verify(taskRepository, times(1)).save(any(Task.class));
    }
    @Test void updateTask_updatesAndReturnsTask() {
        TaskRequest updateRequest = TaskRequest.builder().title("Updated title").description("Updated description").dueDate(LocalDate.of(2025, 8, 15)).priority(Priority.MEDIUM).status(Status.IN_PROGRESS).build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskResponse result = taskService.updateTask(1L, updateRequest);
        assertThat(result.getTitle()).isEqualTo("Updated title");
        assertThat(result.getStatus()).isEqualTo(Status.IN_PROGRESS);
        verify(taskRepository).save(any(Task.class));
    }
    @Test void deleteTask_deletesTask_whenExists() {
        when(taskRepository.existsById(1L)).thenReturn(true);
        doNothing().when(taskRepository).deleteById(1L);
        assertThatCode(() -> taskService.deleteTask(1L)).doesNotThrowAnyException();
        verify(taskRepository).deleteById(1L);
    }
    @Test void deleteTask_throwsException_whenNotFound() {
        when(taskRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> taskService.deleteTask(99L)).isInstanceOf(RuntimeException.class).hasMessageContaining("99");
        verify(taskRepository, never()).deleteById(any());
    }
    @Test void suggestTask_returnsTaskRequest_whenValidDescription() {
        String fakeJson = "{\"title\":\"Submit quarterly report\",\"description\":\"Compile Q2 report\",\"dueDate\":\"2025-05-23\",\"priority\":\"HIGH\",\"status\":\"TODO\"}";
        ContentBlock contentBlock = mock(ContentBlock.class);
        TextBlock textBlock = mock(TextBlock.class);
        when(contentBlock.isText()).thenReturn(true);
        when(contentBlock.asText()).thenReturn(textBlock);
        when(textBlock.text()).thenReturn(fakeJson);
        Message mockMessage = mock(Message.class);
        when(mockMessage.content()).thenReturn(List.of(contentBlock));
        when(anthropicClient.messages()).thenReturn(messagesApi);
        when(messagesApi.create(any(MessageCreateParams.class))).thenReturn(mockMessage);
        TaskRequest result = taskService.suggestTask("remind me to submit the quarterly report before Friday");
        assertThat(result.getTitle()).isEqualTo("Submit quarterly report");
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
        verify(messagesApi).create(any(MessageCreateParams.class));
    }
}
