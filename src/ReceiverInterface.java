
public interface ReceiverInterface {
	/**
	 * Zakończono przygotowywanie posiłku o podanym 
	 * identyfikatorze.
	 * @param orderID identyfikator przygotowanego
	 * posiłku.
	 */
	public void mealReady( int orderID );
}
