import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PMO_ListHelper {
	public static List<Integer> toList(int... seats) {
		List<Integer> result = new ArrayList<>(seats.length);
		for (Integer i : seats) {
			result.add(i);
		}
		return Collections.unmodifiableList(result);
	}

}
