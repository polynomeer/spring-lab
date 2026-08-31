// tools/jdi-tracer와 같은 패턴 - application 플러그인으로 ./gradlew :...:backend:run을
// 쓸 수 있게 한다(이 저장소의 experiments/mini-spring/spring-extensions/sample-app 모듈들과
// 달리, 이 모듈은 IDE 없이도 스크립트로 띄울 수 있어야 한다).
plugins {
    application
}

application {
    mainClass.set("lab.dashboard.DashboardApplication")
}

// JavaExec(run 태스크의 실체)은 기본적으로 작업 디렉터리를 이 서브프로젝트 디렉터리
// (tools/learning-dashboard/backend)로 잡는다 - 하지만 ClasspathResolver는
// System.getProperty("user.dir")을 저장소 루트로 가정하고 거기서 ./gradlew를 찾는다
// (다른 experiments 모듈의 클래스패스를 조회하기 위해서다). 직접 겪은 버그: run 태스크로
// 띄우면 이 가정이 깨져서 "failed to resolve classpath"로 실패했다 - 작업 디렉터리를
// 저장소 루트로 고정해서 고쳤다.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

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
