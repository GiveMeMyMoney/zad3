
public interface OrderInterface {
	/**
	 * Informacja o nowym zleceniu. Kelner przekazuje informację
	 * o numerze zlecenia oraz numerze stolika, przy którym złożono
	 * zamówienie.
	 * @param orderID unikalny w skali systemu numer zlecenia klienta.
	 * @param tableID numer stolika, który złożył zamówienie
	 */
	public void newOrder( int orderID, int tableID );
	
	/**
	 * Potwierdzenie dostarczenia zamównienia do stolika.
	 * @param orderID numer zamównienia, które zostało dostarczone
	 * @param tableID numer stolika, który został obsłużony
	 */
	public void orderComplete( int orderID, int tableID );
}
