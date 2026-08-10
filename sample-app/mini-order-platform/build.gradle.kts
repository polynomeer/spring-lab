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
}
