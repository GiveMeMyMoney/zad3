import java.lang.Thread.State;

public class PMO_ThreadWatcher {
	private Thread ownThread;
	private Thread thread2watch;

	public PMO_ThreadWatcher(Thread thread2watch) {
		this.thread2watch = thread2watch;
	}

	private void print(String txt) {
		print(thread2watch.getName(), txt);
	}

	private static void print(String threadName, String txt) {
		PMO_SystemOutRedirect
				.println("[ " + PMO_TimeHelper.getTimeFromStart() + " - THREAD> " + threadName + " " + txt + " ]");
	}

	public static void watch(Thread thread2watch) {
		String threadName = thread2watch.getName();

		if (thread2watch.isAlive()) {
			Thread.State state = thread2watch.getState();
			print(threadName, "State " + state.name());
			StackTraceElement[] stet = thread2watch.getStackTrace();
			for (StackTraceElement ste : stet) {
				print(threadName,
						"Class: " + ste.getClassName() + " Method: " + ste.getMethodName() + "@" + ste.getLineNumber());
				print(threadName, " -- metoda o nazwie: " + ste.getMethodName());
				print(threadName, " -- linia kodu: " + ste.getLineNumber());
				print(threadName, " -- kod zrodlowy: " + ste.getFileName());
			}
		} else {
			print(threadName, "is not alive");
		}

	}

	public void start(long delay) {
		ownThread = new Thread(new Runnable() {

			@Override
			public void run() {
				boolean first = true;
				PMO_TimeHelper.sleep(delay);
				if (thread2watch.isAlive()) {
					Thread.State state = thread2watch.getState();
					print("State " + state.name());
					if (state == State.WAITING) {
						print("Thread is waitting...");
						StackTraceElement[] stet = thread2watch.getStackTrace();
						for (StackTraceElement ste : stet) {
							print("Class: " + ste.getClassName() + " Method: " + ste.getMethodName() + "@"
									+ ste.getLineNumber());
							if (ste.getClassName().equals("Broker") && first) {
								first = false;
								print("Blokada watku na obiekcie klasy Broker");
								print(" -- metoda o nazwie: " + ste.getMethodName());
								print(" -- linia kodu: " + ste.getLineNumber());
								print(" -- kod zrodlowy: " + ste.getFileName());
							}
						}

					}
				}
			}
		});
		ownThread.setDaemon(true);
		ownThread.start();
	}

}
