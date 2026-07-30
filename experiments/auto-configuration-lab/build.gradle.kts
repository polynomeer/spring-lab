dependencies {
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.0")
    testImplementation("org.springframework.boot:spring-boot-test:3.5.0")
    // main 소스셋에는 없다 - @ConditionalOnClass(name = "...")가 문자열 기반이라 컴파일
    // 의존성 없이도 동작한다는 것과, 테스트 클래스패스에만 Jackson이 있을 때 조건이 어떻게
    // 갈리는지를 확인하기 위해 일부러 testImplementation으로만 넣는다(19주차).
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
