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

    // jdi-tracer 세션마다(그리고 이제 learning-dashboard의 ClasspathResolver도) "이 모듈의
    // main 런타임 클래스패스"가 필요한데, 매번 throwaway init script로 즉석에서 만들어 왔다.
    // 대상 모듈이라면 어디서든 재사용할 수 있게 여기 한 번만 등록해 둔다.
    tasks.register("printRuntimeClasspath") {
        group = "help"
        description = "Prints this module's main runtime classpath, one entry-set per line."
        doLast {
            val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
            if (sourceSets != null) {
                println(sourceSets.getByName("main").runtimeClasspath.asPath)
            }
        }
    }
}
