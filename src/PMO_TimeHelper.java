import java.util.function.BooleanSupplier;

public class PMO_TimeHelper {

	private static long t0 = java.lang.System.currentTimeMillis();

	public interface PeriodOfTime {
		void updateIfBigger(long value);

		long getValue();
	}

	private static PeriodOfTime commonPOTime;

	public static PeriodOfTime getPeriodOfTimeTool() {
		return new PeriodOfTime() {

			private long value = 0;

			@Override
			synchronized public long getValue() {
				return value;
			}

			@Override
			synchronized public void updateIfBigger(long value) {
				if (value > this.value) {
					this.value = value;
				}
			}
		};
	}

	public static void createCommonPeriodOfTimeTool() {
		commonPOTime = getPeriodOfTimeTool();
	}

	public static PeriodOfTime getCommonPeriodOfTimeTool() {
		return commonPOTime;
	}

	public static long executionTime(Runnable run) {
		long start = java.lang.System.currentTimeMillis();

		try {
			run.run();
		} catch ( Exception e ) {
			PMO_Log.log( "Exception " + e.getMessage() + " caught - execution time unknown");
			return -1;
		}

		return java.lang.System.currentTimeMillis() - start;
	}

	public static long executionTime(BooleanSupplier run, long resolution) {
		long start = java.lang.System.currentTimeMillis();

		while (run.getAsBoolean()) {
			sleep(resolution);
		}

		return java.lang.System.currentTimeMillis() - start;
	}

	// aktywny sen - nie jest wywolywana metoda sleep
	public static void asleep( long millis ) {
		long tf = getCurrentTime() + millis;
		do {
		} while ( getCurrentTime() < tf );
	}

	public static boolean sleep(long millis) {
		long tf = getCurrentTime() + millis;
		long tleft;
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
			PMO_CommonErrorLog.warning("W trakcie wykonywania sleep doszlo do wyjatku InterruptedException");
			do {
				tleft = tf - getCurrentTime();
				PMO_CommonErrorLog.warning("Do zakonczeniu snu pozostaje " + tleft);
				if (tleft > 0) {
					try {
						Thread.sleep(tleft);
					} catch (InterruptedException e1) {
						PMO_CommonErrorLog.warning("W trakcie wykonywania sleep ponownie doszlo do wyjatku InterruptedException");
					}
				} else {
					PMO_CommonErrorLog.warning("Koniec snu");					
				}
			} while (tleft > 0);

			return false;
		}
		return true;
	}

	public static long getCurrentTime() {
		return java.lang.System.currentTimeMillis();
	}

	public static long getTimeFromStart() {
		return java.lang.System.currentTimeMillis() - t0;
	}
}
