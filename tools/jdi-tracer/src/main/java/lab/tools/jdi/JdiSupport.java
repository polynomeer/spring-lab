package lab.tools.jdi;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequestManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link Tracer}(CLI)와 {@link TracerServer}(NDJSON 서버 모드)가 공유하는, 자식 JVM
 * launch·브레이크포인트 스펙 파싱·활성화 로직. 이 세 가지는 "무엇을 보여줄지"(콘솔 프린트냐
 * JSON 스트림이냐)와 무관한, 두 진입점 모두에게 동일해야 하는 부분이라 분리했다.
 */
final class JdiSupport {

    private JdiSupport() {
    }

    static VirtualMachine launch(String targetClasspath, String targetMainClass)
            throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("main").setValue(targetMainClass);
        arguments.get("options").setValue("-cp " + targetClasspath);
        return connector.launch(arguments);
    }

    static Map<String, Set<String>> parseTargets(String[] args, int fromIndex) throws IOException {
        Map<String, Set<String>> targets = new LinkedHashMap<>();
        for (int i = fromIndex; i < args.length; i++) {
            String arg = args[i];
            Path path = Path.of(arg);
            List<String> specLines = Files.isRegularFile(path)
                    ? Files.readAllLines(path)
                    : List.of(arg);

            for (String line : specLines) {
                String spec = line.strip();
                if (spec.isEmpty() || spec.startsWith("#")) {
                    continue;
                }
                String[] parts = spec.split("#", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid breakpoint spec (expected Class#method1,method2): " + spec);
                }
                Set<String> methodNames = Set.of(parts[1].split(","));
                targets.merge(parts[0], methodNames, (a, b) ->
                        java.util.stream.Stream.concat(a.stream(), b.stream()).collect(Collectors.toSet()));
            }
        }
        return targets;
    }

    static void enableBreakpoints(EventRequestManager requestManager, ReferenceType refType,
                                   Map<String, Set<String>> targets) {
        Set<String> methodNames = targets.get(refType.name());
        if (methodNames == null) {
            return;
        }
        for (String methodName : methodNames) {
            for (Method method : refType.methodsByName(methodName)) {
                if (!method.isAbstract() && !method.isNative()) {
                    BreakpointRequest request = requestManager.createBreakpointRequest(method.location());
                    request.enable();
                }
            }
        }
    }
}
