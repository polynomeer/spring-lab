package lab.sampleapp.plugindiscovery;

// 카탈로그 프로젝트 11이 명시한 인터페이스 그대로 - type()이 "이 플러그인을 런타임에 어떤
// 이름으로 찾을 것인가"를 정의하고, enabled()는 "발견은 됐지만 지금 켜져 있는가"를 별도로
// 구분한다(빈으로 등록됐다는 것과 사용 가능하다는 것은 다른 질문이다).
public interface NotificationPlugin {

    String type();

    void send(NotificationMessage message);

    default boolean enabled() {
        return true;
    }
}
