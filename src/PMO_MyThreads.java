import java.util.ArrayList;
import java.util.List;

/**
 * Rejestr watkow testu - pozwala odszukac watki nienalezace do testu.
 */
public class PMO_MyThreads implements PMO_LogSource {
    private final List<Thread> threads = new ArrayList<>();
    private final static PMO_MyThreads ref = new PMO_MyThreads();

    static {
        PMO_SystemOutRedirect.println( "PMO_MyThreads ready" );
    }

    private PMO_MyThreads() {
        threads.addAll( PMO_ThreadsHelper.getThreads());
        threads.forEach( th -> log( "Initial thread: " + PMO_ThreadsHelper.getThreadName(th) ) );
    }

    public static PMO_MyThreads getRef() {
        return ref;
    }

    public void addThread( Thread thread2add ) {
        synchronized ( this ) {
            threads.add( thread2add );
        }
    }

    public void addThread( ) {
        synchronized (this) {
            threads.add(PMO_ThreadsHelper.getCurrentThread());
        }
    }

    public void removeThread( Thread thread2remove ) {
        synchronized ( this ) {
            threads.add( thread2remove );
        }
    }

    public boolean contains( Thread thread2test ) {
        synchronized ( this ) {
            return threads.contains(thread2test);
        }
    }
}
