package com.kaushiksridhar.finledger.demo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.kaushiksridhar.finledger.account.AccountType;

/**
 * Builds 12 months of believable history for "Arjun", a software engineer in Bengaluru.
 *
 * Pure function: no database, no Spring. The same date and seed always give exactly the same
 * transactions, so the demo looks identical every time and the generator is easy to unit test.
 *
 * The patterns are deliberate, because later features must find them:
 *  - salary on the 1st, rent on the 5th, SIP on the 10th (recurring detection)
 *  - Netflix and Spotify on the same day every month (subscription detection)
 *  - the credit card bill paying exactly last month's card spending (transfers that aren't spending)
 */
public final class DemoDataGenerator {

    public enum AccountKey {
        HDFC,
        CARD,
        CASH,
        PAYTM
    }

    public record DemoAccount(AccountKey key, String name, AccountType type, long openingBalancePaise) {
    }

    /** category is the exact name of a built-in category. amountPaise is signed. */
    public record DemoTransaction(
            AccountKey account,
            String category,
            long amountPaise,
            LocalDate date,
            String description,
            String merchant) {
    }

    public static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount(AccountKey.HDFC, "HDFC Savings", AccountType.BANK, rupees(85_000)),
            new DemoAccount(AccountKey.CARD, "ICICI Credit Card", AccountType.CREDIT_CARD, 0),
            new DemoAccount(AccountKey.CASH, "Cash", AccountType.CASH, rupees(3_000)),
            new DemoAccount(AccountKey.PAYTM, "Paytm Wallet", AccountType.WALLET, rupees(500)));

    static final String CARD_PAYMENT = "Credit Card Payment";

    private static final String[] RESTAURANTS = { "Meghana Foods", "Truffles", "Third Wave Coffee", "Toit", "Empire Restaurant" };
    private static final String[] GROCERS = { "BigBasket", "Blinkit", "Zepto", "DMart" };
    private static final String[] SHOPS = { "Amazon", "Myntra", "Flipkart", "Decathlon" };
    private static final String[] CINEMAS = { "BookMyShow", "PVR Cinemas" };

    private final LocalDate today;
    private final Random random;
    private final List<DemoTransaction> out = new ArrayList<>();

    // Net credit card spending per month (spends minus refunds), paid off in full the next month
    private final Map<YearMonth, Long> cardNetByMonth = new HashMap<>();

    private DemoDataGenerator(LocalDate today, long seed) {
        this.today = today;
        this.random = new Random(seed);
    }

    /** Transactions from the 1st of the month 11 months ago up to and including today. */
    public static List<DemoTransaction> generate(LocalDate today, long seed) {
        DemoDataGenerator generator = new DemoDataGenerator(today, seed);
        YearMonth last = YearMonth.from(today);
        YearMonth month = last.minusMonths(11);
        for (int index = 0; !month.isAfter(last); index++, month = month.plusMonths(1)) {
            generator.generateMonth(month, index);
        }
        return List.copyOf(generator.out);
    }

    private void generateMonth(YearMonth m, int index) {
        // ---- Income
        add(AccountKey.HDFC, "Salary", rupees(index < 6 ? 85_000 : 92_000), m.atDay(1), "SALARY ACME TECH PVT LTD", "Acme Tech");
        if (index == 3 || index == 8) {
            add(AccountKey.HDFC, "Freelance", rupees(15_000), day(m), "UPI/FREELANCE LOGO DESIGN", null);
        }
        if (m.getMonthValue() % 3 == 0) {
            add(AccountKey.HDFC, "Interest & Dividends", rupees(between(900, 1_400)), m.atDay(28), "SAVINGS INTEREST CREDIT", "HDFC Bank");
        }

        // ---- Fixed monthly payments
        add(AccountKey.HDFC, "Health", -rupees(1_500), m.atDay(3), "CULT.FIT MEMBERSHIP", "Cult.fit");
        add(AccountKey.HDFC, "Rent", -rupees(22_000), m.atDay(5), "UPI/RENT/R KUMAR", "R Kumar (landlord)");
        add(AccountKey.HDFC, "Bills & Utilities", -rupees(between(1_100, 2_400)), m.atDay(8), "BESCOM ELECTRICITY BILL", "BESCOM");
        add(AccountKey.HDFC, "Investment", -rupees(5_000), m.atDay(10), "NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP", "PPFAS Mutual Fund");
        add(AccountKey.HDFC, "Bills & Utilities", -rupees(799), m.atDay(11), "ACT FIBERNET BROADBAND", "ACT Fibernet");
        add(AccountKey.PAYTM, "Bills & Utilities", -rupees(299), m.atDay(12), "JIO PREPAID RECHARGE", "Jio");
        add(AccountKey.CARD, "Subscriptions", -rupees(649), m.atDay(15), "NETFLIX.COM", "Netflix");
        add(AccountKey.CARD, "Subscriptions", -rupees(119), m.atDay(20), "SPOTIFY INDIA", "Spotify");

        // ---- Money moving between Arjun's own accounts (not spending)
        transfer(AccountKey.HDFC, AccountKey.PAYTM, rupees(1_000), m.atDay(2), "PAYTM WALLET TOP-UP");
        transfer(AccountKey.HDFC, AccountKey.CASH, rupees(pick(1_500, 2_000)), m.atDay(4), "ATM WITHDRAWAL");

        long lastMonthCard = cardNetByMonth.getOrDefault(m.minusMonths(1), 0L);
        if (lastMonthCard < 0) {
            LocalDate billDay = m.atDay(18);
            add(AccountKey.HDFC, CARD_PAYMENT, lastMonthCard, billDay, "ICICI CREDIT CARD PAYMENT", "ICICI Bank");
            add(AccountKey.CARD, CARD_PAYMENT, -lastMonthCard, billDay, "PAYMENT RECEIVED - THANK YOU", "ICICI Bank");
        }

        // ---- Everyday spending
        repeat(between(8, 14), () -> {
            boolean swiggy = random.nextBoolean();
            AccountKey account = random.nextInt(10) < 6 ? AccountKey.CARD : AccountKey.HDFC;
            add(account, "Food & Dining", -rupees(between(180, 650)), weekendLeaning(m),
                    swiggy ? "SWIGGY ORDER" : "ZOMATO ORDER", swiggy ? "Swiggy" : "Zomato");
        });

        repeat(between(2, 4), () -> {
            String place = pick(RESTAURANTS);
            add(AccountKey.CARD, "Food & Dining", -rupees(between(600, 2_200)), weekendLeaning(m), upper(place), place);
        });

        repeat(between(5, 9), () -> add(AccountKey.CASH, "Food & Dining", -rupees(between(20, 150)), day(m), "Chai and snacks", null));

        repeat(between(3, 5), () -> add(AccountKey.CASH, "Groceries", -rupees(between(80, 300)), day(m), "Vegetables and fruits", null));

        repeat(between(4, 6), () -> {
            String grocer = pick(GROCERS);
            AccountKey account = random.nextBoolean() ? AccountKey.CARD : AccountKey.HDFC;
            long amount = rupees(between(400, 2_600)) + random.nextInt(100);
            add(account, "Groceries", -amount, day(m), upper(grocer), grocer);
        });

        repeat(between(4, 6), () -> add(AccountKey.PAYTM, "Transport", -rupees(between(60, 180)), day(m), "RAPIDO AUTO", "Rapido"));

        repeat(between(4, 8), () -> {
            boolean uber = random.nextBoolean();
            add(AccountKey.HDFC, "Transport", -rupees(between(150, 450)), day(m), uber ? "UBER TRIP" : "OLA CABS", uber ? "Uber" : "Ola");
        });

        repeat(between(1, 3), () -> {
            String shop = pick(SHOPS);
            add(AccountKey.CARD, "Shopping", -rupees(between(499, 3_999)), day(m), upper(shop), shop);
        });

        repeat(between(1, 2), () -> {
            String cinema = pick(CINEMAS);
            add(AccountKey.CARD, "Entertainment", -rupees(between(300, 900)), weekendLeaning(m), upper(cinema), cinema);
        });

        repeat(between(0, 2), () -> add(AccountKey.HDFC, "Health", -rupees(between(150, 900)), day(m), "APOLLO PHARMACY", "Apollo Pharmacy"));

        add(AccountKey.CASH, "Personal Care", -rupees(between(200, 350)), day(m), "Haircut", "Looks Salon");

        if (random.nextInt(10) < 3) {
            add(AccountKey.HDFC, "Gifts & Donations", -rupees(between(500, 2_500)), day(m), "UPI/GIFT", null);
        }
        if (random.nextInt(4) == 0) {
            add(AccountKey.CARD, "Refunds", rupees(between(300, 1_500)), day(m), "AMAZON REFUND", "Amazon");
        }

        // ---- One-off events
        if (index == 5) {
            add(AccountKey.HDFC, "Education", -rupees(3_499), m.atDay(14), "UDEMY COURSE PURCHASE", "Udemy");
        }
        if (index == 7) {
            add(AccountKey.CARD, "Travel", -rupees(6_540), m.atDay(9), "INDIGO FLIGHT BLR-GOI", "IndiGo");
            add(AccountKey.CARD, "Travel", -rupees(9_200), m.atDay(13), "GOA BEACH RESORT", "Goa Beach Resort");
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Records a transaction unless it falls after today. Tracks card spending for next month's bill. */
    private void add(AccountKey account, String category, long amountPaise, LocalDate date, String description, String merchant) {
        if (date.isAfter(today)) {
            return;
        }
        out.add(new DemoTransaction(account, category, amountPaise, date, description, merchant));

        if (account == AccountKey.CARD && !category.equals(CARD_PAYMENT)) {
            cardNetByMonth.merge(YearMonth.from(date), amountPaise, Long::sum);
        }
    }

    /** Both sides of moving money between two of Arjun's own accounts. */
    private void transfer(AccountKey from, AccountKey to, long amountPaise, LocalDate date, String description) {
        add(from, "Transfer", -amountPaise, date, description, null);
        add(to, "Transfer", amountPaise, date, description, null);
    }

    private void repeat(int times, Runnable action) {
        for (int i = 0; i < times; i++) {
            action.run();
        }
    }

    private int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private int pick(int a, int b) {
        return random.nextBoolean() ? a : b;
    }

    private String pick(String[] options) {
        return options[random.nextInt(options.length)];
    }

    private LocalDate day(YearMonth month) {
        return month.atDay(1 + random.nextInt(month.lengthOfMonth()));
    }

    /** Most eating out and movies happen at weekends: a Monday to Thursday date gets one re-roll. */
    private LocalDate weekendLeaning(YearMonth month) {
        LocalDate date = day(month);
        if (date.getDayOfWeek().compareTo(DayOfWeek.FRIDAY) < 0 && random.nextBoolean()) {
            date = day(month);
        }
        return date;
    }

    private static long rupees(long rupees) {
        return rupees * 100;
    }

    private static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }
}
