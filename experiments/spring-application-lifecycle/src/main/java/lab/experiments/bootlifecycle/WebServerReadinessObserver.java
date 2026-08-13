package lab.experiments.bootlifecycle;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

// "웹 서버가 떴다"(WebServerInitializedEvent)는 이벤트 이름 하나에 실제로는 서로 다른 질문
// 셋이 섞여 있다: (1) 포트가 정말로 TCP 연결을 받아 주는가, (2) 이 애플리케이션의 평범한
// 빈(@Component)들이 이미 만들어져 있는가, (3) 이 둘이 항상 같이 참인가. LifecycleObserver와
// 같은 방식(SpringApplication.addListeners())으로 등록해서, 이벤트가 발행되는 바로 그 순간에
// 셋 다 직접 확인한다 - 소스를 추론하는 대신 실행해서 확인한다는 이 저장소의 방법론 그대로다.
public final class WebServerReadinessObserver implements ApplicationListener<WebServerInitializedEvent> {

    private Snapshot snapshot;

    public record Snapshot(String eventName, int port, boolean portAcceptsConnections, boolean markerBeanAvailable) {
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        snapshot = new Snapshot(event.getClass().getSimpleName(), port, portAcceptsConnections(port),
                markerBeanAvailable(event));
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private boolean portAcceptsConnections(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean markerBeanAvailable(WebServerInitializedEvent event) {
        try {
            event.getApplicationContext().getBean(MarkerBean.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
