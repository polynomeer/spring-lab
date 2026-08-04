dependencies {
    // TraceEvent가 정의된 곳 - 대시보드 백엔드가 실제로 Spring Boot/WebSocket을 갖추는 건
    // 설계 문서(docs/plan/03-learning-dashboard-design.md)의 2단계다. 지금(1단계)은 이
    // 모듈에 semantic 해석기(순수 데이터 변환)만 두고, jdi-tracer를 라이브러리로 참조한다.
    implementation(project(":tools:jdi-tracer"))
}
