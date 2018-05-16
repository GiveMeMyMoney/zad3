
public class PMO_GeneralPurposeFabric {
	public static Object fabric(String className, String interfaceName) {
		try {
			if (Class.forName(interfaceName).isAssignableFrom(Class.forName(className))) {
				return Class.forName(className).newInstance();
			} else {
				PMO_SystemOutRedirect
						.println("Blad: klasa " + className + " nie jest zgodna z interfejsem " + interfaceName);
				java.lang.System.exit(1);
			}
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			PMO_SystemOutRedirect.println(
					"Blad: W trakcie tworzenia obiektu klasy " + className + " pojawil sie wyjatek " + e.toString());
			e.printStackTrace();
		}
		java.lang.System.exit(1);
		return null;
	}

}
