
public interface PMO_LogSource {
    public default void log(String txt) {
        logS(txt);
    }

    public default void error(String txt) {
        errorS(txt);
    }

    public default void exception2log(String txt, Throwable th) {
        exceptionS2log(txt, th);
    }

    public default void exception2error(String txt, Throwable th) {
        exceptionS2error(txt, th);
    }

    public default void exception2log(String txt, Throwable th, Thread thread) {
        exceptionS2log(txt, th, thread);
    }

    public default void exception2error(String txt, Throwable th, Thread thread) {
        exceptionS2error(txt, th, thread);
    }

    public static void logS(String txt) {
        PMO_Log.log(txt);
    }

    public static void errorS(String txt) {
        logS(txt);
        PMO_CommonErrorLog.error(txt);
        PMO_SystemOutRedirect.println(txt);
    }

    public static void exceptionS2log(String txt, Throwable th) {
        logS(txt + " " + th.toString());
        logS(PMO_TestHelper.stackTrace2String(th));
    }

    public static void exceptionS2error(String txt, Throwable th) {
        errorS(txt + " " + th.toString());
        errorS(PMO_TestHelper.stackTrace2String(th));
    }

    public static void exceptionS2log(String txt, Throwable th, Thread thread) {
        exceptionS2log(txt, th);
        logS(PMO_ThreadsHelper.thread2String(thread));
    }

    public static void exceptionS2error(String txt, Throwable th, Thread thread) {
        exceptionS2error(txt, th);
        errorS(PMO_ThreadsHelper.thread2String(thread));
    }

}
