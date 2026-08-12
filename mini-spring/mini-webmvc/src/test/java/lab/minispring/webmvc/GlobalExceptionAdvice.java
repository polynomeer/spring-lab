package lab.minispring.webmvc;

// 실제 @ControllerAdvice 빈에 대응하는 테스트 지원 클래스 - 아무 컨트롤러에도 속하지 않고,
// AnnotationExceptionResolver#registerControllerAdvice()로 명시적으로 등록된다.
class GlobalExceptionAdvice {

    @MiniExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex) {
        return "global: " + ex.getMessage();
    }

    @MiniExceptionHandler(NullPointerException.class)
    public String handleNullPointer(NullPointerException ex) {
        return "global-npe: " + ex.getMessage();
    }
}
