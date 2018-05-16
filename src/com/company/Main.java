package com.company;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) {
        // write your code here
    }
}

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

        if (getOrderId() != order.getOrderId()) return false;
        return getTableId() == order.getTableId();
    }

    @Override
    public int hashCode() {
        int result = getOrderId();
        result = 31 * result + getTableId();
        return result;
    }
}

//TODO przetestowac, porobic blokady
class RestaurantManagement implements RestaurantManagementInterface {
    private KitchenInterface kitchen; //Obserwowany (Observable) ?
    private ConcurrentHashMap<Integer, Runnable> waiterMap; //Obserwatorzy (Observers) ?
    AtomicInteger maxMealCount; //Jeœli restauracja ma N aktywnych kelnerów nie wolno doprowadziæ do zrealizowania przez kuchniê wiêcej ni¿ N posi³ków "na zapas"
    AtomicInteger maxMealCountInProgress; //Rzeczywista ilosc posilkow juz przygotowanych + przygotowywanych przez kuchnie <= N
    AtomicInteger maxParallelMealPrepare; //Liczba zadañ (posi³ków), które kuchnia jest w stanie jednoczeœnie przygotowywaæ. !!to bedzie ile watkow utworze!!

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
        this.waiterMap = new ConcurrentHashMap<Integer, Runnable>();

        this.orderToPrepareQueue = new ConcurrentLinkedQueue<Order>();
        this.orderPreparingInKitchenMap = new ConcurrentHashMap<Integer, Order>();
        this.orderWaitingForGoQueue = new ConcurrentLinkedQueue<Order>();
    }

    @Override
    public void addWaiter(WaiterInterface waiter) {
        synchronized (waitingForWaiterHelper) {
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
                waiterThread.disTerminate();
            }
        }
    }

    /**
     * Kelner koñczy pracê - po zakoñczeniu tej metody nie wolno
     * ju¿ zlecaæ mu prac do wykonania.
     * @param waiter obiekt-kelner, który przestaje
     * obs³ugiwaæ klientów.
     */
    //TODO a moze to jest zrobione? - Kelner chc¹c zakoñczyæ pracê najpierw wykonuje metodê removeWaiter a potem potwierdza wykonanie powierzonej mu pracy. W ten sposób RM bêdzie wiedzieæ o zakoñczeniu pracy przez kelnera przed mo¿liwoœci¹ zlecenia mu wykonania kolejnej pracy.
    @Override
    public void removeWaiter(WaiterInterface waiter) {
        synchronized (waitingForWaiterHelper) {
            maxMealCount.decrementAndGet();
            Runnable runnable = waiterMap.get(waiter.getID());
            WaiterThread waiterThread = (WaiterThread) runnable;
            waiterThread.terminate();
        }
    }

    @Override
    public void setKitchen(KitchenInterface kitchen) {
        this.kitchen = kitchen;
        maxParallelMealPrepare.set(kitchen.getNumberOfParallelTasks());

        new Thread(new KitchenThread(kitchen)).start(); //odpalamy kuchnie ktora chodzi non stop...
    }

    class WaiterThread implements Runnable {
        WaiterInterface waiter;
        OrderInterface orderRegisterInterface;
        private volatile boolean running;

        //TODO jest jeszcze opcja terminate() - https://stackoverflow.com/questions/10961714/how-to-properly-stop-the-thread-in-java 2 odp.
        public void terminate() {
            running = false;
        }

        public void disTerminate() {
            running = true;
        }

        //AtomicInteger actualOrderId;

        public WaiterThread(WaiterInterface waiter) {
            //actualOrderId = null;
            this.waiter = waiter;
            orderRegisterInterface = new OrderInterface() {
                @Override
                public void newOrder(int orderID, int tableID) {
                    //rejestrujemy zadanie oraz powiadamiamy kuchnie o nowym zadaniu.
                    synchronized (waitingForNewOrderHelper) {
                        //TODO czy nie potrzebne? if (orderToPrepareQueue.isEmpty()) { }
                        orderToPrepareQueue.add(new Order(orderID, tableID));
                        waitingForNewOrderHelper.notify(); //poinformuj kuchnie ze mozna robic zlecenie
                    }
                    //zlecenia trzeba przekazac do kuchni - notify kuchnia...
                    //i tam z orderToPrepareQueue -> orderPreparingInKitchenMap
                }

                //wywolywane po zakonczeniu go tzn. ¿e kelner doszed³ do stolika i przekaza³ Order
                @Override
                public void orderComplete(int orderID, int tableID) {
                    //TODO w mealReady mozna uzupelnic bufor o jeden a nie tutaj
                    // usuwamy calkowicie orderRegisterInterface (zamowienie)
                    synchronized (waitingForOrderCompleteHelper) {
                        orderWaitingForGoQueue.remove(new Order(orderID, tableID)); // usuniecie zamowienia z listy buforowej
                        maxMealCountInProgress.decrementAndGet();
                        disTerminate(); //kelner dostarczyl posilek
                    }

                }
            };

            this.waiter.registerOrder(orderRegisterInterface);
        }

        @Override
        public void run() {
            while (running) {
                try {
                    if (orderWaitingForGoQueue.isEmpty()) {
                        synchronized (waitingForOrderCompleteHelper) {
                            waitingForOrderCompleteHelper.wait();
                        }
                    }
                    Order order = orderWaitingForGoQueue.poll();
                    this.terminate(); // wyslanie kelnera z posilkiem GO
                    waiter.go(order.getOrderId(), order.getTableId());
                    //TODO Ci co maj¹ GO nie mog¹ nic robiæ bo chodz¹ i roznosz¹ dopiero zwalniaja sie przy orderComplete
                    //TODO orderComplete gdy wykona siê go + QueueDoOrderow ktore sa w trakcie GO...
                } catch (InterruptedException e) {
                    System.out.println("InterruptedException dla Kelnera: " + e.getMessage());
                }

            }
        }


    }


    class KitchenThread implements Runnable {
        KitchenInterface kitchen;
        ReceiverInterface receiverInterface;

        public KitchenThread(KitchenInterface kitchen) {
            this.kitchen = kitchen;
            receiverInterface = new ReceiverInterface() {
                @Override
                public void mealReady(int orderID) {
                    synchronized (waitingForOrderCompleteHelper) {
                        Order order = orderPreparingInKitchenMap.get(orderID);
                        orderPreparingInKitchenMap.remove(orderID);
                        orderWaitingForGoQueue.add(order);
                        waitingForOrderCompleteHelper.notifyAll();

                    }
                }
            };

            this.kitchen.registerReceiver(receiverInterface);
        }

        private boolean possibleToPrepareOrder() {
            //TODO synchronized z updatem mapy
            if (orderToPrepareQueue.isEmpty()) {
                return false;
            } //czy bufor pomiesci? (obejscie na compare thread save // TODO synchronized z wszystkimi zmianami?)
            else if (maxMealCount.compareAndSet(maxMealCountInProgress.get(), maxMealCount.get())) {
                return false;
            } //maksymalna jednoczesna ilosc robienia zamowien przez kuchnie (obejscie na compare thread save // TODO synchronized z wszystkimi zmianami?)
            else if (maxParallelMealPrepare.compareAndSet(orderPreparingInKitchenMap.size(), maxParallelMealPrepare.get())) {
                return false;
            }
            return true;
        }

        //SYNCHRONIZED?
        private void doPrepareMeal() {
            Order orderToPrepare = orderToPrepareQueue.poll();
            orderPreparingInKitchenMap.put(orderToPrepare.getOrderId(), orderToPrepare);
            maxMealCountInProgress.incrementAndGet();
            kitchen.prepare(orderToPrepare.getOrderId()); // i tu asynchronicznie sie wywoluje
        }

        @Override
        public void run() {
            while (true) {
                try {
                    //sprawdzanie czy mozna przygotowywac posilki przez kuchnie i odkladac do N - bufora
                    synchronized (waitingForNewOrderHelper) {
                        if (!possibleToPrepareOrder()) {
                            waitingForNewOrderHelper.wait();
                        }
                    }
                    //jezeli weszlo tutaj tzn ze jest mozliwe prepare order
                    doPrepareMeal();
                } catch (InterruptedException e) {
                    System.out.println("InterruptedException dla Kuchni: " + e.getMessage());
                }
            }
        }
    }

}