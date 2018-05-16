import java.util.HashMap;
import java.util.Map;

public class PMO_Start {
	private static boolean runTest(PMO_RunTestTimeout test) {
		long timeToFinish = test.getRequiredTime();

		Thread th = PMO_ThreadsHelper.createThreadAndRegister( test );
		th.start();

		PMO_SystemOutRedirect.println("timeToFinish = " + timeToFinish);
		PMO_SystemOutRedirect.println("Maksymalny czas oczekiwania to " + (timeToFinish / 1000) + " sekund");

		long beforeJoin = java.lang.System.currentTimeMillis();
		try {
			th.join(timeToFinish);
		} catch (InterruptedException e) {
		}
		long remainingTime = timeToFinish - java.lang.System.currentTimeMillis() + beforeJoin;
		if (remainingTime > 0) {
			PMO_SystemOutRedirect.println("Dodatkowy czas: " + remainingTime + " msec");
			PMO_TimeHelper.sleep(remainingTime);
		}

		PMO_SystemOutRedirect.println("Zakonczyl sie czas oczekiwania na join()");

		if (th.isAlive()) {
			PMO_SystemOutRedirect.println("BLAD: Test nie zostal ukonczony na czas");
			PMO_ThreadWatcher.watch(th);
			return false;
		} else {
			PMO_SystemOutRedirect.println("Uruchamiam testOK");
			return test.testOK();
		}

	}

	private static void shutdownIfFail(boolean testOK) {
		if (!testOK) {
			PMO_Verdict.show(false);
			shutdown();
		}
	}

	private static void showTest(String txt) {
		PMO_Log.log("");
		PMO_Log.log("xxxxx  " + txt + "  xxxxx");
		PMO_Log.log("");
		PMO_SystemOutRedirect.println("+------------------------+");
		PMO_SystemOutRedirect.println("|                        |");
		PMO_SystemOutRedirect.println("+-- " + txt + " --+");
		PMO_SystemOutRedirect.println("|                        |");
		PMO_SystemOutRedirect.println("+------------------------+");
	}

	private static void shutdown() {
		java.lang.System.out.println("HALT");
		Runtime.getRuntime().halt(0);
		java.lang.System.out.println("EXIT");
		java.lang.System.exit(0);
	}

	private static void showLogs(boolean result) {
		PMO_UncaughtException uncaughtExceptionsLog = PMO_UncaughtException.getRef();
		if (!result) {
            PMO_SystemOutRedirect.println("----- log -----");
            PMO_Log.showLog();

			PMO_SystemOutRedirect.println("--- log bledow ---");
			PMO_CommonErrorLog.getErrorLog(0).forEach(PMO_SystemOutRedirect::println);
		}

		if (!uncaughtExceptionsLog.logIsEmpty()) {
			PMO_SystemOutRedirect.println("--- log wyjatkow ---");
			PMO_SystemOutRedirect.println(uncaughtExceptionsLog.toString());
		}
	}

	private static boolean runSingleTest( String txt, PMO_RunTestTimeout test ) {
	    showTest( txt );
	    return runTest( test );
    }

    private static boolean findTestAndRun(String cmd,
                                          Map<String, PMO_RunTestTimeout> tests ) {

        boolean result = false;

        for ( Map.Entry<String,PMO_RunTestTimeout> entry : tests.entrySet() ) {
            if ( cmd.contains( entry.getKey() ) ) {
                result = runSingleTest( "--- TEST " + entry.getKey() + " ---", entry.getValue());
            }
        }
        return result;
    }

	public static void main(String[] args) {

		PMO_SystemOutRedirect.startRedirectionToNull();

        if ( args.length < 1 ) {
            PMO_SystemOutRedirect.println( "USAGE: java PMO_Start A");
            shutdown();
        } else if ( args[0].length() != 1 ) {
            PMO_SystemOutRedirect.println( "Jedno uruchomienie - jeden test" );
            shutdown();
        }

        Map<String, PMO_RunTestTimeout > tests = new HashMap<>();

        tests.put( "A", new PMO_Test() );

        PMO_MyThreads.getRef();
        PMO_UncaughtException.getRef();

        PMO_SleepTracker sleepTracker = PMO_SleepTracker.getRef();
        sleepTracker.setDelay( 333 );

        boolean result = true;
        result &= findTestAndRun( args[0], tests );

        result &= PMO_CommonErrorLog.isStateOK();

		PMO_SystemOutRedirect.println( "Czas od rozpoczęcia testu " +
                PMO_TimeHelper.getTimeFromStart() + " msec");

		showLogs(result);

		PMO_Verdict.show(result);

		shutdown();
	}


}

