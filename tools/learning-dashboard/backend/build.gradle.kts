dependencies {
    // TraceEvent가 정의된 곳이자, TracerServer를 자식 프로세스로 실행하는 데 필요한 jar -
    // 대시보드 백엔드는 jdi-tracer를 라이브러리로만 쓴다(그 CLI/서버 진입점을 직접
    // 실행하지 않고, ProcessBuilder로 별도 JVM에 띄운다).
    implementation(project(":tools:jdi-tracer"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)

    // @SpringBootTest + STOMP 클라이언트(WebSocketStompClient는 spring-websocket 본체에
    // 있으므로 별도 의존성 없이 이미 사용 가능하다)로 WebSocket 배선을 실제로 검증한다.
    testImplementation(libs.spring.boot.test)
}
