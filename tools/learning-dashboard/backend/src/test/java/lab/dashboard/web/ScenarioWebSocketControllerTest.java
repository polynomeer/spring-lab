package lab.dashboard.web;

import com.fasterxml.jackson.databind.JsonNode;

import lab.dashboard.interpret.SemanticEvent;
import lab.dashboard.session.SemanticEventReceived;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// ScenarioSession의 프로세스 관리는 ScenarioSessionTest가 이미 실제로 검증했다 - 여기서는
// 이 컨트롤러가 하는 유일한 일, 즉 "Spring 이벤트를 구독해서 STOMP로 그대로 중계한다"는
// 배선 자체만 검증한다. 그래서 실제 시나리오를 start()하지 않고(=gradlew 셸아웃 없이),
// ApplicationEventPublisher로 직접 이벤트를 발행해서 구독자에게 도착하는지 확인한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioWebSocketControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void relaysAnInternallyPublishedSemanticEventToStompSubscribers() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
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

        // 구독이 브로커에 실제로 반영될 시간을 준다 - 비동기 STOMP 연결/구독의 흔한 필요.
        Thread.sleep(300);

        eventPublisher.publishEvent(new SemanticEventReceived("fixture-scenario",
                SemanticEvent.of("TEST_SEMANTIC_EVENT", 7, "beanName", "demoBean")));

        JsonNode payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload.get("type").asText()).isEqualTo("semantic");
        assertThat(payload.get("scenario").asText()).isEqualTo("fixture-scenario");
        assertThat(payload.get("event").get("type").asText()).isEqualTo("TEST_SEMANTIC_EVENT");
        assertThat(payload.get("event").get("attributes").get("beanName").asText()).isEqualTo("demoBean");

        session.disconnect();
    }
}
