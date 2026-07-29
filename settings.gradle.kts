rootProject.name = "spring-internals-lab"

include(
    "experiments:ioc-container-lab",
    "experiments:bean-definition-inspector",
    "experiments:context-refresh-visualizer",
    "experiments:bean-lifecycle-recorder",
    "experiments:configuration-proxy-lab",
    "experiments:dependency-resolution-matrix",
    "experiments:circular-dependency-lab",
    "experiments:proxy-playground",
    "experiments:transaction-propagation-playground",
    "experiments:dispatcher-servlet-trace",
    "mini-spring:mini-container",
    "mini-spring:mini-component-scan",
    "mini-spring:mini-java-config",
    "mini-spring:mini-aop",
    "mini-spring:mini-auto-proxy",
    "mini-spring:mini-transaction",
    "mini-spring:mini-webmvc",
    "spring-extensions:configuration-property-rewriter",
    "spring-extensions:method-timing-post-processor",
    "tools:jdi-tracer",
)
