package lab.experiments.scopedproxy;

// 36번(custom-scope-lab)의 TenantContext와 같은 패턴 - "현재 스레드가 어느 테넌트에
// 속하는가"를 담는 흔한 멀티테넌시 축약형. 이번 실험은 그 스코프 자체가 아니라,
// 그 스코프의 빈을 싱글턴에 "안전하게" 주입하는 방법(스코프드 프록시)에 초점을 둔다.
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
