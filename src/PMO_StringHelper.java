
public class PMO_StringHelper {
	public static final String UP_RIGHT_CORNER = "\u2513";
	public static final String DOWN_RIGHT_CORNER = "\u251B";
	public static final String UP_LEFT_CORNER = "\u250F";
	public static final String DOWN_LEFT_CORNER = "\u2517";
	public static final String HORIZONTAL = "\u2501";
	public static final String VERTICAL = "\u2503";
	public static final String NEW_LINE = "\n";
	public static final String SPACE = " ";

	public static String generate(String pattern, int length) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			sb.append(pattern);
		}
		return sb.toString();
	}

	public static String textInFrame(String text) {
		StringBuilder sb = new StringBuilder();
		int width = text.length();
		int MARGIN = (80 - width) / 2;
		width += 2 * MARGIN;
		sb.append(UP_LEFT_CORNER);
		sb.append(generate(HORIZONTAL, width));
		sb.append(UP_RIGHT_CORNER);
		sb.append(NEW_LINE);
		sb.append(VERTICAL);
		sb.append(generate(SPACE, MARGIN));
		sb.append(text);
		sb.append(generate(SPACE, MARGIN));
		sb.append(VERTICAL);
		sb.append(NEW_LINE);
		sb.append(DOWN_LEFT_CORNER);
		sb.append(generate(HORIZONTAL, width));
		sb.append(DOWN_RIGHT_CORNER);
		return sb.toString();
	}

	public static String bool2VictoryOrDefeat(boolean value) {
		if (value)
			return "Victory";
		else
			return "Deafeat";
	}
}
