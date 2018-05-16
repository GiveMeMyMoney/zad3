public class PMO_Test_Consts {
    public static final long ORDER_COMPLETE_MIN_TIME = 100;
    public static final long ORDER_COMPLETE_DELTA_TIME = 40;
    public static final int ORDER_COMPLETE_MAX_MULT = 4; // maksymalnie 5xDELTA
    public static final int NUMBER_OF_ORDERS_GENERATE_IN_EMERGENCY = 3;
    public static final long CYCLIC_BARRIER_TIMEOUT = 1000;

    public static final int WAITERS = 12;
    public static final int FLOATING_WAITERS = 2;
    public static final double FLOATING_WAITER_PROBABILITY = 0; // wyłączone !
    public static final long FLOATING_WAITER_MIN_REST_TIME =  1250;
    public static final long FLOATING_WAITER_DELTA_REST_TIME =  750;

    public static final int SIMULTANEOUS_ORDER = 2;
    public static final int SIMULTANEOUS_CONFIRM = 2;
    public static final int SIMULTANEOUS_READY = 2;

    public static final double WAITER_BULK_ORDERS_PROBABILITY = 0.4;

    public static final int KITCHEN_PARALLEL_TASKS = 10;
    public static final long KITCHEN_MEAL_PREPARATION_MIN_TIME = 250;
    public static final long KITCHEN_MEAL_PREPARATION_DELTA_TIME = 130;

    public static final int INITIAL_ORDERS_PER_WAITER = 2;

    public static final int KITCHEN_AND_WAITERS_PARALLEL_TASKS_EXPECTED = 14;
    public static final int KITCHEN_PARALLEL_TASKS_EXPECTED = 8;
    public static final int WAITERS_PARALLEL_TASKS_EXPECTED = 8;
    public static final int ORDERS_DONE_EXPECTED = 90;
}
