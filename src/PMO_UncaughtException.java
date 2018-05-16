import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PMO_UncaughtException implements Thread.UncaughtExceptionHandler, PMO_LogSource {

    private final Map<Thread, Throwable> log = Collections.synchronizedMap(new HashMap<>());
    private final static PMO_UncaughtException ref = new PMO_UncaughtException();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        PMO_SystemOutRedirect.println("[zlapano wyjatek " + e.toString() + "]");
        PMO_SystemOutRedirect.println(PMO_TestHelper.stackTrace2String(e));
        exception2log("UncaughtException ", e, t);
    }

    public boolean logIsEmpty() {
        return log.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        log.entrySet().forEach(
                e -> {
                    result.append("Watek " + e.getKey().getName() + " zglosil wyjatek " + e.getValue().toString() + "\n");
                    result.append(PMO_TestHelper.stackTrace2String(e.getValue()));
                });

        return result.toString();
    }

    private PMO_UncaughtException() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        log( "Zarejestrowano UncaughtExceptionHandler");
    }

    public static PMO_UncaughtException getRef() {
        return ref;
    }
}
