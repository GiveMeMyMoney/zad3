import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class PMO_Test_Waiter implements PMO_LogSource, WaiterInterface, PMO_Testable {

    private static final AtomicInteger counter = new AtomicInteger(0);
    private final int id;
    private OrderInterface orderInterface;
    private final RestaurantManagementInterface management;
    private final AtomicBoolean stateOK = new AtomicBoolean( false );

    /**
     * Czy kelner jest gotowy na przyjęcie zlecenia?
     */
    private final AtomicBoolean waiterReady = new AtomicBoolean(false);

    /**
     * Dzieki semaforowi czesc kelenerow bedzie zglaszac nowe zamowienia
     * w tym samym czasie. Semafor ma mniej pozwoleń niż jest w systemie kelnerów.
     */
    private Semaphore parallelOrderSemaphore;
    private CyclicBarrier parallelOrderBarrier;

    /**
     * Ten semafor odpowiada za równoczesne potwierdzanie dostawy
     * posiłku do stolika
     */
    private Semaphore parallelConfirmationSemaphore;
    private CyclicBarrier parallelConfirmationBarrier;

    /**
     * Semafor dopowiada za zmienną liczbe kelnerów.
     */
    private Semaphore floatingWaitersSemaphore;

    /**
     * Zliczanie ilości jednocześnie używanych kelnerów.
     */
    private PMO_AtomicCounter parallelWaitersUsage;

    /**
     * Zliczanie ilości jednocześnie używanych kelnerów i
     * przygotowywanych w kuchni dań.
     */
    private PMO_AtomicCounter parallelWaitersAndKitchenUsage;

    /**
     * Liczba aktywnych kelnerów - to dozwolony rozmiar
     * bufora dla kuchni
     */
    private final AtomicInteger activeWaiters;

    /**
     * Liczba posiłków przygotowanych przez kuchnię, a jeszcze nie dostarczonych
     * przez kelnerów.
     */
    private AtomicInteger mealsBuffer;

    private final PMO_Test_MealOrders mealOrders;
    /**
     * Lista zamówień do przekazania - lista nie ma być używania współbieżnie
     */
    private final List<PMO_Test_MealOrder> mealOrdersList = new ArrayList<>();
    private final CyclicBarrier initialOrdersBarrier;

    private final Random rnd = new Random();

    public PMO_Test_Waiter(RestaurantManagementInterface management,
                           PMO_Test_MealOrders mealOrders,
                           AtomicInteger activeWaiters,
                           CyclicBarrier initialOrdersBarrier) {
        this.mealOrders = mealOrders;
        this.management = management;
        this.activeWaiters = activeWaiters;
        this.initialOrdersBarrier = initialOrdersBarrier;
        id = counter.getAndIncrement();
        log("Utworzono obiekt " + toString());

        getInitialOrders();
    }

    private void getInitialOrders() {
        IntStream.range(0,PMO_Test_Consts.INITIAL_ORDERS_PER_WAITER ).
                forEach( i -> mealOrdersList.add( mealOrders.tryOrderAcquire()));
        mealOrdersList.forEach( o -> mealOrders.stateChange( o.getOrderID(), PMO_Test_MealOrder.MealState.BEFORE_ORDER));
    }

    public void setMealsBuffer( AtomicInteger mealsBuffer ) {
        this.mealsBuffer = mealsBuffer;
    }

    public void setParallelOrder( Semaphore parallelOrderSemaphore, CyclicBarrier parallelOrderBarrier ) {
        this.parallelOrderSemaphore = parallelOrderSemaphore;
        this.parallelOrderBarrier = parallelOrderBarrier;
    }

    public void setParallelConfirmation( Semaphore parallelConfirmationSemaphore,
                                         CyclicBarrier parallelConfirmationBarrier ) {
        this.parallelConfirmationSemaphore = parallelConfirmationSemaphore;
        this.parallelConfirmationBarrier = parallelConfirmationBarrier;
    }

    public void setFloatingWaitersSemaphore( Semaphore floatingWaitersSemaphore ) {
        this.floatingWaitersSemaphore = floatingWaitersSemaphore;
    }

    public void setParallelUsageCounters( PMO_AtomicCounter parallelWaitersUsage,
                                       PMO_AtomicCounter parallelWaitersAndKitchenUsage) {
        this.parallelWaitersUsage = parallelWaitersUsage;
        this.parallelWaitersAndKitchenUsage = parallelWaitersAndKitchenUsage;
    }

    public void initWaiter( CyclicBarrier addWaitersSynchronization ) {

        assert parallelWaitersUsage != null;
        assert parallelWaitersAndKitchenUsage != null;
        assert parallelOrderBarrier != null;
        assert parallelOrderSemaphore != null;
        assert parallelConfirmationBarrier != null;
        assert parallelConfirmationSemaphore != null;
        assert parallelWaitersAndKitchenUsage != null;
        assert floatingWaitersSemaphore != null;
        assert activeWaiters != null;
        assert initialOrdersBarrier != null;
        assert mealsBuffer != null;

        boolean assertsOn = false;
        assert assertsOn = true;

        if ( assertsOn ) {
            log( "Obiekt " + this + " przeszedł test asercji" );
        }

        log( "Kelner " + this + " zaraz przyjdzie do pracy.");
        PMO_ThreadsHelper.createThreadAndRegister( () -> {
            PMO_ThreadsHelper.wait( addWaitersSynchronization );
            addWaiter();
            PMO_ThreadsHelper.wait( addWaitersSynchronization );
        }).start();
    }

    private void addWaiter() {
        waiterReady.set(true);
        activeWaiters.incrementAndGet();
        PMO_TestHelper.execute("addWaiter", () -> {
            management.addWaiter(this);
        });
        log("Kelner " + this + " przyszedł do pracy");
    }

    @Override
    public int getID() {
        return id;
    }

    private boolean isOrderInterfaceSet() {
        if (orderInterface == null) {
            logErrorAndChangeState("Kelner " + PMO_Test_Waiter.this + " brak ustawionego orderInterface");
            return false;
        }
        return true;
    }

    private class WaiterWorker implements Runnable, PMO_LogSource {

        final int orderID;
        final int tableID;

        WaiterWorker(int orderID, int tableID) {
            this.orderID = orderID;
            this.tableID = tableID;
        }

        void addWaiterAgain( final long delay ) {
            Runnable code = () -> {
                log( "Kelner " + PMO_Test_Waiter.this + " powroci za " + delay );
                PMO_TimeHelper.sleep( delay );
                addWaiter();
                floatingWaitersSemaphore.release();
            };
            Thread th = PMO_ThreadsHelper.createThreadAndRegister( code );
            th.start();
        }

        @Override
        public void run() {

            boolean parallelOrder = parallelOrderSemaphore.tryAcquire();

            mealOrdersList.clear();
            PMO_Test_MealOrder order;

            do {
                order = mealOrders.tryOrderAcquire();
                if ( order != null ) {
                    mealOrdersList.add( order );
                    mealOrders.stateChange( order.getOrderID(), PMO_Test_MealOrder.MealState.BEFORE_ORDER );
                }
                else {
                    log( PMO_Test_Waiter.this.toString() + " nie znalazł zlecenia!!!");
                    break;
                }
            } while ( rnd.nextDouble() < PMO_Test_Consts.WAITER_BULK_ORDERS_PROBABILITY );

            log( PMO_Test_Waiter.this.toString() + " liczba zleceń do przekazania " + mealOrdersList.size() );

            if ( parallelOrder ) {
                log( "Uzyskano pozwolenie na parallelOrder " + PMO_Test_Waiter.this.toString() );
                PMO_ThreadsHelper.wait( parallelOrderBarrier, PMO_Test_Consts.CYCLIC_BARRIER_TIMEOUT );
            } else {
                long delay = PMO_Test_Consts.ORDER_COMPLETE_MIN_TIME +
                        rnd.nextInt( PMO_Test_Consts.ORDER_COMPLETE_MAX_MULT ) * PMO_Test_Consts.ORDER_COMPLETE_DELTA_TIME;
                PMO_TimeHelper.sleep( delay );
            }

            sendOrders();

            if ( parallelOrder ) {
                parallelOrderSemaphore.release();
                log( "Oddano pozwolenie na parallelOrder " + PMO_Test_Waiter.this.toString() );
            }

            boolean floating = floatingWaitersSemaphore.tryAcquire();

            long restTime = 0;
            if ( floating ) {

                if ( rnd.nextDouble() > PMO_Test_Consts.FLOATING_WAITER_PROBABILITY ) {
                    // rezygnacja z odpoczynku
                    floatingWaitersSemaphore.release();
                    floating = false;
                } else {
                    restTime = PMO_Test_Consts.FLOATING_WAITER_MIN_REST_TIME +
                            rnd.nextInt( (int) PMO_Test_Consts.FLOATING_WAITER_DELTA_REST_TIME );

                    log( "Wylosowano odpoczynek przez " + restTime + " " + toString() );

                    boolean removeOK = PMO_TestHelper.execute("removeWaiter",
                            () -> management.removeWaiter( PMO_Test_Waiter.this ) );
                    if ( removeOK ) {
                        activeWaiters.decrementAndGet();
                        log( "Kelner " + PMO_Test_Waiter.this.toString() + " zgłosił removeWaiter, aktywnych kelnerów "
                                + activeWaiters.get() );
                    } else {
                        logErrorAndChangeState( "Błąd w trakcie przejścia kelnera w stan nieaktywności (removeWaiter)");
                    }
                }
            }

            boolean parallelConfirmation = parallelConfirmationSemaphore.tryAcquire();
            mealOrders.stateChange( orderID, PMO_Test_MealOrder.MealState.BEFORE_COMPLETE );

            if ( parallelConfirmation ) {
                log( "Uzyskano pozwolenie na parallelConfirmation " + PMO_Test_Waiter.this );

                PMO_ThreadsHelper.wait( parallelConfirmationBarrier, PMO_Test_Consts.CYCLIC_BARRIER_TIMEOUT );
            }

            if ( !  waiterReady.compareAndSet( false, true ) ) {
                logErrorAndChangeState( "Zmiana stanu " + PMO_Test_Waiter.this +
                        " na gotowy do pracy zakończona błędem");
            }

            mealOrders.stateChange( orderID, PMO_Test_MealOrder.MealState.DONE );

            PMO_TestHelper.nonBlockingExecute( "orderInterface.orderComplete", () -> {
                orderInterface.orderComplete(orderID,tableID);
                log( "Kelner " + PMO_Test_Waiter.this + " potwierdził dostarczenie posiłku " + orderID );
            });

            parallelWaitersUsage.dec();
            parallelWaitersAndKitchenUsage.dec();

            if ( parallelConfirmation ) {
                parallelConfirmationSemaphore.release();
            }

            if ( floating ) {
                addWaiterAgain( restTime );
            }
        }
    }

    @Override
    public void go(int orderID, int tableID) {

        boolean setOK = waiterReady.compareAndSet(true, false);
        mealsBuffer.decrementAndGet(); // posiłek zdjęty z bufora

        log("Posiłek " + orderID + " do stolika " + tableID + " przekazany kelnerowi " + this);

        if (!isOrderInterfaceSet()) {
            return;
        }

        if (!setOK) {
            logErrorAndChangeState("Kelnerowi " + this + " przekazano zadanie " + orderID + " choć nie był na to gotowy");
            return;
        }

        if (!mealOrders.stateChange(orderID, PMO_Test_MealOrder.MealState.IN_DELIVERY)) {
            return;
        }

        if (!mealOrders.testOrder(orderID, tableID)) {
            logErrorAndChangeState("Kelner " + this + " nie może obsłużyć zlenienia " + orderID + " do " + tableID);
            return;
        }

        parallelWaitersUsage.incAndStoreMax();
        parallelWaitersAndKitchenUsage.incAndStoreMax();

        Thread th = PMO_ThreadsHelper.createThreadAndRegister(new WaiterWorker(orderID, tableID));
        th.start();
    }

    private void logErrorAndChangeState( String txt ) {
        stateOK.set( false );
        PMO_LogSource.errorS( txt );
    }

    private void sendOrders() {
        if ( mealOrdersList.size() == 0 ) return;

        mealOrdersList.stream().forEach( o -> PMO_TestHelper.nonBlockingExecute( "newOrder",
                () -> {
                    orderInterface.newOrder(o.getOrderID(), o.getTableId());
                }
        ) );
        log( "Kelner " + this + " uruchomił przekazanie " + mealOrdersList.size() + " zamówień");
    }

    @Override
    public void registerOrder(OrderInterface orderInterface) {
        boolean first = this.orderInterface == null;
        this.orderInterface = orderInterface;
        if (first) {
            log("Pierwsza rejestracja obiektu orderInterface");
            PMO_ThreadsHelper.wait(initialOrdersBarrier);
            sendOrders();
            stateOK.set( true );
        }
    }

    @Override
    public String toString() {
        return "PMO_Test_Waiter{" +
                "id=" + id +
                '}';
    }

    @Override
    public boolean testOK() {
        return stateOK.get();
    }
}
