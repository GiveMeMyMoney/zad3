import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Klasa okresowo wykonuje przeglad watkow.
 * W przypadku odkrycia watku, ktory spi generowany jest
 * raport (na podstawie stack trace). Raport zglaszany jest jako blad.
 */
public class PMO_SleepTracker implements PMO_LogSource, PMO_Testable {

    private final AtomicBoolean sleepingThreadFound;
    private long delay;
    private Map<Thread, StackTraceElement[]> threadsSnapshot;
    private final PMO_MyThreads registeredThreads;
    private final AtomicBoolean continueFlag;
    private static final PMO_SleepTracker ref = new PMO_SleepTracker();
    private final AtomicBoolean threadRunning = new AtomicBoolean(false);

    {
        continueFlag = new AtomicBoolean(true);
        sleepingThreadFound = new AtomicBoolean(false);
        delay = 1000; // 1 sekunda pomiedzy testami
    }

    private class Tracker implements Runnable {

        private boolean isSleeping(StackTraceElement[] elements) {
            if (elements.length == 0) return false;
            return PMO_ThreadsHelper.executingSleep(elements[0]);
        }

        private void reportCaughtSleeping(Thread thread, StackTraceElement[] elements) {
            String report = PMO_ThreadsHelper.thread2String(thread, elements);

            error("User thread in forbidden state\n" + report);
            sleepingThreadFound.set(true);
        }

        @Override
        public void run() {

            assert registeredThreads != null;
            assert continueFlag != null;
            assert delay > 0;

            threadRunning.set(true);
            while (continueFlag.get()) {
                PMO_TimeHelper.sleep(delay);

                threadsSnapshot = Thread.getAllStackTraces();

                threadsSnapshot.entrySet().removeIf(e -> registeredThreads.contains(e.getKey()));

                threadsSnapshot.entrySet().stream().filter(e -> isSleeping(e.getValue())).forEach(e ->
                        reportCaughtSleeping(e.getKey(), e.getValue()));
            }
            threadRunning.set(false);
        }
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public void stopTracker() {
        continueFlag.set(false);
    }

    public boolean isRunning() {
        return threadRunning.get();
    }

    @Override
    public boolean testOK() {
        return !sleepingThreadFound.get();
    }

    public void start() {
        continueFlag.set(true);
        Thread th = PMO_ThreadsHelper.createThreadAndRegister(new Tracker());
        th.start();
    }

    private PMO_SleepTracker() {
        this.registeredThreads = PMO_MyThreads.getRef();
    }

    public static PMO_SleepTracker getRef() {
        return ref;
    }

}

