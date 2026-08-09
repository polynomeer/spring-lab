package lab.ext.clientregistry;

import java.util.List;

import org.springframework.stereotype.Component;

// 동적으로 등록된 ExternalApiClient들이 "특별 취급"되는 게 아니라, 컴포넌트 스캔으로 찾은
// 평범한 빈과 완전히 동등하게 일반 DI 후보로 참여한다는 걸 보여주기 위한 소비자다 -
// List<ExternalApiClient>로 전부 모아 받는다.
@Component
public class ExternalApiClientConsumer {

    private final List<ExternalApiClient> clients;

    public ExternalApiClientConsumer(List<ExternalApiClient> clients) {
        this.clients = clients;
    }

    public List<ExternalApiClient> getClients() {
        return clients;
    }
}
