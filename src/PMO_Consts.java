import java.util.ArrayList;
import java.util.Collection;

public class PMO_Consts {
	public static final boolean VERBOSE = false;
	
	public static final int TASKS_MULT_MIN = 3;
	public static final int TASKS_MULT_DELTA = 4;
	
	public static final long TASK_SLEEP_TIME_MIN = 500;
	public static final long TASK_SLEEP_TIME_DELTA = 50;
	
	public static final long TASKS_SUBMITION_TIME_LIMIT = 60;
		
	public static final int CORES = 12;
	
	public static final int THE_SAME_ERROR_REPETITIONS_LIMIT = 10;
	
	public static final Collection<String> testClasses = 
			new ArrayList<String>() {{
			}};
}
