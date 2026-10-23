package dev.tremolo.tasks;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    public enum Status { TODO, IN_PROGRESS, DONE }
    public record Input(@NotBlank @Size(max=200) String title,
                        @Size(max=5000) String description,
                        @NotNull Status status, LocalDate dueDate) {}
    public record Task(long id, String title, String description, Status status,
                       LocalDate dueDate, OffsetDateTime createdAt) {}
    private final JdbcTemplate jdbc;
    public TaskController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final org.springframework.jdbc.core.RowMapper<Task> MAPPER = (rs, row) ->
        new Task(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                 Status.valueOf(rs.getString("status")), rs.getObject("due_date", LocalDate.class),
                 rs.getObject("created_at", OffsetDateTime.class));
    @GetMapping
    public List<Task> list() {
        return jdbc.query("SELECT * FROM task_manager_tasks ORDER BY created_at DESC, id DESC", MAPPER);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody Input input) {
        return jdbc.queryForObject("INSERT INTO task_manager_tasks(title,description,status,due_date) VALUES (?,?,?,?) RETURNING *",
            MAPPER, input.title().strip(), description(input), input.status().name(), input.dueDate());
    }
    @PutMapping("/{id}")
    public Task update(@PathVariable long id, @Valid @RequestBody Input input) {
        var tasks = jdbc.query("UPDATE task_manager_tasks SET title=?,description=?,status=?,due_date=? WHERE id=? RETURNING *",
            MAPPER, input.title().strip(), description(input), input.status().name(), input.dueDate(), id);
        if (tasks.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        return tasks.getFirst();
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (jdbc.update("DELETE FROM task_manager_tasks WHERE id=?", id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
    }
    private String description(Input input) { return input.description() == null ? "" : input.description().strip(); }
}
