import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Wersja
 * 0.6 - dodano tworzenie watkow z rejestracja ich w PMO_MyThreads
 * 0.65 - komunikacja z logami za pomoca PMO_LogSource
 * 0.7 - dodano pobieranie informacji o wszystkich wykonujacych sie watkach
 * 0.75 - dodano wait na barierze z maksymalnym okresem oczekiwania
 */
public class PMO_ThreadsHelper {
    private static AtomicInteger threadsCounter = new AtomicInteger(0);
    private static PMO_MyThreads register;

    static
    {
        PMO_SystemOutRedirect.println( "PMO_ThreadsHelper ready" );
    }

    public static String getThreadName() {
        return getCurrentThread().getName();
    }

    public static String getThreadName(Thread th) {
        return th.getName();
    }

    public static Thread getCurrentThread() {
        return Thread.currentThread();
    }

    public static Thread createThreadAndRegister(Runnable code) {
        Runnable exec = new Runnable() {
            @Override
            public void run() {
                String name = getThreadName();
                PMO_LogSource.logS("Thread " + name + " started");

                code.run();

                register.removeThread(getCurrentThread());
                PMO_LogSource.logS("Thread " + name + " finished");
            }
        };
        Thread th = new Thread(exec);
        th.setDaemon(true);
        th.setName("PMO_Thread_" + String.format("%04d", threadsCounter.incrementAndGet()));

        if ( register == null ) {
            register = PMO_MyThreads.getRef();
        }

        register.addThread(th);
        return th;
    }

    public static void joinThreads(List<Thread> ths) {
        ths.forEach((t) -> {
            try {
                t.join();
            } catch (InterruptedException ie) {
                PMO_LogSource.exceptionS2log("Doszlo do wyjatku w trakcie join", ie);
            }
        });
    }

    public static List<Thread> createAndStartRegisteredThreads(Collection<? extends Runnable> tasks, boolean daemon) {
        List<Thread> result = new ArrayList<>();

        tasks.forEach(t -> {
            result.add(createThreadAndRegister(t));
        });

        if (daemon) {
            result.forEach(t -> t.setDaemon(true));
        }

        result.forEach(t -> t.start());

        return result;
    }


    public static List<Thread> createAndStartThreads(Collection<? extends Runnable> tasks, boolean daemon) {
        List<Thread> result = new ArrayList<>();

        tasks.forEach(t -> {
            result.add(new Thread(t));
        });

        if (daemon) {
            result.forEach(t -> t.setDaemon(true));
        }

        result.forEach(t -> t.start());

        return result;
    }


    public static void showThreads(Set<Thread> threadSet) {
        threadSet.forEach((th) -> {
            System.out.println(PMO_ThreadsHelper.thread2String(th));
        });
    }

    public static void showThreads() {
        showThreads(Thread.getAllStackTraces().keySet());
    }

    public static Set<Thread> getThreads() {
        return Thread.getAllStackTraces().keySet();
    }

    public static Set<Thread> eliminateThreadsByState(Set<Thread> threads, Set<Thread.State> states) {
        return threads.stream().filter((th) -> states.contains(th.getState())).collect(Collectors.toSet());
    }

    public static Set<Thread> eliminateThreadsByName(Set<Thread> threads, Set<String> remove) {
        return threads.stream().filter(th -> !remove.contains(th.getName())).collect(Collectors.toSet());
    }

    public static Set<Thread> eliminateThreadsByThread(Set<Thread> threads, Set<Thread> remove) {
        return threads.stream().filter(th -> !remove.contains(th)).collect(Collectors.toSet());
    }

    public static Set<Thread> eliminateThreadByClassName(Set<Thread> threads, String className) {
        return threads.stream().filter(th -> !thread2String(th).contains(className)).collect(Collectors.toSet());
    }

    public static boolean executingSleep(StackTraceElement element) {
        if (!element.isNativeMethod()) return false;

        return element.getMethodName().equals("sleep") &&
                element.getClassName().equals("java.lang.Thread");
    }

    public static boolean executingSleep(Thread thread) {
        if (!thread.isAlive()) return false;
        StackTraceElement[] stet = thread.getStackTrace();
        StackTraceElement firstStackElement = stet[0];

        return executingSleep(firstStackElement);
    }

    public static String thread2String() {
        return thread2String(getCurrentThread());
    }

    public static String stackTraceElement2String(StackTraceElement element) {
        StringBuilder sb = new StringBuilder();

        sb.append(" Class: ");
        sb.append(element.getClassName());
        sb.append(element.isNativeMethod() ? " Native method: " : " Java method: ");
        sb.append(element.getMethodName());
        sb.append(" @");
        sb.append(element.getFileName());
        if (!element.isNativeMethod()) {
            sb.append("@");
            sb.append(element.getLineNumber());
        }

        return sb.toString();
    }

    public static String thread2String(Thread thread) {
        return thread2String(thread, thread.getStackTrace());
    }

    public static String thread2String(Thread thread, StackTraceElement[] stet) {
        StringBuilder sb = new StringBuilder();

        String threadName = "Thread: " + thread.getName();
        sb.append("Thread > ");
        sb.append(threadName);
        sb.append("\n");

        if (thread.isAlive()) {
            Thread.State state = thread.getState();
            sb.append(threadName);
            sb.append(" State ");
            sb.append(state.name());
            sb.append("\n");
            for (StackTraceElement ste : stet) {
                sb.append(threadName);
                sb.append(stackTraceElement2String(ste));
                sb.append("\n");
            }
        } else {
            sb.append(threadName);
            sb.append("is not alive\n");
        }

        return sb.toString();
    }


    public static boolean wait(Object o) {
        if (o != null) {
            synchronized (o) {
                try {
                    o.wait();
                    return true;
                } catch (InterruptedException ie) {
                    PMO_LogSource.exceptionS2log("Doszlo do wyjatku w trakcie wait", ie);
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean wait(CyclicBarrier cb) {
        if (cb != null) {
            try {
                cb.await();
                return true;
            } catch (InterruptedException | BrokenBarrierException e) {
                PMO_LogSource.exceptionS2log("Doszlo do wyjatku w trakcie await", e);
                return false;
            }
        }
        return true;
    }

    public static boolean wait(CyclicBarrier cb, long timeout ) {
        if (cb != null) {
            try {
                try {
                    if ( cb.isBroken() ) {
                        cb.reset();
                        PMO_Log.log( "CyclicBarrier została zresetowana do stanu początkowego");
                    }
                    cb.await( timeout, TimeUnit.MILLISECONDS );
                } catch (TimeoutException e) {
                    PMO_LogSource.logS( "Wątek " + thread2String( getCurrentThread() ) + " zakończył await(CyclicBarrier) przez timeout");
                }
                return true;
            } catch (InterruptedException | BrokenBarrierException e) {
                PMO_LogSource.exceptionS2log("Doszlo do wyjatku w trakcie await", e);
                return false;
            }
        }
        return true;
    }


    public static boolean testIfTrueAndWait(AtomicBoolean o) {
        if (o != null) {
            if (o.get()) {
                return wait(o);
            }
        }
        return true;
    }

    public static void main(String[] argv) {

        Thread th1 = createThreadAndRegister(() -> {
            PMO_TimeHelper.sleep(1000);
        });
        th1.start();

        Thread th2 = createThreadAndRegister(() -> {
            synchronized (PMO_ThreadsHelper.class) {
                try {
                    PMO_ThreadsHelper.class.wait(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        th2.start();

        PMO_TimeHelper.sleep(100);

        showThreads();

    }

}
