package lab.experiments.customscope;

// "현재 스레드가 어느 테넌트에 속하는가"를 담는, 흔한 멀티테넌시 패턴의 축약형 - 실제로는
// 요청 필터 같은 곳에서 설정되겠지만, 이 실험에서는 테스트가 직접 설정/해제한다.
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
