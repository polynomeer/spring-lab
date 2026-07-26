package lab.minispring.scan;

import java.util.Map;

import org.junit.jupiter.api.Test;

import lab.minispring.scan.duplicates.DuplicateA;
import lab.minispring.scan.duplicates.DuplicateB;
import lab.minispring.scan.fixtures.AbstractComponentBase;
import lab.minispring.scan.fixtures.ComponentInterface;
import lab.minispring.scan.fixtures.Excludable;
import lab.minispring.scan.fixtures.Includable;
import lab.minispring.scan.fixtures.NamedComponent;
import lab.minispring.scan.fixtures.NotAnnotatedClass;
import lab.minispring.scan.fixtures.PlainPojo;
import lab.minispring.scan.fixtures.ScannedComponent;
import lab.minispring.scan.fixtures.nested.NestedComponent;
import lab.minispring.scan.other.OtherComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentScannerTest {

    private static final String FIXTURES_PACKAGE = "lab.minispring.scan.fixtures";

    @Test
    void findsAnnotatedConcreteClassesWithDefaultDecapitalizedName() {
        Map<String, Class<?>> found = new ComponentScanner().scan(FIXTURES_PACKAGE);

        assertThat(found).containsEntry("scannedComponent", ScannedComponent.class);
        assertThat(found).doesNotContainKey("notAnnotatedClass");
        assertThat(found.values()).doesNotContain(NotAnnotatedClass.class);
    }

    @Test
    void usesExplicitNameFromAnnotationValueInsteadOfDefault() {
        Map<String, Class<?>> found = new ComponentScanner().scan(FIXTURES_PACKAGE);

        assertThat(found).containsEntry("customName", NamedComponent.class);
        assertThat(found).doesNotContainKey("namedComponent");
    }

    @Test
    void excludesInterfacesAndAbstractClassesEvenIfAnnotated() {
        Map<String, Class<?>> found = new ComponentScanner().scan(FIXTURES_PACKAGE);

        assertThat(found.values()).doesNotContain(ComponentInterface.class, AbstractComponentBase.class);
    }

    @Test
    void recursesIntoSubpackages() {
        Map<String, Class<?>> found = new ComponentScanner().scan(FIXTURES_PACKAGE);

        assertThat(found.values()).contains(NestedComponent.class);
    }

    @Test
    void scansMultipleDisjointPackagesInOneCall() {
        Map<String, Class<?>> found = new ComponentScanner()
                .scan(FIXTURES_PACKAGE, "lab.minispring.scan.other");

        assertThat(found.values()).contains(ScannedComponent.class, OtherComponent.class);
    }

    @Test
    void includeFilterAddsNonAnnotatedCandidates() {
        ComponentScanner scanner = new ComponentScanner();
        scanner.addIncludeFilter(type -> Includable.class.isAssignableFrom(type) && type != Includable.class);

        Map<String, Class<?>> found = scanner.scan(FIXTURES_PACKAGE);

        assertThat(found.values()).contains(PlainPojo.class);
    }

    @Test
    void excludeFilterRemovesOtherwiseMatchingCandidates() {
        ComponentScanner scanner = new ComponentScanner();
        scanner.addExcludeFilter(type -> Excludable.class.isAssignableFrom(type) && type != Excludable.class);

        Map<String, Class<?>> found = scanner.scan(FIXTURES_PACKAGE);

        assertThat(found.values()).doesNotContain(ScannedComponent.class);
        // 다른 후보는 그대로 남아 있어야 한다 - exclude filter가 전부를 걸러내면 안 된다.
        assertThat(found).containsKey("customName");
    }

    @Test
    void duplicateResolvedNameAcrossClassesThrows() {
        ComponentScanner scanner = new ComponentScanner();

        assertThatThrownBy(() -> scanner.scan("lab.minispring.scan.duplicates"))
                .isInstanceOf(DuplicateComponentNameException.class)
                .hasMessageContaining(DuplicateA.class.getName())
                .hasMessageContaining(DuplicateB.class.getName());
    }

    @Test
    void customBeanNameGeneratorIsUsedInsteadOfDefault() {
        ComponentScanner scanner = new ComponentScanner(type -> "prefixed-" + type.getSimpleName());

        Map<String, Class<?>> found = scanner.scan(FIXTURES_PACKAGE);

        assertThat(found).containsEntry("prefixed-ScannedComponent", ScannedComponent.class);
        // 애노테이션에 명시적 이름이 있으면 커스텀 제너레이터보다 우선한다.
        assertThat(found).containsEntry("customName", NamedComponent.class);
    }
}
