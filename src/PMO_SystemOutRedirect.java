import java.io.FileNotFoundException;
import java.io.PrintStream;

public class PMO_SystemOutRedirect {
	private static PrintStream ps;
	private static PrintStream orig = java.lang.System.out;
	private static boolean redirectionON = false;

	static {
		try {
			ps = new PrintStream("/dev/null");
		} catch (FileNotFoundException e) {
			java.lang.System.out.println( "System.out.println can not be redirected to /dev/null");
		}
	}

	public static void startRedirectionToNull() {
		if (ps != null) {
			java.lang.System.setOut(ps);
			java.lang.System.setErr(ps);
		}
		redirectionON = true;
	}

	public static void returnToStandardStream() {
		java.lang.System.setOut(orig);
		java.lang.System.setErr(orig);
		redirectionON = false;
	}

	public static void closeNullStream() {
		ps.close();
	}

	public static void print(String s) {
		if (redirectionON) {
			returnToStandardStream();
			java.lang.System.out.print(s);
			startRedirectionToNull();
		} else {
			java.lang.System.out.print(s);
		}
	}

	synchronized public static void println(String s) {
		if (redirectionON) {
			returnToStandardStream();
			java.lang.System.out.println(s);
			startRedirectionToNull();
		} else {
			java.lang.System.out.println(s);
		}
	}
	
	public static void showCurrentMethodName() {
		println( "-------------------------------------" );
		println( Thread.currentThread().getStackTrace()[2].getMethodName()) ;
		println( "-------------------------------------" );
	}

}
