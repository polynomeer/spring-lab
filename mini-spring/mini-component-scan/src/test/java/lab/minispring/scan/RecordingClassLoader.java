package lab.minispring.scan;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

// 어떤 클래스 이름으로 loadClass()가 실제로 호출됐는지 기록하는 테스트 지원 클래스 -
// ComponentScanner(전부 로딩)와 AsmComponentScanner(후보만 로딩)의 "실제로 로딩을
// 시도한 클래스 집합" 차이를 결과값이 아니라 로딩 시도 자체로 직접 증명하기 위해서다.
final class RecordingClassLoader extends URLClassLoader {

    private final List<String> loadedClassNames = new ArrayList<>();

    RecordingClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        loadedClassNames.add(name);
        return super.loadClass(name, resolve);
    }

    List<String> loadedClassNames() {
        return List.copyOf(loadedClassNames);
    }
}
