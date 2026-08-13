package lab.minispring.scan;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

// AsmComponentScannerTest 전용 - javac를 거치지 않고 ASM ClassWriter로 직접 클래스 바이트를
// 만들어 낸다. 존재하지 않는 슈퍼클래스를 참조하는 클래스처럼, 컴파일이 애초에 안 되는
// "링크 불가능한 클래스"를 결정론적으로 재현하기 위해서다.
final class SyntheticClasses {

    private SyntheticClasses() {
    }

    static byte[] validComponent(String internalName, String explicitBeanName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        AnnotationVisitor av = cw.visitAnnotation("Llab/minispring/scan/MiniComponent;", true);
        if (explicitBeanName != null) {
            av.visit("value", explicitBeanName);
        }
        av.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static byte[] plainNonComponent(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // 존재하지 않는 슈퍼클래스를 참조한다 - JVMS 5.3에 따르면 클래스를 "로딩"만 해도(초기화는
    // 안 해도) 슈퍼클래스는 즉시 함께 로딩을 시도해야 하므로, 이 클래스를 Class.forName으로
    // 로딩하면 반드시 NoClassDefFoundError가 난다. 반면 ASM의 ClassReader는 상수 풀의 문자열을
    // 그대로 읽을 뿐이라, 이 클래스를 파싱하는 데는 아무 문제가 없다 - 그 차이가 이 테스트의
    // 핵심이다.
    static byte[] brokenSuperclass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "does/not/ExistXYZ", null);
        cw.visitEnd();
        return cw.toByteArray();
    }
}
