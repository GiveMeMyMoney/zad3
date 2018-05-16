import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PMO_Test_Kitchen implements PMO_LogSource, KitchenInterface, PMO_Testable {

    private final PMO_AtomicCounter parallelPreparationsMax =
            PMO_CountersFactory.createCommonMaxStorageCounter();
    private final PMO_AtomicCounter parallelPreparations =
            PMO_CountersFactory.createCounterWithMaxStorageSet();
    private ReceiverInterface receiverInterface;
    private final AtomicBoolean stateOK = new AtomicBoolean( true );
    private final Semaphore parallelReadySemaphore;
    private final CyclicBarrier parallelReadyBarrier;
    private final PMO_Test_MealOrders mealOrders;
    private PMO_AtomicCounter parallelWaitersAndKitchen;
    private final AtomicInteger mealsBuffer = new AtomicInteger(0);
    private final AtomicInteger activeWaiters;
    private final Random rnd = new Random();

    public PMO_Test_Kitchen( PMO_Test_MealOrders mealOrders, AtomicInteger activeWaiters ) {
        this.mealOrders = mealOrders;
        this.activeWaiters = activeWaiters;
        this.parallelReadyBarrier = new CyclicBarrier( PMO_Test_Consts.SIMULTANEOUS_READY, () -> {
            log( "KUCHNIA> Przygotowana jest grupa zamówień");
            PMO_TimeHelper.sleep( PMO_Test_Consts.KITCHEN_MEAL_PREPARATION_MIN_TIME );
            log( "KUCHNIA> Grupa zamówień przed ready");
        } );
        this.parallelReadySemaphore = new Semaphore( PMO_Test_Consts.SIMULTANEOUS_READY );
    }

    public void setParallelWaitersAndKitchen( PMO_AtomicCounter parallelWaitersAndKitchen ) {
        this.parallelWaitersAndKitchen = parallelWaitersAndKitchen;
    }

    public AtomicInteger getMealsBuffer( ) {
        return mealsBuffer;
    }

    private void logErrorAndChangeState( String txt ) {
        stateOK.set( false );
        PMO_LogSource.errorS( txt );
    }

    @Override
    public int getNumberOfParallelTasks() {
        return PMO_Test_Consts.KITCHEN_PARALLEL_TASKS;
    }

    private class KitchenWorker implements Runnable {
        private final int orderID;

        public KitchenWorker( int orderID ) {
            this.orderID = orderID;
        }

        @Override
        public void run() {
            boolean parallelReady = parallelReadySemaphore.tryAcquire();

            if ( parallelReady ) {
                log( "KUCHNIA> Informacja o posiłku " + orderID + " moze zostac zwrocona współbieżnie");

                PMO_ThreadsHelper.wait(parallelReadyBarrier, PMO_Test_Consts.CYCLIC_BARRIER_TIMEOUT );

                log( "KUCHNIA> Informacja o posiłku " + orderID + " za chwilę zostanie zwrocona współbieżnie");

            } else {
                long time2prepare = PMO_Test_Consts.KITCHEN_MEAL_PREPARATION_MIN_TIME +
                    rnd.nextInt( (int)PMO_Test_Consts.KITCHEN_MEAL_PREPARATION_DELTA_TIME );

                log( "KUCHNIA> Przygotowanie posiłku " + orderID + " wymaga " + time2prepare + "msec czasu");

                PMO_TimeHelper.sleep( time2prepare );
            }

            mealOrders.stateChange( orderID, PMO_Test_MealOrder.MealState.BEFORE_READY);

            log( "KUCHNIA> Za chwilę potwierdzenie przygotowanie posiłku " + orderID );

            parallelPreparations.dec();
            parallelWaitersAndKitchen.dec();

            PMO_TestHelper.nonBlockingExecute( "receiverInterface.mealReady", () -> {
                log( "KUCHNIA> OrderID " + orderID + " przed mealReady");
                receiverInterface.mealReady(orderID);
                log( "KUCHNIA> OrderID " + orderID + " po mealReady");
            });

            if ( parallelReady ) {
                parallelReadySemaphore.release();
            }
        }
    }

    @Override
    public void prepare(int orderID) {
        int pp = parallelPreparations.incAndStoreMax();
        parallelWaitersAndKitchen.incAndStoreMax();

        log( "KUCHNIA> Zlecono przygotowanie posiłku " + orderID +
                " Jednocześnie w przygotowaniu " + pp );

        mealOrders.stateChange( orderID, PMO_Test_MealOrder.MealState.UNDER_PREPARATION );

        if ( pp > getNumberOfParallelTasks() ) {
            logErrorAndChangeState( "Przekroczono maksymalną liczbę zadań, które kuchnia może jednocześnie realizować");
        }

        int buffer = mealsBuffer.incrementAndGet();
        int waiters = activeWaiters.get();

        if ( buffer > waiters ) {
            logErrorAndChangeState( "Mamy " + waiters + " aktywnych kelnerów, zlecono przygotowanie " + buffer + " posiłku na zapas");
        }

        if ( receiverInterface == null ) {
            logErrorAndChangeState( "Zlecono prepare, ale nie ustawiono receiverInterface");
            PMO_CommonErrorLog.criticalMistake();
        }

        PMO_ThreadsHelper.createThreadAndRegister( new KitchenWorker( orderID  )).start();
    }

    @Override
    public void registerReceiver(ReceiverInterface receiverInterface) {
        this.receiverInterface = receiverInterface;
    }

    @Override
    public boolean testOK() {
        if ( parallelPreparationsMax.get() < PMO_Test_Consts.KITCHEN_PARALLEL_TASKS_EXPECTED  ) {
            error( "Oczekiwano lepszego uzycia kuchni. Było " + parallelPreparationsMax.get() +
                    " limit " + PMO_Test_Consts.KITCHEN_PARALLEL_TASKS_EXPECTED );
            return false;
        }
        return stateOK.get();
    }
}
