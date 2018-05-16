
public interface KitchenInterface {
	/**
	 * Zwraca liczbę posłków, które kuchnia może przygotowywać
	 * w tym samym czasie.
	 * 
	 * @return Liczba zadań (posiłków), które kuchnia jest w stanie
	 * jednocześnie przygotowywać.
	 */
	public int getNumberOfParallelTasks();
	/**
	 * 
	 * Zlecenie przygotowania posiłku o numerze orderID.
	 * @param orderID Identyfikator posiłku, który ma przygotować kuchnia.
	 */
	public void prepare( int orderID );
	
	/**
	 * Rejestracja obiektu, któremu przekazywana będzie informacja
	 * o zakończeniu przygotowywania posiłku.
	 * @param receiverInterface obiekt nasłuchujący informacji o 
	 * zakończeniu przygotowania posiłku.
	 */
	public void registerReceiver(  ReceiverInterface receiverInterface );
}
