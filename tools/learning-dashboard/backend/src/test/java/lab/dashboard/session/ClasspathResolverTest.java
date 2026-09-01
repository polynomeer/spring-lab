package lab.dashboard.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 ./gradlew를 셸아웃으로 부르는 대신, 같은 자리(<repoRoot>/gradlew)에 동작을 마음대로
// 조절할 수 있는 가짜 스크립트를 심어서 검증한다 - 진짜 gradlew를 부르면 이 클래스 자체의
// 캐싱/에러 처리 로직이 아니라 실제 빌드 시간에 좌우되는 느린 테스트가 된다.
// 과거 workingDir 설정 누락으로 이 클래스가 잘못된 저장소 루트를 찾은 실제 버그가 있었다
// (build.gradle.kts의 JavaExec workingDir 수정 커밋 참고) - 캐싱과 실패 경로를 여기서 고정해 둔다.
class ClasspathResolverTest {

    @TempDir
    Path repoRoot;

    private void writeGradlew(String script) throws IOException {
        Path gradlew = repoRoot.resolve("gradlew");
        Files.writeString(gradlew, script);
        assertThat(gradlew.toFile().setExecutable(true)).isTrue();
    }

    @Test
    void resolvesTheLastNonBlankLineOfOutputAsTheClasspath() throws IOException {
        writeGradlew("""
                #!/bin/sh
                echo ""
                echo "some other noise"
                echo "/fake/repo/experiments/ioc-container-lab/build/classes"
                """);

        String classpath = new ClasspathResolver(repoRoot).resolve("experiments:ioc-container-lab");

        assertThat(classpath).isEqualTo("/fake/repo/experiments/ioc-container-lab/build/classes");
    }

    @Test
    void passesTheModulePathAndPrintRuntimeClasspathTaskAsArguments() throws IOException {
        writeGradlew("""
                #!/bin/sh
                echo "args-were:$@"
                """);

        String classpath = new ClasspathResolver(repoRoot).resolve("experiments:aop-lab");

        assertThat(classpath).isEqualTo("args-were:-q experiments:aop-lab:printRuntimeClasspath");
    }

    @Test
    void cachesTheResultAndOnlyInvokesGradlewOnceForRepeatedResolves() throws IOException {
        Path invocationLog = repoRoot.resolve("invocations.log");
        writeGradlew("""
                #!/bin/sh
                echo "call" >> "%s"
                echo "/fake/classpath"
                """.formatted(invocationLog));

        ClasspathResolver resolver = new ClasspathResolver(repoRoot);
        String first = resolver.resolve("experiments:ioc-container-lab");
        String second = resolver.resolve("experiments:ioc-container-lab");

        assertThat(first).isEqualTo(second);
        assertThat(Files.readAllLines(invocationLog)).hasSize(1);
    }

    @Test
    void resolvesDifferentModulePathsIndependently() throws IOException {
        writeGradlew("""
                #!/bin/sh
                echo "classpath-for:$2"
                """);

        ClasspathResolver resolver = new ClasspathResolver(repoRoot);

        assertThat(resolver.resolve("experiments:a")).isEqualTo("classpath-for:experiments:a:printRuntimeClasspath");
        assertThat(resolver.resolve("experiments:b")).isEqualTo("classpath-for:experiments:b:printRuntimeClasspath");
    }

    @Test
    void throwsWithTheExitCodeAndOutputWhenTheGradleTaskFails() throws IOException {
        writeGradlew("""
                #!/bin/sh
                echo "some build error"
                exit 1
                """);

        assertThatThrownBy(() -> new ClasspathResolver(repoRoot).resolve("experiments:broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("printRuntimeClasspath failed")
                .hasMessageContaining("exit 1")
                .hasMessageContaining("some build error");
    }

    @Test
    void wrapsAMissingGradlewScriptInAnIllegalStateException() {
        Path noSuchRepoRoot = repoRoot.resolve("no-such-repo-root");

        assertThatThrownBy(() -> new ClasspathResolver(noSuchRepoRoot).resolve("experiments:ioc-container-lab"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed to resolve classpath for experiments:ioc-container-lab");
    }
}
