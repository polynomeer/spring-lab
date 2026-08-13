package lab.experiments.bootlifecycle;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// 17주차 문서(spring-application.md) 8번 절이 "미룬 것"으로 남겨 뒀던 질문 - "웹 서버가
// 실제로 언제 뜨는가" - 를 실제 내장 톰캣으로 확인한다. 당시엔 자동 설정(18주차) 메커니즘을
// 먼저 다뤄야 해서 미뤘는데, 이제 그 메커니즘을 이미 다뤘으니 같은 SpringApplication 생명주기
// 실험에 실제 WebApplicationType.SERVLET 실행을 더한다.
class WebServerStartupTimingTest {

    @Test
    void webServerInitializedEventFiresDuringRefreshBeforeApplicationBeansExistButAfterThePortAcceptsConnections() {
        SpringApplication application = new SpringApplication(WebLifecycleConfig.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Collections.singletonMap("server.port", "0"));

        LifecycleObserver lifecycleObserver = new LifecycleObserver();
        WebServerReadinessObserver readinessObserver = new WebServerReadinessObserver();
        application.addListeners(lifecycleObserver, readinessObserver);

        ConfigurableApplicationContext context = application.run();
        try {
            List<String> eventNames = lifecycleObserver.observations().stream()
                    .map(LifecycleObserver.Observation::eventName)
                    .toList();

            // ServletWebServerApplicationContext#onRefresh()(refresh()의 12단계 중 9번째)에서
            // WebServer 객체를 만들지만, 실제로 start()해서 이 이벤트를 발행하는 건
            // WebServerStartStopLifecycle이라는 SmartLifecycle 빈이다 - 그래서 이 이벤트는 항상
            // ApplicationPreparedEvent(refresh() 호출 전) 이후, ApplicationStartedEvent
            // (refreshContext() 완료 후) 이전에 낀다.
            assertThat(eventNames).containsSubsequence(
                    "ApplicationPreparedEvent", "ServletWebServerInitializedEvent", "ApplicationStartedEvent");

            WebServerReadinessObserver.Snapshot snapshot = readinessObserver.snapshot();
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.eventName()).isEqualTo("ServletWebServerInitializedEvent");
            assertThat(snapshot.port()).isGreaterThan(0);

            // 포트는 이 시점에 이미 실제로 TCP 연결을 받아 준다.
            assertThat(snapshot.portAcceptsConnections()).isTrue();

            // 예상과 달랐던 지점: onRefresh()의 createWebServer()는 웹 서버를 "시작"하지 않는다
            // - WebServer 인스턴스를 만든 뒤 WebServerStartStopLifecycle이라는 SmartLifecycle
            // 빈을 registerSingleton()으로 직접 등록해 둘 뿐이다(바이트코드로 직접 확인함).
            // 실제 webServer.start() + ServletWebServerInitializedEvent 발행은 그 빈의
            // start()가 호출될 때 일어나는데, SmartLifecycle 빈은 refresh()의 12단계 중 마지막
            // 단계인 finishRefresh()에서(LifecycleProcessor#onRefresh()를 통해) 시작된다 -
            // finishBeanFactoryInitialization()(모든 싱글턴 생성, 11번째 단계) "뒤"다. 그래서
            // 이 이벤트가 발행되는 시점에는 MarkerBean 같은 평범한 애플리케이션 빈도 이미 전부
            // 만들어져 있다 - "포트가 열린 것"과 "이 애플리케이션의 빈이 준비된 것"이 서로 다른
            // 단계일 거라 예상했는데, 실제로는 같은 refresh() 마지막 단계에 붙어 있었다.
            assertThat(snapshot.markerBeanAvailable()).isTrue();

            // run()이 완전히 끝난 뒤(ApplicationReadyEvent까지 지난 뒤)에는 당연히 실제 HTTP
            // 요청도 정상적으로 처리된다 - 컨트롤러 빈까지 전부 준비된 상태다.
            String localPort = context.getEnvironment().getProperty("local.server.port");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + localPort + "/probe")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("ok");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            context.close();
        }
    }
}
