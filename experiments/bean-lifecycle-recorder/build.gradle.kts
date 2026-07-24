dependencies {
    implementation("org.springframework:spring-context:6.2.19")
    // @PostConstruct/@PreDestroy need this on the classpath, or CommonAnnotationBeanPostProcessor
    // never registers at all - see docs/02-bean-definition/bean-definition-registration.md section 8.
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
}
