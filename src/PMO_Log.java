import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class PMO_Log {
	private static List<String> log = Collections.synchronizedList( new LinkedList<>() );
	
	public static void log( String txt ) {
		StringBuilder sb = new StringBuilder();
		sb.append( "[" );
		sb.append( PMO_TimeHelper.getTimeFromStart() );
		sb.append( ":" );
		sb.append( Thread.currentThread().getName() );
		sb.append( "> " );
		sb.append( txt );
		sb.append( "]" );
		log.add( sb.toString() );
	}
	
	public static void logFormatted( String txt ) {
		log.add( txt );
	}
	
	public static void showLog() {
		PMO_SystemOutRedirect.returnToStandardStream();
		log.forEach( System.out::println );
	}
}
