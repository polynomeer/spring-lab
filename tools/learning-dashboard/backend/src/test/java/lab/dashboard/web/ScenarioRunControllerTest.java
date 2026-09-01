package lab.dashboard.web;

import java.util.List;

import lab.dashboard.session.RawHitReceived;
import lab.dashboard.session.ScenarioExited;
import lab.dashboard.session.ScenarioStarted;
import lab.tools.jdi.TraceEvent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// ScenarioWebSocketControllerTest와 같은 패턴 - 실제 ScenarioSession/프로세스를 거치지
// 않고, ApplicationEventPublisher로 직접 도메인 이벤트를 발행해서 ScenarioRunRecorder가
// 진짜로 기록하고 이 컨트롤러가 그걸 진짜로 돌려주는지(실제 H2 DB를 거쳐) 확인한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioRunControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void listsAndFetchesARecordedRunThroughTheRealPipeline() {
        String scenarioName = "run-controller-test-" + System.nanoTime();

        eventPublisher.publishEvent(new ScenarioStarted(scenarioName));
        eventPublisher.publishEvent(new RawHitReceived(scenarioName, sampleHit()));
        eventPublisher.publishEvent(new ScenarioExited(scenarioName, 1));

        ScenarioRunSummary[] summaries = rest.getForObject(
                url("/api/scenario-runs?scenarioName=" + scenarioName), ScenarioRunSummary[].class);
        assertThat(summaries).hasSize(1);
        assertThat(summaries[0].totalHits()).isEqualTo(1);
        assertThat(summaries[0].timedOut()).isFalse();

        ScenarioRunDetail detail = rest.getForObject(
                url("/api/scenario-runs/" + summaries[0].id()), ScenarioRunDetail.class);
        assertThat(detail.events()).hasSize(2);
        assertThat(detail.events().get(0).get("type").asText()).isEqualTo("hit");
        assertThat(detail.events().get(1).get("type").asText()).isEqualTo("exited");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                url("/api/scenario-runs/" + summaries[0].id()), HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ScenarioRunSummary[] afterDelete = rest.getForObject(
                url("/api/scenario-runs?scenarioName=" + scenarioName), ScenarioRunSummary[].class);
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void fetchingAnUnknownRunIdReturnsNotFound() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/scenario-runs/999999999"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private TraceEvent sampleHit() {
        return new TraceEvent(1, 0L, "main",
                new TraceEvent.Location("lab.dynamic.Demo", "main", 10), List.of(), List.of(), true);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
