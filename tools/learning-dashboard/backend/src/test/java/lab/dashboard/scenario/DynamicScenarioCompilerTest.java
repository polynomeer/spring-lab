package lab.dashboard.scenario;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicScenarioCompilerTest {

    private final DynamicScenarioCompiler compiler = new DynamicScenarioCompiler();

    @Test
    void compilesValidSourceAndLeavesAUsableClassFileBehind() throws Exception {
        String source = """
                package lab.dynamic;
                public class HelloLab {
                    public static void main(String[] args) {
                        System.out.println("hello from dynamic scenario");
                    }
                }
                """;

        DynamicScenarioCompiler.CompileResult result =
                compiler.compile("lab.dynamic.HelloLab", source, System.getProperty("java.class.path"));

        assertThat(result.success()).isTrue();
        assertThat(result.outputDir()).isNotNull();
        assertThat(Files.exists(result.outputDir().resolve("lab/dynamic/HelloLab.class"))).isTrue();
    }

    @Test
    void reportsSyntaxErrorsAsDiagnosticsWithoutLeavingAnOutputDir() {
        String brokenSource = """
                package lab.dynamic;
                public class BrokenLab {
                    public static void main(String[] args) {
                        System.out.println("missing semicolon")
                    }
                }
                """;

        DynamicScenarioCompiler.CompileResult result =
                compiler.compile("lab.dynamic.BrokenLab", brokenSource, System.getProperty("java.class.path"));

        assertThat(result.success()).isFalse();
        assertThat(result.outputDir()).isNull();
        assertThat(result.diagnostics()).isNotEmpty();
    }

    @Test
    void reportsUnresolvableTypeReferencesAsDiagnostics() {
        String source = """
                package lab.dynamic;
                public class MissingTypeLab {
                    public static void main(String[] args) {
                        lab.this.package.does.not.Exist thing = null;
                    }
                }
                """;

        DynamicScenarioCompiler.CompileResult result =
                compiler.compile("lab.dynamic.MissingTypeLab", source, System.getProperty("java.class.path"));

        assertThat(result.success()).isFalse();
        assertThat(result.outputDir()).isNull();
        assertThat(result.diagnostics()).isNotEmpty();
    }

    @Test
    void discardDeletesTheCompiledOutputDirectory() {
        String source = """
                package lab.dynamic;
                public class ThrowawayLab {
                    public static void main(String[] args) { }
                }
                """;

        DynamicScenarioCompiler.CompileResult result =
                compiler.compile("lab.dynamic.ThrowawayLab", source, System.getProperty("java.class.path"));
        assertThat(result.success()).isTrue();

        compiler.discard(result);

        assertThat(Files.exists(result.outputDir())).isFalse();
    }
}
