package lab.experiments.mvc;

import java.net.ServerSocket;
import java.nio.file.Files;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 대시보드 6.4절(DispatcherServlet 요청 흐름) 시나리오의 라이브 진입점 - 다른 세 Lab과 달리
 * 자기 완결적인 main()이 아니라, 임베디드 Tomcat을 띄우고 실제 HTTP 요청을 기다린다.
 *
 * <p>{@code DISPATCHER_TRACE_READY port=<n>} 한 줄을 표준 출력으로 찍는데, 이건 TracerServer가
 * 그대로 stdout 이벤트로 중계하고, 대시보드 백엔드({@code ScenarioSession})가 그 줄을 파싱해서
 * "이제 이 포트로 요청을 보내도 된다"를 안다 - 이 프로세스와 백엔드 사이에 이것 말고 다른
 * 프로토콜은 없다.
 */
public final class DispatcherServletTraceLab {

    private DispatcherServletTraceLab() {
    }

    public static void main(String[] args) throws Exception {
        int port = findFreePort();

        AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
        appContext.register(MvcTraceConfig.class);

        String baseDir = Files.createTempDirectory("dispatcher-servlet-trace").toString();
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(baseDir);
        tomcat.getConnector(); // 커넥터를 지금 강제로 만들어 둔다 - 그러지 않으면 start()가 바인딩을 건너뛴다.

        Context context = tomcat.addContext("", baseDir);
        appContext.setServletContext(context.getServletContext());

        Tomcat.addServlet(context, "dispatcherServlet", new DispatcherServlet(appContext)).setLoadOnStartup(1);
        context.addServletMappingDecoded("/*", "dispatcherServlet");

        tomcat.start();
        System.out.println("DISPATCHER_TRACE_READY port=" + port);
        tomcat.getServer().await();
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
