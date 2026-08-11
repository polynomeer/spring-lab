dependencies {
    implementation(libs.spring.context)
    // @PostConstruct/@PreDestroy need this on the classpath, or CommonAnnotationBeanPostProcessor
    // never registers at all - see docs/02-bean-definition/bean-definition-registration.md section 8.
    implementation(libs.jakarta.annotation.api)
    // @Aspect/@Around annotation parsing for proxy-based Spring AOP (no full AspectJ weaving) -
    // same choice as experiments/circular-dependency-lab.
    implementation(libs.aspectjweaver)
    // Order/PaymentHistory/Outbox persistence - same JdbcTemplate + embedded H2 setup as
    // sample-app/transactional-outbox-order.
    implementation(libs.spring.jdbc)
    implementation(libs.h2)
    // Phase 4 (MVC).
    implementation(libs.spring.webmvc)
    implementation(libs.jakarta.servlet.api)
    implementation(libs.jackson.databind)
    // 공통 응답 처리를 처음부터 다시 만들지 않고 project 26(spring-extensions/api-response-handler)의
    // ApiResponse<T>를 그대로 재사용한다 - 캡스톤이 지향하는 "이미 배운 걸 조립한다"는 취지.
    implementation(project(":spring-extensions:api-response-handler"))
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
    testImplementation(libs.json.path)
    // Phase 6 (Boot). autoconfigure만 있으면 조건부 빈 등록은 충분하지만, 실제
    // @EnableAutoConfiguration 진입점(OrderPlatformApplication)을 내장 톰캣으로 띄우려면
    // spring-boot(starter-web)까지 필요하다 - :starter가 :autoconfigure와 spring-boot-starter-web을
    // api로 함께 끌고 온다(project 32와 같은 구성).
    implementation(libs.spring.boot.autoconfigure)
    implementation(project(":spring-extensions:mini-observability-starter:starter"))
    testImplementation(libs.spring.boot.test)
}
