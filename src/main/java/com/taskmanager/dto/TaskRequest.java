package com.taskmanager.dto;
import com.taskmanager.model.Priority;
import com.taskmanager.model.Status;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequest {
    @NotBlank(message = "Title is required")
    private String title;
    private String description;
    private LocalDate dueDate;
    @Builder.Default
    private Priority priority = Priority.MEDIUM;
    @Builder.Default
    private Status status = Status.TODO;
}
