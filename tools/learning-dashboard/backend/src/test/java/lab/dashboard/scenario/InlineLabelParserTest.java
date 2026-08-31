package lab.dashboard.scenario;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InlineLabelParserTest {

    @Test
    void associatesALabelCommentWithTheMethodDeclaredImmediatelyBelowIt() {
        String source = """
                package lab.dynamic;
                public class MyLab {
                    // @dashboard-label: "빈 생성 시작"
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """;

        Map<String, String> labels = InlineLabelParser.parseLabels(source);

        assertThat(labels).containsExactly(Map.entry("main", "빈 생성 시작"));
    }

    @Test
    void skipsBlankLinesAndOtherCommentsAndAnnotationsBetweenTheLabelAndTheMethod() {
        String source = """
                package lab.dynamic;
                public class MyLab {
                    // @dashboard-label: "초기화"

                    // 그냥 평범한 설명 주석
                    @Deprecated
                    public void init() {
                    }
                }
                """;

        Map<String, String> labels = InlineLabelParser.parseLabels(source);

        assertThat(labels).containsExactly(Map.entry("init", "초기화"));
    }

    @Test
    void givesUpOnALabelIfNonSkippableCodeAppearsBeforeAMethodDeclaration() {
        String source = """
                package lab.dynamic;
                public class MyLab {
                    // @dashboard-label: "무시됨"
                    private int counter = 0;

                    public void tick() {
                    }
                }
                """;

        Map<String, String> labels = InlineLabelParser.parseLabels(source);

        assertThat(labels).isEmpty();
    }

    @Test
    void parsesMultipleLabelsInTheSameSource() {
        String source = """
                package lab.dynamic;
                public class MyLab {
                    // @dashboard-label: "시작"
                    public static void main(String[] args) {
                    }

                    // @dashboard-label: "정리"
                    private static void cleanup() {
                    }
                }
                """;

        Map<String, String> labels = InlineLabelParser.parseLabels(source);

        assertThat(labels).containsExactly(
                Map.entry("main", "시작"),
                Map.entry("cleanup", "정리"));
    }

    @Test
    void returnsAnEmptyMapWhenThereAreNoLabelComments() {
        String source = """
                package lab.dynamic;
                public class MyLab {
                    public static void main(String[] args) {
                    }
                }
                """;

        assertThat(InlineLabelParser.parseLabels(source)).isEmpty();
    }
}
