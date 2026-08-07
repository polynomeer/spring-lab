package lab.experiments.mvcerror;

import java.net.ServerSocket;
import java.nio.file.Files;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 대시보드 mvc-exception-priority 시나리오의 라이브 진입점 - dispatcher-servlet-trace의
 * {@code DispatcherServletTraceLab}과 동일한 패턴(임베디드 Tomcat + 실제 HTTP)이다.
 *
 * <p>{@code EMBEDDED_SERVER_READY port=<n>}를 표준 출력으로 찍어 대시보드 백엔드
 * ({@code ScenarioSession})가 포트를 알아내게 한다.
 */
public final class ExceptionPipelineLab {

    private ExceptionPipelineLab() {
    }

    public static void main(String[] args) throws Exception {
        int port = findFreePort();

        AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
        appContext.register(ExceptionPipelineConfig.class);

        String baseDir = Files.createTempDirectory("mvc-exception-pipeline").toString();
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(baseDir);
        tomcat.getConnector(); // 커넥터를 지금 강제로 만들어 둔다 - 그러지 않으면 start()가 바인딩을 건너뛴다.

        Context context = tomcat.addContext("", baseDir);
        appContext.setServletContext(context.getServletContext());

        Tomcat.addServlet(context, "dispatcherServlet", new DispatcherServlet(appContext)).setLoadOnStartup(1);
        context.addServletMappingDecoded("/*", "dispatcherServlet");

        tomcat.start();
        System.out.println("EMBEDDED_SERVER_READY port=" + port);
        tomcat.getServer().await();
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
