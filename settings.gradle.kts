rootProject.name = "spring-internals-lab"

include(
    "experiments:ioc-container-lab",
    "experiments:bean-definition-inspector",
    "experiments:context-refresh-visualizer",
    "experiments:bean-lifecycle-recorder",
    "mini-spring:mini-container",
    "spring-extensions:configuration-property-rewriter",
    "spring-extensions:method-timing-post-processor",
    "tools:jdi-tracer",
)
