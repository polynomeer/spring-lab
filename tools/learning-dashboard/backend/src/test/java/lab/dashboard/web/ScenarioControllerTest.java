package lab.dashboard.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 스캐폴딩(진짜 H2, 진짜 settings.gradle.kts 파싱) 위에서 REST 계층 자체를 검증한다 -
// 이 저장소의 다른 대시보드 테스트들(ScenarioStartupErrorTest 등)과 같은 이유로 Mockito를
// 쓰지 않는다. gradlew 셸아웃(ClasspathResolver)은 여전히 안 탄다 - 시나리오를 "실행"하는
// 게 아니라 정의를 저장/조회/삭제하는 것뿐이라 필요 없다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioControllerTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void listsAtLeastTheSixSeededScenarios() {
        ScenarioResponse[] scenarios = rest.getForObject(url("/api/scenarios"), ScenarioResponse[].class);

        assertThat(scenarios).extracting(ScenarioResponse::name).contains(
                "bean-lifecycle", "aop-proxy", "tx-propagation", "dispatcher-flow", "event-multicast",
                "mvc-exception-priority");
    }

    @Test
    void listsGradleModulePathsParsedFromSettingsFile() {
        String[] modules = rest.getForObject(url("/api/scenarios/modules"), String[].class);

        assertThat(modules).contains("experiments:ioc-container-lab", "tools:jdi-tracer");
    }

    @Test
    void createReadUpdateDeleteRoundTripsThroughTheRealDatabase() {
        ScenarioSaveRequest create = new ScenarioSaveRequest(
                "controller-test-scenario", "컨트롤러 테스트용", "임시 시나리오",
                List.of("experiments:ioc-container-lab"), "lab.experiments.ioc.BeanFactoryLab",
                "org.springframework.beans.factory.support.DefaultListableBeanFactory#getBean", null);

        ResponseEntity<ScenarioResponse> created = rest.postForEntity(url("/api/scenarios"), create, ScenarioResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = created.getBody().id();

        ScenarioResponse[] afterCreate = rest.getForObject(url("/api/scenarios"), ScenarioResponse[].class);
        assertThat(afterCreate).extracting(ScenarioResponse::name).contains("controller-test-scenario");

        ScenarioSaveRequest update = new ScenarioSaveRequest(
                "controller-test-scenario", "제목을 바꿈", "설명도 바꿈",
                List.of("experiments:ioc-container-lab"), "lab.experiments.ioc.BeanFactoryLab",
                "org.springframework.beans.factory.support.DefaultListableBeanFactory#getBean", null);
        rest.put(url("/api/scenarios/" + id), update);

        ScenarioResponse[] afterUpdate = rest.getForObject(url("/api/scenarios"), ScenarioResponse[].class);
        assertThat(afterUpdate)
                .filteredOn(s -> s.name().equals("controller-test-scenario"))
                .extracting(ScenarioResponse::title)
                .containsExactly("제목을 바꿈");

        ResponseEntity<Void> deleteResponse = rest.exchange(
                url("/api/scenarios/" + id), org.springframework.http.HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ScenarioResponse[] afterDelete = rest.getForObject(url("/api/scenarios"), ScenarioResponse[].class);
        assertThat(afterDelete).extracting(ScenarioResponse::name).doesNotContain("controller-test-scenario");
    }

    @Test
    void creatingWithADuplicateNameIsRejectedWithConflict() {
        ScenarioSaveRequest duplicate = new ScenarioSaveRequest(
                "bean-lifecycle", "이미 있는 이름", "설명",
                List.of("experiments:ioc-container-lab"), "lab.experiments.ioc.BeanFactoryLab",
                "org.springframework.beans.factory.support.DefaultListableBeanFactory#getBean", null);

        ResponseEntity<String> response = rest.postForEntity(url("/api/scenarios"), duplicate, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void exportsAMarkdownSkeletonWithTodoPlaceholdersWhenNoRunExists() {
        String markdown = rest.getForObject(url("/api/scenarios/export?name=bean-lifecycle"), String.class);

        assertThat(markdown).contains("## 1. 이번 질문", "## 8. 런타임 관찰", "## 12. 결론 (예상과 실제의 차이)");
        assertThat(markdown).contains("<!-- TODO");
        assertThat(markdown).contains("## 7. 브레이크포인트");
    }

    @Test
    void exportingAnUnknownScenarioNameIsNotFound() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/scenarios/export?name=no-such-scenario"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
