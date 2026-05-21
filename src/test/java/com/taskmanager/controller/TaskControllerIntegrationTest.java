package com.taskmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanager.dto.TaskRequest;
import com.taskmanager.dto.TaskResponse;
import com.taskmanager.model.Priority;
import com.taskmanager.model.Status;
import com.taskmanager.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private TaskService taskService;

    private TaskResponse sampleResponse() {
        return TaskResponse.builder().id(1L).title("Write integration tests").description("Cover all CRUD endpoints").dueDate(LocalDate.of(2025, 6, 1)).priority(Priority.HIGH).status(Status.TODO).createdAt(LocalDateTime.of(2025, 5, 20, 9, 0)).build();
    }
    private TaskRequest sampleRequest() {
        return TaskRequest.builder().title("Write integration tests").description("Cover all CRUD endpoints").dueDate(LocalDate.of(2025, 6, 1)).priority(Priority.HIGH).status(Status.TODO).build();
    }
    @Test void createTask_returns201() throws Exception {
        when(taskService.createTask(any(TaskRequest.class))).thenReturn(sampleResponse());
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(sampleRequest()))).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.title").value("Write integration tests"));
    }
    @Test void createTask_returns400_whenTitleMissing() throws Exception {
        TaskRequest bad = TaskRequest.builder().description("no title here").build();
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bad))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }
    @Test void getAllTasks_returns200() throws Exception {
        when(taskService.getAllTasks()).thenReturn(List.of(sampleResponse()));
        mockMvc.perform(get("/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$[0].id").value(1));
    }
    @Test void getTaskById_returns200() throws Exception {
        when(taskService.getTaskById(1L)).thenReturn(sampleResponse());
        mockMvc.perform(get("/tasks/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }
    @Test void getTaskById_returns404_whenNotFound() throws Exception {
        when(taskService.getTaskById(99L)).thenThrow(new RuntimeException("Task not found with id: 99"));
        mockMvc.perform(get("/tasks/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Task not found with id: 99"));
    }
    @Test void updateTask_returns200() throws Exception {
        TaskResponse updated = TaskResponse.builder().id(1L).title("Updated title").description("Updated description").priority(Priority.MEDIUM).status(Status.IN_PROGRESS).createdAt(LocalDateTime.of(2025, 5, 20, 9, 0)).build();
        when(taskService.updateTask(eq(1L), any(TaskRequest.class))).thenReturn(updated);
        TaskRequest updateReq = TaskRequest.builder().title("Updated title").description("Updated description").priority(Priority.MEDIUM).status(Status.IN_PROGRESS).build();
        mockMvc.perform(put("/tasks/1").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateReq))).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated title")).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
    @Test void deleteTask_returns204() throws Exception {
        doNothing().when(taskService).deleteTask(1L);
        mockMvc.perform(delete("/tasks/1")).andExpect(status().isNoContent());
    }
    @Test void deleteTask_returns404_whenNotFound() throws Exception {
        doThrow(new RuntimeException("Task not found with id: 99")).when(taskService).deleteTask(99L);
        mockMvc.perform(delete("/tasks/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Task not found with id: 99"));
    }
    @Test void suggestTask_returns200() throws Exception {
        TaskRequest suggested = TaskRequest.builder().title("Submit quarterly report").description("Compile and submit the Q2 financial report").dueDate(LocalDate.of(2025, 5, 23)).priority(Priority.HIGH).status(Status.TODO).build();
        when(taskService.suggestTask(any(String.class))).thenReturn(suggested);
        Map<String, String> body = Map.of("description", "remind me to submit the quarterly report before Friday");
        mockMvc.perform(post("/tasks/suggest").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Submit quarterly report")).andExpect(jsonPath("$.priority").value("HIGH"));
    }
    @Test void suggestTask_returns400_whenDescriptionMissing() throws Exception {
        Map<String, String> body = Map.of("description", "");
        mockMvc.perform(post("/tasks/suggest").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }
}
