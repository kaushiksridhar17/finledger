package com.kaushiksridhar.finledger.rules;

import java.util.List;

/**
 * Well-known Indian merchants and bank phrases, matched as whole words in a transaction's description.
 * Each gives a built-in category and a tidy merchant name ("UPI/SWIGGY/4123..." -> "Swiggy").
 *
 * Order matters: the first match wins, so more specific phrases come first
 * ("AMAZON PRIME" before "AMAZON", "REFUND" before any shop).
 */
public final class BuiltInRules {

    public record BuiltInRule(String keyword, String category, String merchant) {
    }

    public static final List<BuiltInRule> RULES = List.of(
            // Money coming back or moving between your own accounts
            new BuiltInRule("REFUND", "Refunds", null),
            new BuiltInRule("CASHBACK", "Refunds", null),
            new BuiltInRule("CREDIT CARD", "Credit Card Payment", null),
            new BuiltInRule("CRED", "Credit Card Payment", "CRED"),
            new BuiltInRule("ATM", "Transfer", null),
            new BuiltInRule("SELF", "Transfer", null),
            new BuiltInRule("TOPUP", "Transfer", null),

            // Investments
            new BuiltInRule("SIP", "Investment", null),
            new BuiltInRule("STARMF", "Investment", null),
            new BuiltInRule("MUTUAL FUND", "Investment", null),
            new BuiltInRule("ZERODHA", "Investment", "Zerodha"),
            new BuiltInRule("GROWW", "Investment", "Groww"),

            // Income
            new BuiltInRule("SALARY", "Salary", null),
            new BuiltInRule("SAL", "Salary", null),
            new BuiltInRule("INTEREST", "Interest & Dividends", null),
            new BuiltInRule("INT PD", "Interest & Dividends", null),
            new BuiltInRule("DIVIDEND", "Interest & Dividends", null),

            // Subscriptions
            new BuiltInRule("NETFLIX", "Subscriptions", "Netflix"),
            new BuiltInRule("SPOTIFY", "Subscriptions", "Spotify"),
            new BuiltInRule("HOTSTAR", "Subscriptions", "JioHotstar"),
            new BuiltInRule("AMAZON PRIME", "Subscriptions", "Amazon Prime"),
            new BuiltInRule("YOUTUBE", "Subscriptions", "YouTube Premium"),

            // Food
            new BuiltInRule("SWIGGY", "Food & Dining", "Swiggy"),
            new BuiltInRule("ZOMATO", "Food & Dining", "Zomato"),
            new BuiltInRule("DOMINOS", "Food & Dining", "Domino's"),
            new BuiltInRule("STARBUCKS", "Food & Dining", "Starbucks"),

            // Groceries
            new BuiltInRule("BIGBASKET", "Groceries", "BigBasket"),
            new BuiltInRule("BLINKIT", "Groceries", "Blinkit"),
            new BuiltInRule("ZEPTO", "Groceries", "Zepto"),
            new BuiltInRule("DMART", "Groceries", "DMart"),
            new BuiltInRule("INSTAMART", "Groceries", "Swiggy Instamart"),

            // Transport
            new BuiltInRule("UBER", "Transport", "Uber"),
            new BuiltInRule("OLA", "Transport", "Ola"),
            new BuiltInRule("RAPIDO", "Transport", "Rapido"),
            new BuiltInRule("METRO", "Transport", null),
            new BuiltInRule("FASTAG", "Transport", "FASTag"),
            new BuiltInRule("PETROL", "Transport", null),

            // Shopping
            new BuiltInRule("AMAZON", "Shopping", "Amazon"),
            new BuiltInRule("FLIPKART", "Shopping", "Flipkart"),
            new BuiltInRule("MYNTRA", "Shopping", "Myntra"),
            new BuiltInRule("AJIO", "Shopping", "AJIO"),
            new BuiltInRule("DECATHLON", "Shopping", "Decathlon"),

            // Bills
            new BuiltInRule("BESCOM", "Bills & Utilities", "BESCOM"),
            new BuiltInRule("ELECTRICITY", "Bills & Utilities", null),
            new BuiltInRule("BROADBAND", "Bills & Utilities", null),
            new BuiltInRule("FIBERNET", "Bills & Utilities", null),
            new BuiltInRule("AIRTEL", "Bills & Utilities", "Airtel"),
            new BuiltInRule("JIO", "Bills & Utilities", "Jio"),
            new BuiltInRule("RECHARGE", "Bills & Utilities", null),

            // Rent, health, entertainment, travel, education
            new BuiltInRule("RENT", "Rent", null),
            new BuiltInRule("APOLLO", "Health", "Apollo Pharmacy"),
            new BuiltInRule("PHARMACY", "Health", null),
            new BuiltInRule("CULT", "Health", "Cult.fit"),
            new BuiltInRule("BOOKMYSHOW", "Entertainment", "BookMyShow"),
            new BuiltInRule("PVR", "Entertainment", "PVR Cinemas"),
            new BuiltInRule("IRCTC", "Travel", "IRCTC"),
            new BuiltInRule("INDIGO", "Travel", "IndiGo"),
            new BuiltInRule("MAKEMYTRIP", "Travel", "MakeMyTrip"),
            new BuiltInRule("UDEMY", "Education", "Udemy"),
            new BuiltInRule("COURSERA", "Education", "Coursera"));

    private BuiltInRules() {
    }
}
