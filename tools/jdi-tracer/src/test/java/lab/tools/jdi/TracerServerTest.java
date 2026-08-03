package lab.tools.jdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// TracerServer의 NDJSON stdin/stdout 프로토콜을 실제 자식 프로세스로 띄워서 검증한다 -
// docs/plan/03-learning-dashboard-design.md 0단계가 요구하는 "UI 없이 간단한 테스트
// 클라이언트/스크립트로 검증"을, 이 JUnit 테스트 자체가 그 "테스트 클라이언트" 역할로
// 수행한다.
class TracerServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private Process process;
    private BlockingQueue<String> outputLines;
    private PrintWriter stdin;

    @AfterEach
    void tearDown() {
        if (process != null) {
            process.destroyForcibly();
        }
    }

    @Test
    void firstHitArrivesFreelyThenEachFurtherHitWaitsForItsOwnStepSignal() throws Exception {
        startServer("lab.tools.jdi.fixtures.SampleTarget#greet");

        // 최초 vm.resume()만으로 첫 번째 브레이크포인트까지는 자유롭게 도달한다 - step 명령이
        // 필요 없다(실제 디버거를 attach했을 때 첫 브레이크포인트까지 자동으로 멈추는 것과
        // 같은 동작이다).
        JsonNode hit1 = awaitEventOfType("hit");
        assertThat(hit1.at("/event/hitId").asInt()).isEqualTo(1);
        assertThat(hit1.at("/event/location/methodName").asText()).isEqualTo("greet");
        assertThat(hit1.at("/event/location/className").asText()).isEqualTo("lab.tools.jdi.fixtures.SampleTarget");
        assertThat(hit1.at("/event/localsAvailable").asBoolean()).isTrue();
        assertThat(nameLocalValue(hit1)).contains("\"Alice\"");

        // 아직 아무 명령도 보내지 않았다 - 두 번째 히트도, 프로세스 종료도 일어날 수 없어야
        // 한다. hit1을 "보고"한 것과 hit1을 "지나간" 것은 다르다: 리포트는 이미 됐지만
        // 다음으로 넘어가려면 반드시 신호가 필요하다.
        assertThat(pollEventOfType("hit", Duration.ofMillis(400))).isNull();
        assertThat(pollEventOfType("exited", Duration.ofMillis(100))).isNull();

        send("{\"cmd\":\"step\"}");
        JsonNode hit2 = awaitEventOfType("hit");
        assertThat(hit2.at("/event/hitId").asInt()).isEqualTo(2);
        assertThat(nameLocalValue(hit2)).contains("\"Bob\"");

        // 여기서도 마찬가지 - 아직 종료 이벤트가 오면 안 된다.
        assertThat(pollEventOfType("exited", Duration.ofMillis(400))).isNull();

        // "play"로 전환하면 이후 step 명령 없이도 남은 실행 + 종료까지 자동 진행된다.
        send("{\"cmd\":\"play\",\"intervalMs\":50}");
        JsonNode exited = awaitEventOfType("exited");
        assertThat(exited.get("totalHits").asInt()).isEqualTo(2);
    }

    private static java.util.Optional<String> nameLocalValue(JsonNode hit) {
        for (JsonNode local : hit.at("/event/locals")) {
            if ("name".equals(local.get("name").asText())) {
                return java.util.Optional.of(local.get("value").asText());
            }
        }
        return java.util.Optional.empty();
    }

    @Test
    void quitTerminatesTheTargetProcessWithoutWaitingForCompletion() throws Exception {
        startServer("lab.tools.jdi.fixtures.SampleTarget#greet");

        // 첫 히트까지는 step 없이 도달한다 - 그 상태(hit1에 멈춘 채)에서 바로 quit한다.
        awaitEventOfType("hit");

        send("{\"cmd\":\"quit\"}");
        boolean exitedInTime = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(exitedInTime).as("server process exits promptly after quit").isTrue();
    }

    private void startServer(String breakpointSpec) throws IOException {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder builder = new ProcessBuilder(
                javaBin, "--add-modules", "jdk.jdi",
                "-cp", classpath,
                "lab.tools.jdi.TracerServer",
                classpath, "lab.tools.jdi.fixtures.SampleTarget", breakpointSpec);
        builder.redirectErrorStream(false);
        process = builder.start();

        outputLines = new LinkedBlockingQueue<>();
        Thread pump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.offer(line);
                }
            } catch (IOException ignored) {
            }
        });
        pump.setDaemon(true);
        pump.start();

        stdin = new PrintWriter(process.getOutputStream(), true);
    }

    private void send(String json) {
        stdin.println(json);
        stdin.flush();
    }

    /** 지정한 타입의 이벤트가 timeout 안에 도착하면 반환하고, 없으면(실패로 처리하지 않고) null. */
    private JsonNode pollEventOfType(String type, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            long remainingMillis = Math.max(0, (deadline - System.nanoTime()) / 1_000_000);
            String line = outputLines.poll(remainingMillis, TimeUnit.MILLISECONDS);
            if (line == null) {
                continue;
            }
            JsonNode node = MAPPER.readTree(line);
            if (type.equals(node.path("type").asText())) {
                return node;
            }
        }
        return null;
    }

    private JsonNode awaitEventOfType(String type) throws Exception {
        JsonNode node = pollEventOfType(type, TIMEOUT);
        if (node == null) {
            fail("timed out waiting for event of type \"" + type + "\"");
        }
        return node;
    }
}
