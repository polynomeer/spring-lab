dependencies {
    implementation(libs.spring.context)
    // @PostConstruct/@PreDestroy need this on the classpath, or CommonAnnotationBeanPostProcessor
    // never registers at all - see docs/02-bean-definition/bean-definition-registration.md section 8.
    implementation(libs.jakarta.annotation.api)
}
