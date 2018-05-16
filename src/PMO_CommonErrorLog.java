import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * <ul>
 * CHANGELOG
 * <li>0.70 - refaktoryzacja, dodano prezentacje metody i linii, z ktorej
 * przekazywana jest informacja
 * <li>0.71 - getMethodNameAndLineNumber przeszukuje klasy testowe i pokazuje
 * uproszczony stos wywolania
 * <li>0.72 - formatowanie elementu stosu w osobnej metodzie
 * <li>0.73 - dodano logowanie stackTrace wyjatku
 * <li>0.74 - uproszczono wskazywanie miejsca wystapienia wyjatku w klasie
 * testowej
 * <li>0.75 - logowanie bledu wyniklego z wystapienia wyjatku za pomoca jednej
 * metody
 * <li>0.76 - limit raportowanych bledow
 * </ul>
 * 
 * @author oramus
 * @version 0.76
 */
public class PMO_CommonErrorLog {
	private static boolean stateOK = true;
	private static boolean criticalMistake = false;
	private static Queue<String> errorLog = new LinkedList<>();
	private static int errorsCounter;
	private static final int MAX_ERRORS = 50;
	private static boolean lastErrorMessageAdded;

	synchronized public static boolean isStateOK() {
		return stateOK;
	}

	private static String formatStackElement(StackTraceElement e) {
		if (e == null)
			return "";
		return e.getClassName() + "/" + e.getMethodName() + "/" + e.getLineNumber();
	}

	public static String getMethodNameAndLineNumber() {

		StackTraceElement[] stackElements = Thread.currentThread().getStackTrace();

		for (StackTraceElement element : stackElements) {
			if (PMO_Consts.testClasses.contains(element.getClassName())) {
				return formatStackElement(element);
			}
		}

		return formatStackElement(stackElements[2]);
	}

	public static void internalError( String txt ) {
		PMO_SystemOutRedirect.println( "Wewnetrzny blad testu: " + txt );
		PMO_SystemOutRedirect.println( PMO_ThreadsHelper.thread2String());
		criticalMistake();
	}
	
	public static void exception(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		showAndLog("* EXCEPTION *", sw.toString());
	}

	public static void exceptionError(String description, Exception e) {
		error("Wykryto wyjatek " + e.toString() + "> " + description);
		exception(e);
	}

	synchronized public static boolean isCriticalMistake() {
		return criticalMistake;
	}

	private static String getCommonPrefix(String start) {
		StringBuilder sb = new StringBuilder();
		sb.append(start);
		sb.append(" ");
		sb.append(PMO_TimeHelper.getTimeFromStart());
		sb.append(" ");
		sb.append(Thread.currentThread().getName());
		sb.append("@");
		sb.append(getMethodNameAndLineNumber());
		sb.append("> ");
		return sb.toString();
	}

	synchronized public static void error(String description) {
		if (errorsCounter > MAX_ERRORS) {
			if (lastErrorMessageAdded)
				return;
			showAndLog("", PMO_StringHelper.NEW_LINE
					+ PMO_StringHelper.textInFrame("OSIAGNIETO LIMIT BLEDOW, KOLEJNE BLEDY NIE BEDA JUZ ZAPISANE"));
			showAndLog("", PMO_StringHelper.NEW_LINE + PMO_StringHelper
					.textInFrame("Aby zwiekszyc limit zapisywanych bledow zmien MAX_ERRORS w PMO_CommonErrorLog"));
			lastErrorMessageAdded = true;
		}
		stateOK = false;
		showAndLog("* BLAD *", description);
		errorsCounter++;
	}

	private static String addSpace(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++)
			sb.append(".");
		return sb.toString();
	}

	private static void showAndLog(String prefix, String description) {
		String txt = getCommonPrefix(prefix) + "\n" + addSpace(prefix.length()) + " " + description;

		errorLog.add(txt);
		PMO_Log.logFormatted(txt);
	}

	synchronized public static void warning(String description) {
		showAndLog("* UWAGA *", description);
	}

	synchronized public static List<String> getErrorLog(int size) {
		return new ArrayList<>(errorLog);
	}

	synchronized public static void criticalMistake() {
		criticalMistake = true;
		PMO_SystemOutRedirect.println("              ---- --- ------ --- ----");
		PMO_SystemOutRedirect.println("              ---- BLAD KRYTYCZNY ----");
		PMO_SystemOutRedirect.println("              ---- BLAD KRYTYCZNY ----");
		PMO_SystemOutRedirect.println("              ---- --- ------ --- ----");
		PMO_SystemOutRedirect.println("Zgłoszono błąd krytyczny, kontynuacja testu nie ma sensu");
		PMO_SystemOutRedirect.println("Poniżej pojawi się log błędów po jego wyświtleniu podjęta");
		PMO_SystemOutRedirect.println("zostanie próba wyłącznia JVM.");

		PMO_SystemOutRedirect.returnToStandardStream();
		errorLog.forEach(java.lang.System.out::println);

		PMO_Log.showLog();

		closeJVM();
	}

	private static void closeJVM() {
		java.lang.System.out.println("EXIT");
		java.lang.System.exit(0);
		java.lang.System.out.println("HALT");
		Runtime.getRuntime().halt(0);
	}

}
