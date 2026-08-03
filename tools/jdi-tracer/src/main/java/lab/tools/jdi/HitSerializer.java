package lab.tools.jdi;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.BreakpointEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code Tracer#printHit()}과 같은 정보를 콘솔 문자열 대신 {@link TraceEvent}(순수 데이터)로
 * 뽑아낸다 - CLI({@link Tracer})와 서버({@link TracerServer})가 같은 추출 로직을 공유하기
 * 위한 분리다.
 */
final class HitSerializer {

    private static final int MAX_STACK_DEPTH = 14;
    private static final int MAX_VALUE_LENGTH = 200;

    private HitSerializer() {
    }

    static TraceEvent toTraceEvent(int hitId, BreakpointEvent event) throws IncompatibleThreadStateException {
        ThreadReference thread = event.thread();
        Location loc = event.location();

        List<StackFrame> frames = thread.frames();
        int depth = Math.min(frames.size(), MAX_STACK_DEPTH);
        List<TraceEvent.Frame> stack = new ArrayList<>(depth);
        for (int i = 0; i < depth; i++) {
            Location frameLocation = frames.get(i).location();
            stack.add(new TraceEvent.Frame(frameLocation.declaringType().name(), frameLocation.method().name()));
        }

        List<TraceEvent.Local> locals = new ArrayList<>();
        boolean localsAvailable = true;
        try {
            StackFrame top = frames.get(0);
            for (LocalVariable variable : top.visibleVariables()) {
                Value value = top.getValue(variable);
                locals.add(new TraceEvent.Local(variable.name(), variable.typeName(), summarize(value)));
            }
        } catch (AbsentInformationException e) {
            localsAvailable = false;
        }

        return new TraceEvent(
                hitId,
                System.nanoTime(),
                thread.name(),
                new TraceEvent.Location(loc.declaringType().name(), loc.method().name(), loc.lineNumber()),
                stack,
                locals,
                localsAvailable);
    }

    private static String summarize(Value value) {
        if (value == null) {
            return "null";
        }
        String text = value.toString();
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "…" : text;
    }
}
