plugins {
    application
}

application {
    mainClass.set("lab.tools.jdi.Tracer")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.jdi")
}

dependencies {
    // TracerServer(대시보드용 확장, 0단계)의 NDJSON 프로토콜 직렬화 - 스택 프레임/지역 변수
    // 값에 임의의 특수문자가 섞여 있어 손으로 짠 JSON escaping은 위험하다고 판단했다.
    implementation(libs.jackson.databind)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.jdi"))
}

tasks.withType<JavaExec> {
    jvmArgs("--add-modules", "jdk.jdi")
}

// 기존 콘솔 CLI(:run, Tracer)는 그대로 두고, NDJSON 서버 모드(TracerServer)는 별도 태스크로
// 실행할 수 있게 한다 - standardInput을 연결해야 셸에서 파이프로 명령을 흘려보낼 수 있다.
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run TracerServer (NDJSON stdin/stdout protocol) instead of the console Tracer."
    mainClass.set("lab.tools.jdi.TracerServer")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}
