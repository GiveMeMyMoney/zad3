
public interface RestaurantManagementInterface {
	/**
	 * Kelner rozpoczyna prace - można wysłać go z posiłkiem.
	 * 
	 * @param waiter implementacja interfejsu kelnera
	 */
	public void addWaiter( WaiterInterface waiter );
	
	/**
	 * Kelner kończy pracę - po zakończeniu tej metody nie wolno
	 * już zlecać mu prac do wykonania.
	 * @param waiter obiekt-kelner, który przestaje 
	 * obsługiwać klientów.
	 */
	public void removeWaiter( WaiterInterface waiter );
	
	/**
	 * Ustalany jest dostęp do kuchni. 
	 * 
	 * @param kitchen referencja do obiektu implementującego
	 * KitchenInterface.
	 */
	public void setKitchen( KitchenInterface kitchen );
}
