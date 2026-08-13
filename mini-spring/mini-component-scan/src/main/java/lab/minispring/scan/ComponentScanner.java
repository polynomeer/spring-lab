package lab.minispring.scan;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class ComponentScanner {

    private final BeanNameGenerator beanNameGenerator;
    private final List<Predicate<Class<?>>> includeFilters = new ArrayList<>();
    private final List<Predicate<Class<?>>> excludeFilters = new ArrayList<>();

    public ComponentScanner() {
        this(new DefaultBeanNameGenerator());
    }

    public ComponentScanner(BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = beanNameGenerator;
    }

    public void addIncludeFilter(Predicate<Class<?>> filter) {
        includeFilters.add(filter);
    }

    public void addExcludeFilter(Predicate<Class<?>> filter) {
        excludeFilters.add(filter);
    }

    public Map<String, Class<?>> scan(String... basePackages) {
        Map<String, Class<?>> result = new LinkedHashMap<>();
        for (String basePackage : basePackages) {
            for (Class<?> candidate : findClasses(basePackage)) {
                if (!isEligible(candidate)) {
                    continue;
                }
                String beanName = resolveBeanName(candidate);
                Class<?> existing = result.putIfAbsent(beanName, candidate);
                if (existing != null && existing != candidate) {
                    throw new DuplicateComponentNameException(beanName, existing, candidate);
                }
            }
        }
        return result;
    }

    private boolean isEligible(Class<?> type) {
        // 실제 Spring의 ClassPathScanningCandidateComponentProvider#isCandidateComponent도
        // "구체 클래스"만 후보로 인정한다(인터페이스·추상 클래스 제외) - 소스로 확인한 규칙을
        // 그대로 따른다.
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return false;
        }

        boolean matches = type.isAnnotationPresent(MiniComponent.class)
                || includeFilters.stream().anyMatch(filter -> filter.test(type));
        if (!matches) {
            return false;
        }

        return excludeFilters.stream().noneMatch(filter -> filter.test(type));
    }

    private String resolveBeanName(Class<?> type) {
        MiniComponent annotation = type.getAnnotation(MiniComponent.class);
        if (annotation != null && !annotation.value().isBlank()) {
            return annotation.value();
        }
        return beanNameGenerator.generateName(type);
    }

    private Set<Class<?>> findClasses(String basePackage) {
        String path = basePackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<Class<?>> classes = new LinkedHashSet<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                File directory = new File(resources.nextElement().toURI());
                scanDirectory(directory, basePackage, classLoader, classes);
            }
        } catch (IOException | URISyntaxException e) {
            throw new ComponentScanException("Failed to scan package '" + basePackage + "'", e);
        }

        return classes;
    }

    private void scanDirectory(File directory, String packageName, ClassLoader classLoader, Set<Class<?>> classes) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classLoader, classes);
                continue;
            }
            if (!file.getName().endsWith(".class")) {
                continue;
            }

            String simpleName = file.getName().substring(0, file.getName().length() - ".class".length());
            String className = packageName + "." + simpleName;
            try {
                // initialize=false: 클래스를 찾아서 링크는 하지만 static 초기화 블록은 실행하지
                // 않는다 - 그래도 클래스 로딩 자체는 피할 수 없다. Spring이 ASM 기반
                // MetadataReader로 클래스를 아예 로딩하지 않고 바이트코드만 읽는 것과 대비된다
                // (AsmComponentScanner 참고).
                classes.add(Class.forName(className, false, classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                // LinkageError(그 하위 타입인 NoClassDefFoundError 포함)는 Exception이 아니라
                // Error다 - 원래 ClassNotFoundException만 잡던 시절에는 이 경로로 새어 나오는
                // 예외가 catch되지 않은 채 scan() 밖으로 그대로 전파됐다(직접 겪은 버그,
                // AsmComponentScannerTest#unlikeTheReflectionBasedScannerTheAsmScannerNeverLoadsA
                // ClassWithABrokenSuperclass가 이 상태를 재현해서 발견했다). 슈퍼클래스가 없거나
                // 링크에 실패하는 후보가 아닌 클래스 하나 때문에 스캔 전체가 예측 불가능한
                // Error로 죽는 대신, 다른 IOException 경로와 같은 ComponentScanException으로
                // 일관되게 감싼다.
                throw new ComponentScanException("Failed to load class '" + className + "'", e);
            }
        }
    }
}
