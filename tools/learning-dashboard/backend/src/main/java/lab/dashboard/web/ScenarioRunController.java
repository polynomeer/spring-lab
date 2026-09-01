package lab.dashboard.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lab.dashboard.scenario.ScenarioRunEntity;
import lab.dashboard.scenario.ScenarioRunRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/plan/04-dynamic-scenario-design.md 7번 절 "실행 히스토리 스냅샷" 조회 API - 읽기
 * 전용이다. 기록 자체는 {@code lab.dashboard.session.ScenarioRunRecorder}가 이벤트를
 * 구독해서 자동으로 하므로, 이 컨트롤러에는 POST/PUT이 없다.
 */
@RestController
@RequestMapping("/api/scenario-runs")
public class ScenarioRunController {

    private final ScenarioRunRepository repository;
    private final ObjectMapper mapper;

    public ScenarioRunController(ScenarioRunRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @GetMapping
    public List<ScenarioRunSummary> list(@RequestParam String scenarioName) {
        return repository.findByScenarioNameOrderByStartedAtDesc(scenarioName).stream()
                .map(ScenarioRunSummary::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ScenarioRunDetail detail(@PathVariable Long id) {
        ScenarioRunEntity entity = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("no scenario run with id " + id));
        return new ScenarioRunDetail(
                entity.getId(), entity.getScenarioName(), entity.getStartedAt(), entity.getFinishedAt(),
                entity.getTotalHits(), entity.isTimedOut(), parseEvents(entity.getEventsJson()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }

    private List<JsonNode> parseEvents(String eventsJson) {
        try {
            JsonNode array = mapper.readTree(eventsJson);
            List<JsonNode> events = new ArrayList<>();
            array.forEach(events::add);
            return events;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }
}
