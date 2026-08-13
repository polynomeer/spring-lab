package lab.minispring.scan;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 7주차 문서(component-scan.md) 10번 절이 남겨 뒀던 질문 - "실제로 구현해 보면
// Class.forName 기반 스캐너와 성능·안전성 차이를 직접 측정해 볼 만하다" - 를 검증한다.
// 두 스캐너를 실제 디렉터리(임시 디렉터리에 손으로 만든 .class 파일들)에 대해 나란히 돌려서
// 비교한다.
class AsmComponentScannerTest {

    private static final String PACKAGE = "asmscan.synthetic";
    private static final String PACKAGE_PATH = PACKAGE.replace('.', '/');

    private ClassLoader originalContextClassLoader;

    @BeforeEach
    void captureOriginalClassLoader() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void restoreOriginalClassLoader() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void unlikeTheReflectionBasedScannerTheAsmScannerNeverLoadsAClassWithABrokenSuperclass(@TempDir Path tempDir)
            throws IOException {
        writeClass(tempDir, PACKAGE + ".GoodComponent",
                SyntheticClasses.validComponent(PACKAGE_PATH + "/GoodComponent", null));
        writeClass(tempDir, PACKAGE + ".BrokenNonComponent",
                SyntheticClasses.brokenSuperclass(PACKAGE_PATH + "/BrokenNonComponent"));

        RecordingClassLoader reflectionLoader = useRecordingClassLoader(tempDir);
        ComponentScanner reflectionScanner = new ComponentScanner();
        // 후보가 아닌 클래스 하나가 링크에 실패해도 스캔 전체가 죽는다 - 심지어 그 클래스
        // 이름조차 몰랐어도(우리 도메인의 @MiniComponent가 없는, 그냥 클래스패스에 섞여 있던
        // 다른 라이브러리의 클래스일 수도 있다) 실패한다. (직접 겪은 버그: 이 테스트를 짜기
        // 전까지 scanDirectory()의 catch 블록이 ClassNotFoundException만 잡고
        // LinkageError(NoClassDefFoundError의 상위 타입)는 잡지 않아서, ComponentScanException이
        // 아니라 catch되지 않은 java.lang.Error가 그대로 새어 나왔다 - ComponentScanner.java에서
        // 고쳤다.)
        assertThatThrownBy(() -> reflectionScanner.scan(PACKAGE)).isInstanceOf(ComponentScanException.class)
                .hasCauseInstanceOf(NoClassDefFoundError.class);
        assertThat(reflectionLoader.loadedClassNames()).contains(PACKAGE + ".BrokenNonComponent");

        RecordingClassLoader asmLoader = useRecordingClassLoader(tempDir);
        AsmComponentScanner asmScanner = new AsmComponentScanner();

        Map<String, Class<?>> found = asmScanner.scan(PACKAGE);

        assertThat(found).containsOnlyKeys("goodComponent");
        // 바이트코드만 읽고도 "@MiniComponent가 없다"는 걸 알 수 있으므로, 링크가 실패할
        // 클래스인데도 애초에 로딩을 시도조차 하지 않는다.
        assertThat(asmLoader.loadedClassNames()).doesNotContain(PACKAGE + ".BrokenNonComponent");
    }

    @Test
    void bothScannersAgreeOnTheEligibleSetButAsmLoadsOnlyTheCandidates(@TempDir Path tempDir) throws IOException {
        int totalClasses = 300;
        int componentCount = 20;
        for (int i = 0; i < totalClasses; i++) {
            String simpleName = "Generated" + i;
            byte[] bytes = i < componentCount
                    ? SyntheticClasses.validComponent(PACKAGE_PATH + "/" + simpleName, "component" + i)
                    : SyntheticClasses.plainNonComponent(PACKAGE_PATH + "/" + simpleName);
            writeClass(tempDir, PACKAGE + "." + simpleName, bytes);
        }

        RecordingClassLoader reflectionLoader = useRecordingClassLoader(tempDir);
        long reflectionStart = System.nanoTime();
        Map<String, Class<?>> reflectionResult = new ComponentScanner().scan(PACKAGE);
        long reflectionElapsedMillis = (System.nanoTime() - reflectionStart) / 1_000_000;

        RecordingClassLoader asmLoader = useRecordingClassLoader(tempDir);
        long asmStart = System.nanoTime();
        Map<String, Class<?>> asmResult = new AsmComponentScanner().scan(PACKAGE);
        long asmElapsedMillis = (System.nanoTime() - asmStart) / 1_000_000;

        assertThat(reflectionResult).hasSize(componentCount);
        assertThat(asmResult.keySet()).isEqualTo(reflectionResult.keySet());

        long reflectionLoadedSyntheticClasses = countLoadedSyntheticClasses(reflectionLoader);
        long asmLoadedSyntheticClasses = countLoadedSyntheticClasses(asmLoader);

        // 리플렉션 스캐너는 후보 판정을 위해 300개 전부 로딩해야 하지만, ASM 스캐너는
        // 바이트코드만 읽고 실제로 로딩하는 건 후보로 판명된 20개뿐이다 - 결과값(둘 다 같은
        // 20개)이 아니라 "무엇을 로딩했는가" 자체가 다르다는 걸 증명한다. (asmscan.synthetic.*
        // 이외의 이름으로 필터링하는 이유: 두 스캐너 모두 java.lang.Object와
        // lab.minispring.scan.MiniComponent를 딱 한 번씩 추가로 로딩한다 - 첫 슈퍼클래스/
        // 애노테이션 타입 해석 결과가 클래스로더 안에 캐싱되어, 300개 각각에 대해 반복되지
        // 않기 때문이다. 이 자체도 흥미로운 관찰이라 문서에 남긴다.)
        assertThat(reflectionLoadedSyntheticClasses).isEqualTo(totalClasses);
        assertThat(asmLoadedSyntheticClasses).isEqualTo(componentCount);

        System.out.printf(
                "[AsmComponentScannerTest] %d개 클래스(후보 %d개) - reflection: %dms(로딩 %d개, 부수 로딩 %d개 포함), "
                        + "asm: %dms(로딩 %d개, 부수 로딩 %d개 포함)%n",
                totalClasses, componentCount, reflectionElapsedMillis, reflectionLoader.loadedClassNames().size(),
                reflectionLoader.loadedClassNames().size() - reflectionLoadedSyntheticClasses, asmElapsedMillis,
                asmLoader.loadedClassNames().size(), asmLoader.loadedClassNames().size() - asmLoadedSyntheticClasses);
    }

    private long countLoadedSyntheticClasses(RecordingClassLoader loader) {
        return loader.loadedClassNames().stream().filter(name -> name.startsWith(PACKAGE + ".")).count();
    }

    private RecordingClassLoader useRecordingClassLoader(Path tempDir) throws IOException {
        URL root = tempDir.toUri().toURL();
        RecordingClassLoader recordingClassLoader = new RecordingClassLoader(new URL[] {root},
                originalContextClassLoader);
        Thread.currentThread().setContextClassLoader(recordingClassLoader);
        return recordingClassLoader;
    }

    private void writeClass(Path tempDir, String fullyQualifiedName, byte[] bytes) throws IOException {
        Path target = tempDir.resolve(fullyQualifiedName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
