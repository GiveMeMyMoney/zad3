import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

class Order {
    private int orderId;
    private int tableId;

    public Order(int orderId, int tableId) {
        this.orderId = orderId;
        this.tableId = tableId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public int getTableId() {
        return tableId;
    }

    public void setTableId(int tableId) {
        this.tableId = tableId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Order order = (Order) o;

        return getOrderId() == order.getOrderId();
    }

    @Override
    public int hashCode() {
        return getOrderId();
    }
}

//TODO przetestowac, porobic blokady
class RestaurantManagement implements RestaurantManagementInterface {
    private KitchenInterface kitchen; //Obserwowany (Observable) ?
    private ConcurrentHashMap<Integer, Runnable> waiterMap; //Obserwatorzy (Observers) ?

    AtomicInteger maxMealCount; //Je�li restauracja ma N aktywnych kelner�w nie wolno doprowadzi� do zrealizowania przez kuchni� wi�cej ni� N posi�k�w "na zapas"
    AtomicInteger maxMealCountInProgress; //Rzeczywista ilosc posilkow juz przygotowanych + przygotowywanych przez kuchnie <= N
    AtomicInteger maxParallelMealPrepare; //Liczba zada� (posi�k�w), kt�re kuchnia jest w stanie jednocze�nie przygotowywa�. !!to bedzie ile watkow utworze!!

    //potrzebuje sortowanie po terminie w liscie by brac te ordery (zamowienia) ktore czekaja najdluzej
    private ConcurrentLinkedQueue<Order> orderToPrepareQueue; //zadania (posilki) do przygotowania przez kuchnie
    private ConcurrentHashMap<Integer, Order> orderPreparingInKitchenMap; //zadania W TRAKCIE przygotowywania przez kuchnie
    //potrzebuje sortowanie po terminie w liscie by brac te ordery (zamowienia) ktore czekaja najdluzej
    private ConcurrentLinkedQueue<Order> orderWaitingForGoQueue; //zadania wykonane przez kuchnie i czekajace na odbior czyli tzw. N-bufor.

    private final Object waitingForWaiterHelper = new Object();

    private final Object waitingForNewOrderHelper = new Object();
    private final Object waitingForOrderCompleteHelper = new Object();

    public RestaurantManagement() {
        this.maxMealCount = new AtomicInteger(0);
        this.maxMealCountInProgress = new AtomicInteger(0);
        this.maxParallelMealPrepare = new AtomicInteger(0);
        this.waiterMap = new ConcurrentHashMap<>();
        this.kitchenThreadInStopMap = new ConcurrentHashMap<>();

        this.orderToPrepareQueue = new ConcurrentLinkedQueue<>();
        this.orderPreparingInKitchenMap = new ConcurrentHashMap<>();
        this.orderWaitingForGoQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void addWaiter(WaiterInterface waiter) {
        //System.out.println("-----------------------------------------------addWaiter KELNER!!!!: " + waiter.getID());
        //synchronized (waitingForWaiterHelper) {
        //waiterMap.put(waiter.getID(), waiter);
        maxMealCount.incrementAndGet();
        Runnable runnable = waiterMap.get(waiter.getID());
        //nowy kelner, rejestracji w restauracji
        if (runnable == null) {
            Runnable waiterThread = new WaiterThread(waiter); //tworzenie watku dla kelnera
            waiterMap.put(waiter.getID(), waiterThread); //wrzucanie do mapy
            new Thread(waiterThread).start(); // odpalanie watku kelnera
        } else {
            //powrot kelnera do pracy o tym samym ID
            WaiterThread waiterThread = (WaiterThread) runnable;
            waiterThread.reactivateThread(waiterThread.waiter);
            //waiterThread.disTerminate();
            //new Thread(waiterThread).start(); // odpalanie watku kelnera
            //TODO startNewThread ?
        }
        //}
    }

    /**
     * Kelner ko�czy prac� - po zako�czeniu tej metody nie wolno
     * ju� zleca� mu prac do wykonania.
     *
     * @param waiter obiekt-kelner, kt�ry przestaje
     *               obs�ugiwa� klient�w.
     */
    //TODO a moze to jest zrobione? - Kelner chc�c zako�czy� prac� najpierw wykonuje metod� removeWaiter a potem potwierdza wykonanie powierzonej mu pracy. W ten spos�b RM b�dzie wiedzie� o zako�czeniu pracy przez kelnera przed mo�liwo�ci� zlecenia mu wykonania kolejnej pracy.
    @Override
    public void removeWaiter(WaiterInterface waiter) {
        //synchronized (waitingForWaiterHelper) {
        //synchronized (waitingForNewOrderHelper) {
        //System.out.println("-----------------------------------------------removeWaiter KELNER!!!!: " + waiter.getID());
        maxMealCount.decrementAndGet();
        Runnable runnable = waiterMap.get(waiter.getID());
        WaiterThread waiterThread = (WaiterThread) runnable;
        waiterThread.terminate();
        //}
        //}
    }

    @Override
    public void setKitchen(KitchenInterface kitchen) {
        this.kitchen = kitchen;
        maxParallelMealPrepare.set(kitchen.getNumberOfParallelTasks());
        //System.out.println("-----------------------------------------------setKitchen KITCHEN!!!!" + " maxParallelMealPrepare: " + kitchen.getNumberOfParallelTasks());

        //odpalamy tyle kucharzy ile mozna jednoczesnie przygotowac posilkow, jeden kucharz = jeden mozliwy posilek do przygotowania
        for (int i = 0; i < kitchen.getNumberOfParallelTasks(); i++) {
            new Thread(new KitchenThread(kitchen)).start();
        }

    }

    ///region WaitherThread
    class WaiterThread implements Runnable {
        WaiterInterface waiter;

        //region running
        private boolean running;

        public void terminate() {
            running = false;
        }

        public void disTerminate() {
            running = true;
        }

        public void reactivateThread(WaiterInterface waiter) {
            Runnable runnable = waiterMap.get(waiter.getID());
            WaiterThread waiterThread = (WaiterThread) runnable;
            waiterThread.disTerminate();
            new Thread(waiterThread).start(); // odpalanie watku kelnera
        }

        //endregion

        public WaiterThread(WaiterInterface waiter) {
            this.waiter = waiter;
            this.running = true;
        }

        public void regiserOrderToWaiter() {
            OrderInterface orderRegisterInterface = new OrderInterface() {
                @Override
                public void newOrder(int orderID, int tableID) {
                    //System.out.println("-----------------------------------------------newOrder KELNER!!!!" + waiter.getID() + " orderId: " + orderID);
                    //rejestrujemy zadanie oraz powiadamiamy kuchnie o nowym zadaniu.
                    //System.out.println("-----------------------------------------------newOrder KELNER!!!!" + waiter.getID() + " orderId: " + orderID);
                    orderToPrepareQueue.add(new Order(orderID, tableID));
                    synchronized (waitingForNewOrderHelper) {
                        waitingForNewOrderHelper.notifyAll(); //poinformuj kuchnie ze mozna robic zlecenie
                    }
                    //zlecenia trzeba przekazac do kuchni - notify kuchnia...
                    //i tam z orderToPrepareQueue -> orderPreparingInKitchenMap
                }

                //wywolywane po zakonczeniu go tzn. �e kelner doszed� do stolika i przekaza� Order
                @Override
                public void orderComplete(int orderID, int tableID) {
                    //TODO w mealReady mozna uzupelnic bufor o jeden a nie tutaj
                    orderWaitingForGoQueue.remove(new Order(orderID, tableID)); // usuniecie zamowienia z listy buforowej
                    //maxMealCountInProgress.decrementAndGet();

                    //System.out.println("-----------------------------------------------orderComplete KELNER!!!!" + waiter.getID() + " orderId: " + orderID);

                    //trzeba od nowa wystartowac thread
                    reactivateThread(waiter);
                }
            };

            waiter.registerOrder(orderRegisterInterface);
            disTerminate();
        }


        @Override
        public void run() {
            regiserOrderToWaiter();
            while (running) {
                //System.out.println("-----------------------------------------------RUN IN KELNER!!!!" + waiter.getID());
                try {
                    //jesli kolejka do odbioru danych jest pusta
                    if (orderWaitingForGoQueue.isEmpty()) {
                        synchronized (waitingForOrderCompleteHelper) {
                            waitingForOrderCompleteHelper.wait();
                        }
                    }
                    Order order = orderWaitingForGoQueue.poll();
                    if (order != null) {
                        //System.out.println("-----------------------------------------------GO KELNER!!!!" + waiter.getID() + " orderId: " + order.getOrderId());
                        waiter.go(order.getOrderId(), order.getTableId());
                        maxMealCountInProgress.decrementAndGet();
                        this.terminate(); // wyslanie kelnera z posilkiem GO
                    }
                    //TODO Ci co maj� GO nie mog� nic robi� bo chodz� i roznosz� dopiero zwalniaja sie przy orderComplete
                    //TODO orderComplete gdy wykona si� go + QueueDoOrderow ktore sa w trakcie GO...
                } catch (InterruptedException e) {
                    //System.out.println("InterruptedException dla Kelnera: " + e.getMessage());
                }

            }
        }


    }
    ///endregion WaitherThread

    // ---------------------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------
    // ---------------------------------------------------------------------------------------------------------

    ///region KitchenThread

    private ConcurrentHashMap<KitchenInterface, Runnable> kitchenThreadInStopMap;

    class KitchenThread implements Runnable {
        KitchenInterface kitchen;
        ReceiverInterface receiverInterface;

        //region running
        private boolean running;

        public void terminate() {
            running = false;
        }

        public void disTerminate() {
            running = true;
        }

        public void reactivateThread() {
            Runnable runnable = kitchenThreadInStopMap.get(kitchen);
            KitchenThread kitchenThread = (KitchenThread) runnable;
            kitchenThread.disTerminate();
            new Thread(runnable).start(); // odpalanie watku kelnera
        }

        //endregion

        public KitchenThread(KitchenInterface kitchen) {
            this.kitchen = kitchen;
            this.running = true;

            receiverInterface = new ReceiverInterface() {
                @Override
                public void mealReady(int orderID) {
                        Order order = orderPreparingInKitchenMap.get(orderID);
                        orderPreparingInKitchenMap.remove(orderID);
                        //TODO nie robie nic z orderWaitingForGoQueue
                        orderWaitingForGoQueue.add(order);
                    synchronized (waitingForOrderCompleteHelper) {
                        waitingForOrderCompleteHelper.notifyAll();
                    }
                    reactivateThread();
                }
            };

            this.kitchen.registerReceiver(receiverInterface);
        }

        private boolean possibleToPrepareOrder() {
            //TODO synchronized z updatem mapy
            /*int maxMealCountNow = maxMealCount.get();
            int maxMealCountInProgressNow = maxMealCountInProgress.get();
            ////System.out.println("ILOSC maxMealCount: " + maxMealCountNow + " ILOSC maxMealCountInProgress: " + maxMealCountInProgress.get());
            //czy bufor pomiesci? (obejscie na compare thread save // TODO synchronized z wszystkimi zmianami?)
            if (maxMealCountNow <= maxMealCountInProgressNow) {
                return false;
            }*/


            //czy bufor pomiesci? (obejscie na compare thread save // TODO synchronized z wszystkimi zmianami?)
            if (maxMealCount.compareAndSet(maxMealCountInProgress.get(), maxMealCount.get())) {
                return false;
            }
            return true;
        }

        //SYNCHRONIZED?
        private void doPrepareMeal() {
            Order orderToPrepare = orderToPrepareQueue.poll();
            if (orderToPrepare != null) {
                orderPreparingInKitchenMap.put(orderToPrepare.getOrderId(), orderToPrepare);
                maxMealCountInProgress.incrementAndGet();
                //synchronized (waitingForNewOrderHelper) {
                if (possibleToPrepareOrder()) {
                    //System.out.println("-----------------------------------------------prepare KUCHNIA!!!! orderId: " + orderToPrepare.getOrderId());
                    kitchen.prepare(orderToPrepare.getOrderId()); // i tu asynchronicznie sie wywoluje
                    kitchenThreadInStopMap.put(kitchen, this);
                    terminate();
                }
                //}
            }
        }

        @Override
        public void run() {
            while (running) {
                try {
                    //sprawdzanie czy mozna przygotowywac posilki przez kuchnie i odkladac do N - bufora
                    if (!possibleToPrepareOrder()) {
                        synchronized (waitingForNewOrderHelper) {
                            waitingForNewOrderHelper.wait();
                        }
                    }
                    //jezeli weszlo tutaj tzn ze jest mozliwe prepare order
                    doPrepareMeal();
                } catch (InterruptedException e) {
                    //System.out.println("InterruptedException dla Kuchni: " + e.getMessage());
                }
            }
        }
    }
    //endregion KitcheThread

}