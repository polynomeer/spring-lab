plugins {
    java
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        // 리플렉션으로 파라미터 이름을 읽어야 하는 실험(예: 9주차 이름 기반 후보 선택)이
        // 여러 모듈에 걸쳐 있어서 전역으로 켜 둔다. 기본값(-parameters 없음)이면
        // Parameter#getName()이 "arg0"류 이름만 돌려준다.
        options.compilerArgs.add("-parameters")
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.11.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("testImplementation", "org.assertj:assertj-core:3.26.3")
    }
}
