import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class PMO_Test_MealOrder implements PMO_LogSource {
    private final static int MAX_TABLE_ID = 1000;

    /**
     * Licznik zamównień.
     */
    private final static AtomicInteger counter = new AtomicInteger(0);
    /**
     * Zbiór aktualnie używanych ID stolików
     */
    private final static Set<Integer> tableIDsSet = Collections.synchronizedSet(new HashSet<>());

    private final int orderID;
    private final int tableID;

    /**
     * Historia zmiany stanu zamówienia.
     */
    private final Map<MealState, Long> state2time = new ConcurrentHashMap<>();

    /**
     * Aktualny stan zamówienia posiłku.
     * Uwaga: musi być chroniony przed jednoczesnymi modyfikacjami/odczytami.
     */
    private MealState currentState;
    private Random rnd = ThreadLocalRandom.current();

    public enum MealState {
        /**
         * Nowoutworzone zamownienie
         */
        NEW,
        /**
         * U kelnera przed przekazaniem do systemu
         */
        BEFORE_ORDER,
        /**
         * W trakcie przetwarzania przez kuchnie
         */
        UNDER_PREPARATION,
        /**
         * Kuchnia zakończyła przygotowanie posiłku, za chwilę
         * potwierdzi to za pomocą (mealReady)
         */
        BEFORE_READY,
        /**
         * Przakazane kelnerowi do dostarczenia
         */
        IN_DELIVERY,
        /**
         * Tuż przed potwierdzeniem dostarcznia posiłku do stolika
         */
        BEFORE_COMPLETE,
        /**
         * Obsługa zakończona
         */
        DONE;
    }

    {
        orderID = counter.getAndIncrement();
        int tableID;
        do {
            tableID = rnd.nextInt(MAX_TABLE_ID);
        } while (tableIDsSet.add(tableID));
        this.tableID = tableID;

        currentState = MealState.NEW;
        state2time.put(currentState, PMO_TimeHelper.getTimeFromStart());
    }

    public int getOrderID() {
        return orderID;
    }

    public int getTableId() {
        return tableID;
    }

    public boolean stateChange(MealState newState) {
        synchronized (this) {
            if (newState == currentState) {
                error("Powielenie! " + orderID + " Już jest w stanie " + newState);
                return false;
            }
            if (newState.ordinal() != currentState.ordinal() + 1) {
                error("Błąd kolejności stanów zlecenia. Po " + currentState + " nie " + newState);
                return false;
            }
            currentState = newState;
        }

        long t = PMO_TimeHelper.getTimeFromStart();
        state2time.put(newState, t);
        log("Posiłek " + orderID + " zmienił stan na " + newState + "@" + t);

        if ( newState == MealState.DONE ) {
            tableIDsSet.remove( orderID );
            log( "Historia posiłku " + orderID + " " + createHistory() );
        }

        return true;
    }

    private String createHistory() {
        StringBuilder sb = new StringBuilder();

        sb.append( "Order {id=" );
        sb.append( orderID );
        Stream.of(MealState.values()).forEach(
                (ms)-> {
                    sb.append( ",");
                    sb.append(ms.name());
                    sb.append("@");
                    sb.append(state2time.get(ms));
                } );
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "PMO_Test_MealOrder{orderID=" + orderID +
                ", currentState=" + currentState +
                '}';
    }
}
