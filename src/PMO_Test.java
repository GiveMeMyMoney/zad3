import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class PMO_Test implements PMO_RunTestTimeout, PMO_LogSource {

    private final RestaurantManagementInterface managementInterface;
    private final PMO_Test_MealOrders mealOrders = new PMO_Test_MealOrders();
    private final AtomicInteger activeWaiters = new AtomicInteger(0);
    private final PMO_Test_Kitchen kitchen = new PMO_Test_Kitchen(mealOrders, activeWaiters );
    private final List<PMO_Test_Waiter> waiters = new ArrayList<>();
    private final Semaphore floatingWaitersSemaphore = new Semaphore( PMO_Test_Consts.FLOATING_WAITERS );
    private final AtomicInteger mealsBuffer;
    private final Semaphore parallelConfirmationSemaphore = new Semaphore( PMO_Test_Consts.SIMULTANEOUS_CONFIRM );
    private final CyclicBarrier parallelConfirmationBarrier;
    private final Semaphore parallelOrderSemaphore = new Semaphore( PMO_Test_Consts.SIMULTANEOUS_ORDER );
    private final CyclicBarrier parallelOrderBarrier;
    private final PMO_AtomicCounter parallelWaitersAndKitchenUsageMax = PMO_CountersFactory.createCommonMaxStorageCounter();
    private final PMO_AtomicCounter parallelWaitersAndKitchenUsage = PMO_CountersFactory.createCounterWithMaxStorageSet();
    private final PMO_AtomicCounter parallelWaitersUsageMax = PMO_CountersFactory.createCommonMaxStorageCounter();
    private final PMO_AtomicCounter parallelWaitersUsage = PMO_CountersFactory.createCounterWithMaxStorageSet();
    private final AtomicBoolean continuationFlag = new AtomicBoolean( true  );
    private final AtomicBoolean runDone = new AtomicBoolean( false );

    public PMO_Test() {
        managementInterface =
                (RestaurantManagementInterface)PMO_GeneralPurposeFabric.fabric("RestaurantManagement",
                        "RestaurantManagementInterface");
        mealsBuffer = kitchen.getMealsBuffer();

        parallelConfirmationBarrier = new CyclicBarrier( PMO_Test_Consts.SIMULTANEOUS_CONFIRM,
                () -> {
                    log( "Kelnerzy przed jednoczesnym potwierdzeniem dostarczenia posiłku");
                } );

        parallelOrderBarrier = new CyclicBarrier( PMO_Test_Consts.SIMULTANEOUS_ORDER,
                () -> {
                    log( "Kelnerzy wspólnie oczekuja przed dostarczeniem nowych zamówień");
                    PMO_TimeHelper.sleep( PMO_Test_Consts.ORDER_COMPLETE_MIN_TIME );
                    log( "Kelnerzy tuż przed dostarczeniem nowych zamówień");
                });

        mealOrders.setContinuationFlag( continuationFlag );
        mealOrders.startOrdersCreatorThread();
        mealOrders.startOrderProbabilityDecreasingThread();
    }

    protected void setKitchen() {
        kitchen.setParallelWaitersAndKitchen( parallelWaitersAndKitchenUsage );
        PMO_TestHelper.execute( "setKichen", () -> {
            managementInterface.setKitchen( kitchen );
            log( "Dodano obiekt reprezentujący kuchnię");
        });
    }

    protected void prepareWaiter( PMO_Test_Waiter waiter ) {
        waiter.setFloatingWaitersSemaphore( floatingWaitersSemaphore );
        waiter.setMealsBuffer( mealsBuffer );
        waiter.setParallelConfirmation( parallelConfirmationSemaphore, parallelConfirmationBarrier );
        waiter.setParallelOrder( parallelOrderSemaphore, parallelOrderBarrier );
        waiter.setParallelUsageCounters( parallelWaitersUsage, parallelWaitersAndKitchenUsage );
    }

    protected void createAndPrepareWaiters() {
        CyclicBarrier initialBarrier = new CyclicBarrier( PMO_Test_Consts.WAITERS, () -> {
               log( "Wszyscy kelnerzy dotarli do bariery synchronizującej wysyłanie początkowych zamówień.");
            });
        IntStream.range(0, PMO_Test_Consts.WAITERS ).forEach( i -> {
            waiters.add( new PMO_Test_Waiter(managementInterface,mealOrders,activeWaiters, initialBarrier ));
        });
        waiters.forEach( this::prepareWaiter );
    }

    protected void initializeWaiters() {
        CyclicBarrier addWaitersBarrier = new CyclicBarrier( PMO_Test_Consts.WAITERS + 1, () -> {
            log( "Wszyscy kelnerzy dotarli do bariery synchronizującej wykonanie addWaiter" );
        });
        waiters.forEach( w -> w.initWaiter( addWaitersBarrier ));

        PMO_ThreadsHelper.wait( addWaitersBarrier );
        PMO_ThreadsHelper.wait( addWaitersBarrier ); // oczekiwanie na zakończenie addWaiter
    }

    protected void prepareInitialOrders() {
        IntStream.range(0, PMO_Test_Consts.WAITERS * PMO_Test_Consts.INITIAL_ORDERS_PER_WAITER ).forEach(
                (i) -> mealOrders.createNewOrder()
        );
    }

    @Override
    public long getRequiredTime() {
        return 15000;
    }

    @Override
    public boolean testOK() {

        if ( ! runDone.get() ) {
            error( "Metoda run nie została zakończona przed uruchomieniem testu");
            return false;
        }

        boolean result = true;

        result &= kitchen.testOK();

        result &= waiters.stream().allMatch( e -> e.testOK() );

        int usage = parallelWaitersAndKitchenUsageMax.get();

        if ( usage < PMO_Test_Consts.KITCHEN_AND_WAITERS_PARALLEL_TASKS_EXPECTED ) {
            error( "Oczekiwano większego użycia dostępnych zasobów, jest " + usage +
            " limit " + PMO_Test_Consts.KITCHEN_AND_WAITERS_PARALLEL_TASKS_EXPECTED );
            result = false;
        } else {
            PMO_SystemOutRedirect.println( "Uzycie dostępnych zasobów kuchni i kelnerów " + usage );
        }

        int waiters = parallelWaitersUsageMax.get();
        if ( waiters >= PMO_Test_Consts.WAITERS_PARALLEL_TASKS_EXPECTED ) {
            PMO_SystemOutRedirect.println( "Jednocześnie pracowało " + waiters + " kelnerów");
        } else {
            PMO_SystemOutRedirect.println( "Jednocześnie pracowało tylko " + waiters + " kelnerów. Oczekiwano " +
                    PMO_Test_Consts.WAITERS_PARALLEL_TASKS_EXPECTED );
            result = false;
        }

        int finished = mealOrders.getFinishedOrders();
        if ( finished >= PMO_Test_Consts.ORDERS_DONE_EXPECTED ) {
            PMO_SystemOutRedirect.println( "Wydano klientom " + finished + " zamówień");
        } else {
            PMO_SystemOutRedirect.println( "Klientom wydano tylko " + finished + " zamówień. Oczekiwano " +
                    PMO_Test_Consts.ORDERS_DONE_EXPECTED );
            result = false;
        }

        return result;
    }

    @Override
    public void run() {
        prepareInitialOrders();
        createAndPrepareWaiters();
        setKitchen();
        initializeWaiters();
        runDone.set( true );
    }
}
