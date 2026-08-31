package lab.dashboard.scenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code settings.gradle.kts}의 {@code include(...)} 블록에서 모듈 경로 목록을 그대로
 * 뽑아 온다 - 새 시나리오를 등록할 때 "어느 모듈을 쓸지" 선택지를 사용자가 직접 타이핑하지
 * 않고 고를 수 있게 하기 위해서다. 별도 목록을 따로 관리하지 않는다 - settings.gradle.kts
 * 자체가 이미 "이 저장소에 어떤 모듈이 있는가"의 유일하고 정확한 출처이므로, 그걸 다시
 * 베껴 적으면 둘이 어긋날 뿐이다.
 */
final class GradleModuleCatalog {

    private static final Pattern MODULE_PATH = Pattern.compile("\"([a-zA-Z0-9:_-]+)\"");

    private GradleModuleCatalog() {
    }

    static List<String> listModulePaths(Path repoRoot) {
        String content;
        try {
            content = Files.readString(repoRoot.resolve("settings.gradle.kts"));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read settings.gradle.kts", e);
        }

        int includeStart = content.indexOf("include(");
        if (includeStart < 0) {
            return List.of();
        }
        int includeEnd = content.indexOf(')', includeStart);
        String includeBlock = content.substring(includeStart, includeEnd < 0 ? content.length() : includeEnd);

        List<String> modulePaths = new ArrayList<>();
        Matcher matcher = MODULE_PATH.matcher(includeBlock);
        while (matcher.find()) {
            modulePaths.add(matcher.group(1));
        }
        return modulePaths;
    }
}
