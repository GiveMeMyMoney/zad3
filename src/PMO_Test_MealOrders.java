import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

public class PMO_Test_MealOrders implements PMO_LogSource {
    /**
     * Okres uśpienia generatora zleceń
     */
    private final static long ORDERS_CREATOR_SLEEP_TIME = 15;
    /**
     * Początkowe wartości prawdopodobieństwa wygnerowania zlecenia
     * dla danej liczby nieukończonych zleceń w systemie
     */
    private final static double[] ORDER_CREATION_PROBABILITY_INIT = {
            1.0, 1.0, 0.99, 0.99, 0.98,
            0.97, 0.96, 0.95, 0.92, 0.88,
            0.84, 0.83, 0.8, 0.75, 0.7,
            0.6, 0,55, 0.5, 0.45, 0.4, 0.35, 0.3
    };
    /**
     * Prawdopodobieństwo natychmiastowego wygenerowania nowego zlecenia
     */
    private final static double NEXT_ORDER_PROBABILITY = 0.5;
    private final static long PROBABILITY_TO_LONG = 2000000000;
    private final static double PROBABILITY_DECREASE = 0.99;
    private final static double LONG_TO_PROBABILITY = 1.0 / PROBABILITY_TO_LONG;
    private final static AtomicLongArray orderCreationProbability =
            new AtomicLongArray( ORDER_CREATION_PROBABILITY_INIT.length );
    private final static long PROBABILITY_DECREASE_SLEEP_TIME = 1000;
    /**
     * Flaga kontynuacji prac - gdy zostanie ustawiona na false
     * nie będą już generowane nowe zlecenia.
     */
    private AtomicBoolean continuationFlag;

    /**
     * Mapa przechowująca informacje o powiązaniu zlecenia z stolikiem
     */
    private final static Map<Integer, Integer> order2table = new ConcurrentHashMap<>();
    /**
     * Mapa przechowująca informację o wszystkich wygenerowanych zamowieniach.
     */
    private final static Map<Integer, PMO_Test_MealOrder> id2order = new ConcurrentHashMap<>();

    private Random rnd = ThreadLocalRandom.current();

    /**
     * Zbiór identyfikatorów posiłków, których obsługa jeszcze nie została
     * zakończona.
     */
    private final Set<Integer> unfinishedOrders = Collections.synchronizedSet(new HashSet<>());

    /**
     * Liczba ukończonych zleceń.
     */
    private final AtomicInteger finishedOrders = new AtomicInteger(0);

    /**
     * Kolejka zamówień, które są nowe (tj. jeszcze nie trafiły do kelnerów)
     */
    private final Queue<PMO_Test_MealOrder> newOrders = new ConcurrentLinkedDeque<>();

    /**
     * Liczba zamówień, które maja trafić do kelnerów.
     * Inkrementacja w momencie utworzenia, dekrementacja (synchronized) w momencie
     * przekazania kelnerowi. Gdy spada do zera ->
     * synchroniczne wygenerowanie nowego zamówienia do kolejki.
     */
    private final AtomicInteger ordersBeforeInDeliveryState = new AtomicInteger(0);

    static {
        for ( int i = 0; i < ORDER_CREATION_PROBABILITY_INIT.length; i++ ) {
            orderCreationProbability.set(i,
                    (long)( ORDER_CREATION_PROBABILITY_INIT[i] * PROBABILITY_TO_LONG ));
        }
    }

    public void setContinuationFlag( AtomicBoolean continuationFlag ) {
        this.continuationFlag = continuationFlag;
    }

    public void startOrderProbabilityDecreasingThread() {

        class OrderProbabilityDecreasingWorker implements  Runnable {
            @Override
            public void run() {
                log( "Uruchomiono wątek odpowiedzialny za spadek prawdopodobieństwa nowego zlecenia");
                double value;
                while ( continuationFlag.get() ) {
                    for ( int i = 0; i < ORDER_CREATION_PROBABILITY_INIT.length; i++ ) {
                        value = orderCreationProbability.get(i)*LONG_TO_PROBABILITY;
                        value *=PROBABILITY_DECREASE;
                        orderCreationProbability.set(i,
                                (long)( value * PROBABILITY_TO_LONG ));
                    }
                    PMO_TimeHelper.sleep(PROBABILITY_DECREASE_SLEEP_TIME);
                }
                log( "Koniec wątku odpowiedzialnego za spadek prawdopodobieństwa nowego zlecenia");
            }
        }

        Thread th = PMO_ThreadsHelper.createThreadAndRegister( new OrderProbabilityDecreasingWorker() );
        th.start();
    }

    public void startOrdersCreatorThread() {
        class OrdersCreatorWorker implements Runnable {

            final Random rnd = ThreadLocalRandom.current();

            @Override
            public void run() {
                assert continuationFlag != null;

                log("Watek generujący zlecenia uruchomiony");
                double probability;
                int size;
                while (continuationFlag.get()) {

                    // uwzględniamy liczbę zleceń w systemie
                    size = unfinishedOrders.size();

                    if (size < ORDER_CREATION_PROBABILITY_INIT.length )  {
                        probability = orderCreationProbability.get(size) * LONG_TO_PROBABILITY;
                    } else {
                        probability = orderCreationProbability.get(
                                ORDER_CREATION_PROBABILITY_INIT.length - 1) * LONG_TO_PROBABILITY;
                    }

//                    log( "Liczba niezakończonych zleceń " + size +
//                            " prawdopodobieństwo nowego zamówienia " + probability );

                    if (rnd.nextDouble() < probability) {
                        do {
                            createNewOrder();
                        } while ( rnd.nextDouble() < NEXT_ORDER_PROBABILITY );
                    }

                    PMO_TimeHelper.sleep(ORDERS_CREATOR_SLEEP_TIME);

                }
                log("Watek generujący zlecenia zakończony");
            }
        }

        Thread th = PMO_ThreadsHelper.createThreadAndRegister(new OrdersCreatorWorker());
        th.start();
    }

    public boolean testOrder(int orderID, int tableID) {
        if (!orderExists(orderID)) {
            error("Test zlecenia " + orderID + " niemożliwy; ID nieznane.");
            return false;
        }
        Optional<PMO_Test_MealOrder> mealOrder = getMealOrder(orderID);

        if (mealOrder.get().getTableId() != tableID) {
            error("Zlenienie " + mealOrder.get() + " powinno trafić do innego stołu. Oczekiwano " +
                    mealOrder.get().getOrderID() + " , jest " + tableID);
            return false;
        }
        return true;
    }

    public boolean stateChange(int orderID, PMO_Test_MealOrder.MealState newState) {
        if (!orderExists(orderID)) {
            error("Zmiana stanu zlecenia " + orderID + " niemożliwa; ID nieznane.");
            return false;
        }

        Optional<PMO_Test_MealOrder> mealOrder = getMealOrder(orderID);

        /*
         * Ratunek w przypadku otrzymania ostatniego
         * aktywnego zlecenia przez kelnera.
         */
        if ( newState == PMO_Test_MealOrder.MealState.IN_DELIVERY ) {
            if ( ( newOrders.size() == 0 ) && ( continuationFlag.get() ) ) {
               int obids = ordersBeforeInDeliveryState.decrementAndGet();
               if (obids == 0 ) {
                   String ordersState = toString();
                   IntStream.range(0,PMO_Test_Consts.NUMBER_OF_ORDERS_GENERATE_IN_EMERGENCY).
                           forEach( (i)->createNewOrder());
                   log( "Przy stanie zleceń: " + ordersState +
                           " awaryjnie wygenerowano zlecenia");
               }
            }
        }

        if (newState == PMO_Test_MealOrder.MealState.DONE) {
            // koniec tego zamówienia
            unfinishedOrders.remove(mealOrder.get());
            int fo = finishedOrders.incrementAndGet();
            log("Obsługa " + mealOrder.get() + " zakończona jako " + fo + " posiłek");
        }

        return mealOrder.get().stateChange(newState);
    }

    public boolean orderExists(int orderID) {
        return id2order.containsKey(orderID);
    }

    public int getFinishedOrders() {
        return finishedOrders.get();
    }

    public Optional<PMO_Test_MealOrder> getMealOrder(int orderID) {
        return Optional.ofNullable(id2order.get(orderID));
    }

    public PMO_Test_MealOrder tryOrderAcquire() {
        return newOrders.poll();
    }

    public void createNewOrder() {
        PMO_Test_MealOrder order = new PMO_Test_MealOrder();

        log("Utworzono zamówienie " + order);

        order2table.put(order.getOrderID(), order.getTableId());
        id2order.put(order.getOrderID(), order);
        newOrders.add(order);
        unfinishedOrders.add(order.getOrderID());
        ordersBeforeInDeliveryState.incrementAndGet();
    }

    @Override
    public String toString() {
        return "MealOrders{queue=" + newOrders.size() + ", unfinished=" + unfinishedOrders.size() +
                ", beforeInDelivery=" + ordersBeforeInDeliveryState.get() + ", finished=" + finishedOrders.get()
                + "}";
    }

    public static void main(String[] args) {
        PMO_Test_MealOrders o = new PMO_Test_MealOrders();
        AtomicBoolean flag = new AtomicBoolean(true);

 //       o.startOrdersCreatorThread(flag);

        System.out.println("Sleep");
        PMO_TimeHelper.sleep(20000);
        PMO_Log.showLog();
    }

}
