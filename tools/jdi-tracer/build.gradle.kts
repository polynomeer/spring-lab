plugins {
    application
}

application {
    mainClass.set("lab.tools.jdi.Tracer")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.jdi")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.jdi"))
}

tasks.withType<JavaExec> {
    jvmArgs("--add-modules", "jdk.jdi")
}
