/**
 * Interfejs kelnera.
 */
public interface WaiterInterface {

	/**
	 * Unikalny identyfikator liczbowy kelnera.
	 * @return identyfikator kelnera
	 */
	public int getID();

	/**
	 * Zlecenie dla kelnera obsłużenia stolika tableID
	 * i dostarczenie do niego zlecenia orderID.
	 * 
	 * @param orderID numer zlecenia (posiłku)
	 * @param tableID numer stolika, do którego posiłek jest dostarczany
	 */
	public void go( int orderID, int tableID );
	
	/**
	 * Rejestracja obiektu obsługującego przepływ zamówień klientów.
 	 * @param orderInterface obiekt obsługujący zamówienia klientów
	 */
	public void registerOrder( OrderInterface orderInterface );
}
