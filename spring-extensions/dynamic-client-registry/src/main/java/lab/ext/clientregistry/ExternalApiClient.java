package lab.ext.clientregistry;

// ExternalClientRegistrar가 설정 파일 한 항목마다 이 타입의 빈을 하나씩 동적으로 등록한다 -
// 클래스 자신은 등록 방식과 무관한 평범한 POJO다.
public class ExternalApiClient {

    private final String name;
    private final String baseUrl;
    private final int timeoutMillis;

    public ExternalApiClient(String name, String baseUrl, int timeoutMillis) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.timeoutMillis = timeoutMillis;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public String toString() {
        return "ExternalApiClient{name='%s', baseUrl='%s', timeoutMillis=%d}"
                .formatted(name, baseUrl, timeoutMillis);
    }
}
