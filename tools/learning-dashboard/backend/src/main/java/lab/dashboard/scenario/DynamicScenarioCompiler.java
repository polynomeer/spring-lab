package lab.dashboard.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.springframework.stereotype.Component;

/**
 * docs/plan/04-dynamic-scenario-design.md 4.2절 - 사용자가 브라우저에서 바로 작성한 Lab
 * 클래스 소스를 {@code javax.tools.JavaCompiler}로 임시 디렉터리에 컴파일한다. 실행마다
 * 새 임시 디렉터리를 쓰므로 이전 실행과 절대 섞이지 않는다(5번 절 안전장치).
 *
 * <p>javac는 in-memory 소스 파일이라도 "public 최상위 타입의 단순 이름과 파일 이름이
 * 일치해야 한다"는 제약을 그대로 적용한다 - 그래서 파일 이름을 {@code mainClass}에서
 * 뽑은 단순 이름으로 정확히 맞춰 준다.
 */
@Component
public class DynamicScenarioCompiler {

    public record CompileResult(boolean success, Path outputDir, List<String> diagnostics) {

        static CompileResult failure(List<String> diagnostics) {
            return new CompileResult(false, null, diagnostics);
        }
    }

    public boolean isAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    public CompileResult compile(String mainClass, String sourceCode, String classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return CompileResult.failure(List.of(
                    "이 JVM에는 컴파일러가 없습니다(JRE로 실행 중일 수 있음) - JDK로 다시 실행해야 합니다."));
        }

        Path outputDir;
        try {
            outputDir = Files.createTempDirectory("dynamic-scenario-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, Locale.KOREAN, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));

            String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);
            JavaFileObject sourceFile = new InMemorySource(simpleName, sourceCode);
            List<String> options = List.of("-classpath", classpath);

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, List.of(sourceFile));
            boolean success = task.call();

            List<String> messages = diagnostics.getDiagnostics().stream()
                    .map(DynamicScenarioCompiler::formatDiagnostic)
                    .toList();

            if (!success) {
                deleteRecursively(outputDir);
                return CompileResult.failure(messages);
            }
            return new CompileResult(true, outputDir, messages);
        } catch (IOException e) {
            deleteRecursively(outputDir);
            throw new UncheckedIOException(e);
        }
    }

    /** 저장 시점 검증처럼 산출물을 실행에 쓰지 않을 때 - 임시 디렉터리를 바로 치운다. */
    public void discard(CompileResult result) {
        if (result.outputDir() != null) {
            deleteRecursively(result.outputDir());
        }
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String location = diagnostic.getLineNumber() >= 0 ? "line " + diagnostic.getLineNumber() + ": " : "";
        return location + diagnostic.getMessage(Locale.KOREAN);
    }

    private static void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // 최선을 다해 정리할 뿐 - 실패해도 로컬 개발 도구 하나의 임시 파일이라
                    // 치명적이지 않다.
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static final class InMemorySource extends SimpleJavaFileObject {

        private final String code;

        InMemorySource(String simpleClassName, String code) {
            super(URI.create("string:///" + simpleClassName + ".java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
