dependencies {
    implementation(libs.spring.webmvc)
    implementation(libs.jakarta.servlet.api)
    // @ConfigurationProperties 애노테이션 자체가 spring-boot(core) 모듈에 있다 - 프로퍼티
    // 바인딩 로직(Binder)까지는 여기서 안 쓰고, 타입/애노테이션만 필요하다.
    implementation(libs.spring.boot)
    // 실제 Micrometer의 Observation API - ObservationRegistry/Observation을 그대로 쓴다.
    implementation(libs.micrometer.observation)
    testImplementation(libs.spring.test)
}
