package lab.minispring.scan;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

// 실제 Spring의 MetadataReader(ASM 기반)와 같은 접근 - ComponentScanner는 후보 여부를
// 판단하기 위해 클래스패스의 모든 .class 파일을 Class.forName으로 로딩(링크)했지만, 이
// 스캐너는 바이트코드만 읽어서 판단하고 후보로 판명된 것만 실제로 로딩한다. 그 결과 "후보가
// 아닌 클래스는 절대 JVM에 로딩되지 않는다"는 실제 Spring의 안전성 보장을 그대로 재현한다 -
// AsmComponentScannerTest가 존재하지 않는 슈퍼클래스를 참조하는(그래서 로딩하면 반드시
// NoClassDefFoundError가 나는) 비후보 클래스로 이 차이를 직접 증명한다.
//
// ComponentScanner와의 의도적 차이(docs/07-component-scan.md 10번 절 참고): 커스텀
// Predicate<Class<?>> include/exclude 필터는 지원하지 않는다 - Class<?> 없이 판단할 수
// 있는 조건은 @MiniComponent 애노테이션과 클래스 modifier(구체/추상/인터페이스)뿐이다.
// 필터까지 바이트코드만으로 지원하려면, 실제 Spring의 TypeFilter처럼 필터 자체가
// Class<?> 대신 메타데이터를 받는 형태로 다시 설계돼야 한다.
public final class AsmComponentScanner {

    private static final String MINI_COMPONENT_DESCRIPTOR = "Llab/minispring/scan/MiniComponent;";

    private final BeanNameGenerator beanNameGenerator;

    public AsmComponentScanner() {
        this(new DefaultBeanNameGenerator());
    }

    public AsmComponentScanner(BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = beanNameGenerator;
    }

    public Map<String, Class<?>> scan(String... basePackages) {
        Map<String, Class<?>> result = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String basePackage : basePackages) {
            for (ClassMetadata metadata : readMetadata(basePackage, classLoader)) {
                if (!metadata.eligible()) {
                    continue;
                }
                Class<?> loaded = load(metadata.className(), classLoader);
                String beanName = metadata.explicitName() != null && !metadata.explicitName().isBlank()
                        ? metadata.explicitName()
                        : beanNameGenerator.generateName(loaded);
                Class<?> existing = result.putIfAbsent(beanName, loaded);
                if (existing != null && existing != loaded) {
                    throw new DuplicateComponentNameException(beanName, existing, loaded);
                }
            }
        }
        return result;
    }

    private Class<?> load(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new ComponentScanException("Failed to load class '" + className + "'", e);
        }
    }

    private List<ClassMetadata> readMetadata(String basePackage, ClassLoader classLoader) {
        String path = basePackage.replace('.', '/');
        List<ClassMetadata> found = new ArrayList<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                File directory = new File(resources.nextElement().toURI());
                readDirectory(directory, basePackage, found);
            }
        } catch (IOException | URISyntaxException e) {
            throw new ComponentScanException("Failed to scan package '" + basePackage + "'", e);
        }
        return found;
    }

    private void readDirectory(File directory, String packageName, List<ClassMetadata> found) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                readDirectory(file, packageName + "." + file.getName(), found);
                continue;
            }
            if (!file.getName().endsWith(".class")) {
                continue;
            }
            found.add(readClassMetadata(file));
        }
    }

    private ClassMetadata readClassMetadata(File file) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            ClassReader reader = new ClassReader(in);
            MetadataVisitor visitor = new MetadataVisitor();
            // 메서드 바이트코드/디버그 정보/스택맵 프레임은 필요 없다 - 클래스 헤더(modifier,
            // 이름)와 애노테이션만 읽으면 후보 여부를 판단하기에 충분하다.
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor.toMetadata();
        } catch (IOException e) {
            throw new ComponentScanException("Failed to read class file '" + file + "'", e);
        }
    }

    private record ClassMetadata(String className, boolean eligible, String explicitName) {
    }

    private static final class MetadataVisitor extends ClassVisitor {

        private String className;
        private boolean concreteClass;
        private boolean hasComponentAnnotation;
        private String explicitName;

        MetadataVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.className = name.replace('/', '.');
            boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            boolean isAbstract = (access & Opcodes.ACC_ABSTRACT) != 0;
            this.concreteClass = !isInterface && !isAbstract;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (!MINI_COMPONENT_DESCRIPTOR.equals(descriptor)) {
                return null;
            }
            hasComponentAnnotation = true;
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String name, Object value) {
                    if ("value".equals(name) && value instanceof String stringValue) {
                        explicitName = stringValue;
                    }
                }
            };
        }

        ClassMetadata toMetadata() {
            return new ClassMetadata(className, concreteClass && hasComponentAnnotation, explicitName);
        }
    }
}
