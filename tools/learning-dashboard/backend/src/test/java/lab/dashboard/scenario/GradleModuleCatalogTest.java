package lab.dashboard.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// settings.gradle.kts를 "이 저장소에 어떤 모듈이 있는가"의 유일한 출처로 삼는다(별도 목록을
// 따로 관리하지 않는다) - 그래서 이 파서가 실제 파일 형식(멀티라인 include(...), 콤마 포함)을
// 정확히 이해하는지, 그리고 예상 밖의 모양(블록 없음, 안 닫힌 괄호)에서 조용히 이상한 값을
// 돌려주지 않는지가 중요하다.
class GradleModuleCatalogTest {

    @TempDir
    Path repoRoot;

    private void writeSettings(String content) throws IOException {
        Files.writeString(repoRoot.resolve("settings.gradle.kts"), content);
    }

    @Test
    void extractsModulePathsFromAMultilineIncludeBlock() throws IOException {
        writeSettings("""
                rootProject.name = "spring-core-lab"

                include(
                    "experiments:ioc-container-lab",
                    "mini-spring:mini-container",
                    "tools:jdi-tracer",
                )
                """);

        assertThat(GradleModuleCatalog.listModulePaths(repoRoot)).containsExactly(
                "experiments:ioc-container-lab",
                "mini-spring:mini-container",
                "tools:jdi-tracer");
    }

    @Test
    void returnsAnEmptyListWhenThereIsNoIncludeBlock() throws IOException {
        writeSettings("rootProject.name = \"spring-core-lab\"\n");

        assertThat(GradleModuleCatalog.listModulePaths(repoRoot)).isEmpty();
    }

    @Test
    void ignoresQuotedStringsOutsideTheIncludeBlock() throws IOException {
        writeSettings("""
                rootProject.name = "spring-core-lab"

                include("experiments:ioc-container-lab")

                // 다음 릴리스에서 추가할 모듈 - 아직 include되지 않았다.
                val future = "experiments:not-yet-included"
                """);

        assertThat(GradleModuleCatalog.listModulePaths(repoRoot)).containsExactly("experiments:ioc-container-lab");
    }

    @Test
    void readsToEndOfFileWhenTheIncludeBlockIsMissingItsClosingParenthesis() throws IOException {
        writeSettings("""
                rootProject.name = "spring-core-lab"

                include(
                    "experiments:ioc-container-lab"
                """);

        assertThat(GradleModuleCatalog.listModulePaths(repoRoot)).containsExactly("experiments:ioc-container-lab");
    }

    @Test
    void wrapsAMissingSettingsFileInAnUncheckedIOException() {
        assertThatThrownBy(() -> GradleModuleCatalog.listModulePaths(repoRoot.resolve("no-such-dir")))
                .isInstanceOf(UncheckedIOException.class);
    }
}
