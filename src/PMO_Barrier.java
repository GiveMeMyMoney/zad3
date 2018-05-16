public class PMO_Barrier implements  PMO_LogSource {
    private final int threads;
    private int arrived;
    private final Runnable allArrivedCode;
    private Runnable code2RunBeforeThreadRelease;

    public PMO_Barrier( int threads, Runnable allArrivedCode ) {
        this.threads = threads;
        this.allArrivedCode = allArrivedCode;
    }

    public void setCode2RunBeforeThreadRelease( Runnable code2RunBeforeThreadRelease ) {
        this.code2RunBeforeThreadRelease = code2RunBeforeThreadRelease;
    }

    public void await() {
        synchronized ( this ) {
            arrived++;

            if ( arrived == threads ) {
                Thread th = PMO_ThreadsHelper.createThreadAndRegister(
                        new Runnable() {
                            @Override
                            public void run() {
                                synchronized ( this ) {
                                    // log + efekt uboczny - trzeba poczekac na wait
                                    // w ostatnim watku
                                    log( "PMO_Barrier: Last thread arrived ");
                                }
                                allArrivedCode.run();
                            }
                        } );
                th.start();
            }
            PMO_ThreadsHelper.wait(this);
        } // synchronized
    }

    public void releaseOneThread() {
        log("PMO_Barrier: One thread release");
        if ( code2RunBeforeThreadRelease != null )
            code2RunBeforeThreadRelease.run();
        synchronized ( this ) {
            arrived--;
            notify();
        }
    }

    public void releaseAllThreads() {
        synchronized( this ) {
            arrived = 0;
            notifyAll();
        }
        log( "PMO_Barrier: All remaining threads released");
    }

    synchronized public int threadsWaitingForRelease() {
        return arrived;
    }
}
