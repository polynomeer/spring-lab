import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

allprojects {
    repositories {
        mavenCentral()
    }
}

// 버전 카탈로그의 타입-세이프 accessor(libs.xxx)는 allprojects/subprojects 같은 교차
// 프로젝트 설정 블록 안에서는 지원되지 않는다(Gradle 자체의 제약) - 그래서 여기서는
// VersionCatalogsExtension으로 이름 기반 동적 조회를 쓴다. 각 서브모듈 자신의
// build.gradle.kts에서는 평범하게 libs.xxx를 그대로 쓸 수 있다.
val rootLibs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

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
        add("testImplementation", platform(rootLibs.findLibrary("junit-bom").get()))
        add("testImplementation", rootLibs.findLibrary("junit-jupiter").get())
        add("testRuntimeOnly", rootLibs.findLibrary("junit-jupiter-engine").get())
        add("testRuntimeOnly", rootLibs.findLibrary("junit-platform-launcher").get())
        add("testImplementation", rootLibs.findLibrary("assertj-core").get())
    }
}
