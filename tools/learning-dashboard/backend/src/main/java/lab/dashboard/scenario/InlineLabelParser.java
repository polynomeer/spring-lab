package lab.dashboard.scenario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * docs/plan/04-dynamic-scenario-design.md 7번 절 - 손으로 짠 {@link lab.dashboard.interpret.ScenarioInterpreter}
 * 없이도, 사용자가 소스 코드 안에 남긴 {@code // @dashboard-label: "..."} 주석만으로 최소한의
 * semantic 라벨을 만든다. 라벨을 별도 JSON 필드가 아니라 코드 자체에 두는 것 - "정보를
 * 어디에 저장하느냐가 그 정보를 다루는 방식을 결정한다"는 50번 문서(placeholder-resolution)에서
 * 확인한 원칙과 같은 결이다.
 *
 * <p>완전한 자바 파서가 아니다 - 이 도구가 다루는 작고 단순한 즉석 Lab 클래스를 대상으로 한
 * 실용적인 휴리스틱이다. 라벨 주석 바로 아래(빈 줄/다른 한 줄 주석/애너테이션은 건너뛰고)
 * 나오는 첫 메서드 선언을 찾아 그 메서드의 단순 이름과 짝짓는다. 생성자는 못 찾는다(반환
 * 타입이 없어서 "타입 + 이름" 패턴에 안 맞는다) - 이 도구의 Lab 클래스는 보통
 * {@code public static void main}류의 평범한 메서드 몇 개뿐이라 실무적으로 충분하다.
 */
public final class InlineLabelParser {

    private static final Pattern LABEL_COMMENT =
            Pattern.compile("^\\s*//\\s*@dashboard-label:\\s*\"([^\"]*)\"\\s*$");

    // 흔한 자바 메서드 선언 한 줄짜리 형태를 넉넉히 잡는다 - 제네릭 반환 타입, 배열,
    // throws 절까지는 봐주지만 여러 줄에 걸친 시그니처는 못 잡는다.
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "^\\s*(?:(?:public|private|protected|static|final|synchronized|native|abstract|default)\\s+)+"
                    + "[\\w<>,\\[\\]. ]+?\\s+(\\w+)\\s*\\([^)]*\\)");

    private InlineLabelParser() {
    }

    public static Map<String, String> parseLabels(String sourceCode) {
        Map<String, String> labels = new LinkedHashMap<>();
        List<String> lines = sourceCode.lines().toList();

        for (int i = 0; i < lines.size(); i++) {
            Matcher labelMatcher = LABEL_COMMENT.matcher(lines.get(i));
            if (!labelMatcher.matches()) {
                continue;
            }
            String label = labelMatcher.group(1);

            for (int j = i + 1; j < lines.size(); j++) {
                String candidate = lines.get(j);
                Matcher methodMatcher = METHOD_DECLARATION.matcher(candidate);
                if (methodMatcher.find()) {
                    labels.put(methodMatcher.group(1), label);
                    break;
                }
                if (!isSkippable(candidate)) {
                    // 라벨과 무관한 코드(필드 선언 등)가 끼어들면 이 라벨은 포기하고
                    // 다음 라벨 탐색으로 넘어간다 - 엉뚱한 메서드에 잘못 붙이지 않는다.
                    break;
                }
            }
        }
        return labels;
    }

    private static boolean isSkippable(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.matches("@\\w+(\\([^)]*\\))?");
    }
}
