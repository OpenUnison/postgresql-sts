package dev.tremolo.tasks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(TaskController.class)
class TaskControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @Test void rejectsBlankTitleBeforeDatabaseAccess() throws Exception {
        mvc.perform(post("/api/tasks").contentType("application/json")
            .content("{\"title\":\"   \",\"status\":\"TODO\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsUnknownStatusBeforeDatabaseAccess() throws Exception {
        mvc.perform(post("/api/tasks").contentType("application/json")
            .content("{\"title\":\"Test\",\"status\":\"INVALID\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
    }
    @Test void missingTaskDeleteReturns404() throws Exception {
        mvc.perform(delete("/api/tasks/123")).andExpect(status().isNotFound());
    }
    @Test void existingTaskDeleteReturns204() throws Exception {
        when(jdbc.update("DELETE FROM task_manager_tasks WHERE id=?", 123L)).thenReturn(1);
        mvc.perform(delete("/api/tasks/123")).andExpect(status().isNoContent());
    }
}
