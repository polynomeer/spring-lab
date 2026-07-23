package lab.tools.jdi;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Launches a target Java program under a JDI-controlled child JVM, sets
 * breakpoints on the given class/method targets, and prints the call stack
 * (+ visible local variables) every time one is hit, then resumes.
 *
 * Usage:
 *   java --add-modules jdk.jdi -cp <tracerClasses> lab.tools.jdi.Tracer \
 *        <targetClasspath> <targetMainClass> <breakpointSpec>...
 *
 * Each breakpointSpec is either:
 *   - "fully.qualified.ClassName#method1,method2,..."
 *   - a path to a file containing one such spec per line (blank lines and
 *     lines starting with '#' are ignored)
 */
public final class Tracer {

    private Tracer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: Tracer <targetClasspath> <targetMainClass> <breakpointSpec|specFile>...");
            System.exit(1);
        }

        String targetClasspath = args[0];
        String targetMainClass = args[1];
        Map<String, Set<String>> targets = parseTargets(args, 2);

        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("main").setValue(targetMainClass);
        arguments.get("options").setValue("-cp " + targetClasspath);

        VirtualMachine vm = connector.launch(arguments);

        EventRequestManager requestManager = vm.eventRequestManager();
        for (String className : targets.keySet()) {
            ClassPrepareRequest request = requestManager.createClassPrepareRequest();
            request.addClassFilter(className);
            request.enable();
        }

        Process process = vm.process();
        redirect(process.getInputStream(), System.out);
        redirect(process.getErrorStream(), System.err);

        EventQueue queue = vm.eventQueue();
        vm.resume();

        boolean connected = true;
        int hitCount = 0;
        while (connected) {
            EventSet eventSet = queue.remove();
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent classPrepareEvent) {
                    enableBreakpoints(requestManager, classPrepareEvent.referenceType(), targets);
                } else if (event instanceof BreakpointEvent breakpointEvent) {
                    hitCount++;
                    printHit(hitCount, breakpointEvent);
                } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                    connected = false;
                }
            }
            if (connected) {
                eventSet.resume();
            }
        }
        process.waitFor();
        System.out.println("\n=== target process exited, total breakpoint hits: " + hitCount + " ===");
    }

    private static Map<String, Set<String>> parseTargets(String[] args, int fromIndex) throws IOException {
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

    private static void enableBreakpoints(EventRequestManager requestManager, ReferenceType refType,
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

    private static void printHit(int n, BreakpointEvent event) throws IncompatibleThreadStateException {
        ThreadReference thread = event.thread();
        Location location = event.location();
        System.out.println();
        System.out.printf("=== hit #%d: %s#%s (line %d) ===%n",
                n, location.declaringType().name(), location.method().name(), location.lineNumber());

        List<StackFrame> frames = thread.frames();
        int depth = Math.min(frames.size(), 14);
        for (int i = 0; i < depth; i++) {
            Location frameLocation = frames.get(i).location();
            System.out.printf("  [%2d] %s#%s%n", i, frameLocation.declaringType().name(), frameLocation.method().name());
        }

        try {
            StackFrame top = frames.get(0);
            List<LocalVariable> variables = top.visibleVariables();
            if (variables.isEmpty()) {
                System.out.println("    (no visible local variables at this line)");
            }
            for (LocalVariable variable : variables) {
                Value value = top.getValue(variable);
                System.out.println("    " + variable.name() + " = " + value);
            }
        } catch (AbsentInformationException e) {
            System.out.println("    (no local variable debug info in this jar)");
        }
    }

    private static void redirect(InputStream in, java.io.PrintStream out) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.println("[target] " + line);
                }
            } catch (IOException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
