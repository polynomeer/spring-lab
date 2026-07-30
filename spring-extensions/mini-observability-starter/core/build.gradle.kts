dependencies {
    implementation("org.springframework:spring-webmvc:6.2.19")
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    // @ConfigurationProperties 애노테이션 자체가 spring-boot(core) 모듈에 있다 - 프로퍼티
    // 바인딩 로직(Binder)까지는 여기서 안 쓰고, 타입/애노테이션만 필요하다.
    implementation("org.springframework.boot:spring-boot:3.5.0")
    testImplementation("org.springframework:spring-test:6.2.19")
}
