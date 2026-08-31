package lab.dashboard.session;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// ScenarioWebSocketControllerTest와 같은 STOMP 테스트 클라이언트 패턴을 쓴다. 이 테스트는
// "@MessageMapping 메서드 밖으로 새어 나온 예외가 /topic/scenario로 중계되는가"만 검증한다 -
// 실제로 겪었던 버그(존재하지 않는 gradlew 경로 때문에 ClasspathResolver가
// IllegalStateException을 던지는데도 브라우저에는 아무 신호가 안 갔던 것)를 그대로 재현한다.
// Mockito 없이, 존재하지 않는 저장소 루트를 가리키는 진짜 ClasspathResolver로 같은 실패를
// 결정론적이고 빠르게(ProcessBuilder가 gradlew 자체를 못 찾아 즉시 IOException) 재현한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ScenarioStartupErrorTest.BrokenClasspathResolverConfig.class)
class ScenarioStartupErrorTest {

    @TestConfiguration
    static class BrokenClasspathResolverConfig {

        @Bean
        @Primary
        ClasspathResolver classpathResolver() {
            return new ClasspathResolver(Path.of(System.getProperty("java.io.tmpdir"), "no-such-repo-root-" + UUID.randomUUID()));
        }
    }

    @LocalServerPort
    private int port;

    private StompSession connect(BlockingQueue<JsonNode> received) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/scenario", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((JsonNode) payload);
            }
        });
        Thread.sleep(300);
        return session;
    }

    @Test
    void anUnknownScenarioNameProducesAnErrorMessageInsteadOfSilence() throws Exception {
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        StompSession session = connect(received);

        session.send("/app/scenario/does-not-exist/start", null);

        JsonNode payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.get("type").asText()).isEqualTo("error");
        assertThat(payload.get("message").asText()).contains("unknown scenario");

        session.disconnect();
    }

    @Test
    void aClasspathResolutionFailureDuringStartupIsSurfacedAsAnErrorMessage() throws Exception {
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        StompSession session = connect(received);

        // "bean-lifecycle"은 카탈로그에 실제로 등록된 이름이다 - unknown-scenario 브랜치가
        // 아니라, classpathResolver.resolve()가 던지는 IllegalStateException 쪽 경로를 탄다.
        session.send("/app/scenario/bean-lifecycle/start", null);

        JsonNode payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.get("type").asText()).isEqualTo("error");
        assertThat(payload.get("message").asText()).contains("failed to resolve classpath");

        session.disconnect();
    }
}
