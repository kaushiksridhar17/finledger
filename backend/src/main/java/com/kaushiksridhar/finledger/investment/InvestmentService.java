package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.investment.PortfolioMath.Position;
import com.kaushiksridhar.finledger.investment.SchemeCache.SchemeInfo;
import com.kaushiksridhar.finledger.transaction.Transaction;
import com.kaushiksridhar.finledger.user.UserRepository;

/**
 * Mutual fund holdings: the portfolio view, adding purchases and redemptions, and turning SIP debits
 * found in bank statements into purchases.
 *
 * Nothing here calls the internet. Funds are fetched into SchemeCache first (by the controller, the
 * nightly job or the demo), so a slow or unreachable mfapi.in never holds a database transaction open.
 */
@Service
public class InvestmentService {

    static final int HISTORY_MONTHS = 12;
    static final int MIN_DAYS_FOR_XIRR = 30;
    static final int SUGGESTION_LOOKBACK_MONTHS = 13;
    static final long MAX_AMOUNT_PAISE = 100_000_000_000L;

    private final MfTransactionRepository transactionRepository;
    private final SipLinkRepository sipLinkRepository;
    private final SchemeCache schemeCache;
    private final UserRepository userRepository;
    private final Clock clock;

    public InvestmentService(MfTransactionRepository transactionRepository,
            SipLinkRepository sipLinkRepository,
            SchemeCache schemeCache,
            UserRepository userRepository,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.sipLinkRepository = sipLinkRepository;
        this.schemeCache = schemeCache;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ portfolio

    @Transactional(readOnly = true)
    public PortfolioResponse portfolio(long userId) {
        LocalDate today = AppTime.today(clock);
        List<MfTransaction> all = transactionRepository.findByUserIdOrderByTxnDateAscIdAsc(userId);
        List<SipLink> sips = sipLinkRepository.findByUserIdOrderByIdAsc(userId);

        Set<Integer> codes = new TreeSet<>();
        all.forEach(t -> codes.add(t.getSchemeCode()));
        sips.forEach(s -> codes.add(s.getSchemeCode()));
        Map<Integer, SchemeInfo> schemes = schemeCache.findAll(codes);

        Map<Integer, List<MfTransaction>> byScheme = all.stream()
                .collect(Collectors.groupingBy(MfTransaction::getSchemeCode, LinkedHashMap::new, Collectors.toList()));
        Set<Integer> sipCodes = sips.stream().map(SipLink::getSchemeCode).collect(Collectors.toSet());

        List<PortfolioResponse.Fund> funds = new ArrayList<>();
        long invested = 0;
        long value = 0;
        LocalDate asOf = null;
        for (Map.Entry<Integer, List<MfTransaction>> entry : byScheme.entrySet()) {
            SchemeInfo scheme = schemes.get(entry.getKey());
            List<PortfolioMath.Trade> trades = toTrades(entry.getValue());
            Position position = PortfolioMath.positionAsOf(trades, today);
            if (position.isEmpty() || scheme == null || scheme.latestNav() == null) {
                continue;
            }

            long fundValue = PortfolioMath.valuePaise(position.units(), scheme.latestNav());
            invested += position.investedPaise();
            value += fundValue;
            if (asOf == null || scheme.latestNavDate().isAfter(asOf)) {
                asOf = scheme.latestNavDate();
            }

            funds.add(new PortfolioResponse.Fund(scheme.schemeCode(), scheme.name(), scheme.fundHouse(), scheme.category(),
                    position.units(), scheme.latestNav(), scheme.latestNavDate(), position.investedPaise(), fundValue,
                    fundValue - position.investedPaise(), xirr(trades, fundValue, scheme.latestNavDate()),
                    sipCodes.contains(scheme.schemeCode())));
        }
        funds.sort(Comparator.comparingLong(PortfolioResponse.Fund::valuePaise).reversed());

        LocalDate valuedOn = asOf == null ? today : asOf;
        Double portfolioXirr = xirr(toTrades(all), value, valuedOn);

        List<PortfolioResponse.Trade> trades = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            MfTransaction t = all.get(i);
            SchemeInfo scheme = schemes.get(t.getSchemeCode());
            trades.add(new PortfolioResponse.Trade(t.getId(), t.getSchemeCode(),
                    scheme == null ? "Scheme " + t.getSchemeCode() : scheme.name(), t.getType(), t.getTxnDate(),
                    t.getAmountPaise(), t.getUnits(), t.getNav(), t.getLedgerTransactionId() != null));
        }

        Map<Long, Integer> purchasesPerSip = new HashMap<>();
        for (MfTransaction t : all) {
            if (t.getSipLink() != null) {
                purchasesPerSip.merge(t.getSipLink().getId(), 1, Integer::sum);
            }
        }
        List<PortfolioResponse.Sip> sipViews = sips.stream()
                .map(s -> new PortfolioResponse.Sip(s.getId(), s.getSchemeCode(),
                        schemes.containsKey(s.getSchemeCode()) ? schemes.get(s.getSchemeCode()).name() : "Scheme " + s.getSchemeCode(),
                        s.getLabel(), purchasesPerSip.getOrDefault(s.getId(), 0)))
                .toList();

        return new PortfolioResponse(invested, value, value - invested, portfolioXirr, asOf, funds,
                history(byScheme, today), trades, sipViews);
    }

    /** Invested and value at the end of each of the last 12 months, and today. */
    private List<PortfolioResponse.HistoryPoint> history(Map<Integer, List<MfTransaction>> byScheme, LocalDate today) {
        if (byScheme.isEmpty()) {
            return List.of();
        }
        LocalDate firstTrade = byScheme.values().stream()
                .flatMap(List::stream).map(MfTransaction::getTxnDate).min(LocalDate::compareTo).orElse(today);

        List<LocalDate> dates = new ArrayList<>();
        YearMonth thisMonth = YearMonth.from(today);
        for (int i = HISTORY_MONTHS; i >= 1; i--) {
            LocalDate monthEnd = thisMonth.minusMonths(i).atEndOfMonth();
            if (!monthEnd.isBefore(firstTrade)) {
                dates.add(monthEnd);
            }
        }
        dates.add(today);

        Map<Integer, NavSeries> navs = new HashMap<>();
        for (Integer code : byScheme.keySet()) {
            navs.put(code, schemeCache.series(code, dates.get(0).minusDays(15), today));
        }

        List<PortfolioResponse.HistoryPoint> points = new ArrayList<>();
        for (LocalDate date : dates) {
            long invested = 0;
            long value = 0;
            for (Map.Entry<Integer, List<MfTransaction>> entry : byScheme.entrySet()) {
                Position position = PortfolioMath.positionAsOf(toTrades(entry.getValue()), date);
                if (position.isEmpty()) {
                    continue;
                }
                invested += position.investedPaise();
                NavPoint nav = navs.get(entry.getKey()).valueOn(date).orElse(null);
                value += nav == null ? position.investedPaise() : PortfolioMath.valuePaise(position.units(), nav.nav());
            }
            points.add(new PortfolioResponse.HistoryPoint(date, invested, value));
        }
        return points;
    }

    /** XIRR only means something once money has been invested for a while; a few days' return annualised is noise. */
    private static Double xirr(List<PortfolioMath.Trade> trades, long valuePaise, LocalDate asOf) {
        if (trades.isEmpty()) {
            return null;
        }
        LocalDate first = trades.stream().map(PortfolioMath.Trade::date).min(LocalDate::compareTo).orElseThrow();
        if (first.isAfter(asOf.minusDays(MIN_DAYS_FOR_XIRR))) {
            return null;
        }
        return Xirr.calculate(PortfolioMath.cashFlows(trades, valuePaise, asOf));
    }

    // ------------------------------------------------------------------ purchases and redemptions

    /** Records a purchase or redemption. The fund must already be in SchemeCache (the controller makes sure). */
    @Transactional
    public void addTransaction(long userId, FundTransactionRequest request) {
        LocalDate today = AppTime.today(clock);
        if (request.date().isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The date can't be in the future");
        }
        if (request.amountPaise() > MAX_AMOUNT_PAISE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount is too large");
        }

        SchemeInfo scheme = schemeCache.find(request.schemeCode())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Choose a fund from the search"));
        NavPoint nav = schemeCache.series(scheme.schemeCode(), request.date().minusDays(10), request.date().plusDays(10))
                .tradeOn(request.date())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "There's no NAV for " + scheme.name() + " around " + request.date() + ". Is the date right?"));

        BigDecimal units = request.units() != null
                ? request.units().setScale(PortfolioMath.UNIT_SCALE, RoundingMode.HALF_UP)
                : PortfolioMath.unitsFor(request.amountPaise(), nav.nav());
        if (units.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That amount is too small to buy any units");
        }

        if (request.type() == MfTxnType.SELL) {
            // Checked on the sale's own date and every later one, so later sales still add up too
            List<PortfolioMath.Trade> trades = new ArrayList<>(
                    toTrades(transactionRepository.findByUserIdAndSchemeCode(userId, scheme.schemeCode())));
            BigDecimal heldThen = PortfolioMath.positionAsOf(trades, request.date()).units();
            trades.add(new PortfolioMath.Trade(request.date(), MfTxnType.SELL, request.amountPaise(), units));
            if (!PortfolioMath.neverOversold(trades)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "You only held " + heldThen.toPlainString()
                        + " units of " + scheme.name() + " on " + request.date() + ", counting later redemptions too");
            }
        }

        MfTransaction transaction = new MfTransaction();
        transaction.setUser(userRepository.getReferenceById(userId));
        transaction.setSchemeCode(scheme.schemeCode());
        transaction.setType(request.type());
        transaction.setTxnDate(request.date());
        transaction.setAmountPaise(request.amountPaise());
        transaction.setUnits(units);
        transaction.setNav(nav.nav());
        transactionRepository.save(transaction);
    }

    @Transactional
    public void deleteTransaction(long userId, long transactionId) {
        MfTransaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fund transaction not found"));
        if (transaction.getLedgerTransactionId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This purchase comes from a SIP in your bank transactions. Unlink the SIP to remove it.");
        }
        if (transaction.getType() == MfTxnType.BUY) {
            List<MfTransaction> remaining = new ArrayList<>(
                    transactionRepository.findByUserIdAndSchemeCode(userId, transaction.getSchemeCode()));
            remaining.removeIf(t -> t.getId().equals(transaction.getId()));
            if (!PortfolioMath.neverOversold(toTrades(remaining))) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "A later redemption sold these units. Delete that redemption first.");
            }
        }
        transactionRepository.delete(transaction);
    }

    // ------------------------------------------------------------------ SIPs from the bank statement

    /** Repeating SIP-like debits that aren't linked to a fund yet, most frequent first. */
    @Transactional(readOnly = true)
    public List<SipSuggestion> sipSuggestions(long userId) {
        LocalDate from = AppTime.today(clock).minusMonths(SUGGESTION_LOOKBACK_MONTHS);
        Set<String> linked = sipLinkRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(SipLink::getMatchKey).collect(Collectors.toSet());

        Map<String, List<Transaction>> groups = new LinkedHashMap<>();
        for (Transaction t : transactionRepository.outgoingSince(userId, from)) {
            String category = t.getCategory() == null ? null : t.getCategory().getName();
            if (SipDetector.looksLikeSip(t.getDescription(), t.getMerchant(), category)) {
                groups.computeIfAbsent(SipDetector.key(t.getDescription()), k -> new ArrayList<>()).add(t);
            }
        }

        return groups.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2 && !e.getKey().isBlank() && !linked.contains(e.getKey()))
                .map(e -> {
                    List<Transaction> debits = e.getValue();
                    Transaction latest = debits.get(debits.size() - 1);
                    return new SipSuggestion(e.getKey(), latest.getDescription(), debits.size(), typicalAmount(debits),
                            latest.getTxnDate(), SipDetector.fundQuery(latest.getDescription(), latest.getMerchant()));
                })
                .sorted(Comparator.comparingInt(SipSuggestion::occurrences).reversed()
                        .thenComparing(SipSuggestion::label))
                .toList();
    }

    /** Links a SIP to a fund and turns its debits into purchases. The fund must already be in SchemeCache. */
    @Transactional
    public void linkSip(long userId, SipLinkRequest request) {
        String key = request.matchKey().trim();
        if (sipLinkRepository.existsByUserIdAndMatchKey(userId, key)) {
            throw new ApiException(HttpStatus.CONFLICT, "That SIP is already linked to a fund");
        }
        SchemeInfo scheme = schemeCache.find(request.schemeCode())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Choose a fund from the search"));

        List<Transaction> debits = transactionRepository.outgoingSince(userId, LocalDate.of(1900, 1, 1)).stream()
                .filter(t -> SipDetector.key(t.getDescription()).equals(key))
                .toList();
        if (debits.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "None of your bank transactions match that SIP");
        }

        SipLink link = new SipLink();
        link.setUser(userRepository.getReferenceById(userId));
        link.setSchemeCode(scheme.schemeCode());
        link.setMatchKey(key);
        link.setLabel(truncate(debits.get(debits.size() - 1).getDescription(), 255));
        sipLinkRepository.save(link);

        syncSips(userId);
    }

    @Transactional
    public void unlinkSip(long userId, long linkId) {
        SipLink link = sipLinkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SIP not found"));
        sipLinkRepository.delete(link);   // the database deletes its purchases too
    }

    /**
     * Brings SIP purchases in line with the bank statement: new matching debits become purchases,
     * edited ones are updated, and ones that no longer match are removed. A debit whose NAV isn't
     * published yet (today's, usually) waits for the nightly refresh. Uses cached NAVs only.
     * Returns how many purchases were added.
     */
    @Transactional
    public int syncSips(long userId) {
        List<SipLink> links = sipLinkRepository.findByUserIdOrderByIdAsc(userId);
        List<MfTransaction> existing = transactionRepository.findFromSips(userId);
        if (links.isEmpty() && existing.isEmpty()) {
            return 0;
        }

        Map<Long, MfTransaction> byLedgerId = new HashMap<>();
        existing.forEach(t -> byLedgerId.put(t.getLedgerTransactionId(), t));
        Map<String, SipLink> linkByKey = links.stream().collect(Collectors.toMap(SipLink::getMatchKey, l -> l));

        LocalDate today = AppTime.today(clock);
        Map<Integer, NavSeries> navCache = new HashMap<>();
        Set<Long> stillMatching = new HashSet<>();
        int added = 0;

        for (Transaction debit : transactionRepository.outgoingSince(userId, LocalDate.of(1900, 1, 1))) {
            SipLink link = linkByKey.get(SipDetector.key(debit.getDescription()));
            if (link == null) {
                continue;
            }
            stillMatching.add(debit.getId());
            long amount = -debit.getAmountPaise();

            MfTransaction purchase = byLedgerId.get(debit.getId());
            boolean unchanged = purchase != null
                    && purchase.getAmountPaise() == amount
                    && purchase.getTxnDate().equals(debit.getTxnDate())
                    && purchase.getSchemeCode().equals(link.getSchemeCode());
            if (unchanged) {
                continue;
            }

            NavSeries series = navCache.computeIfAbsent(link.getSchemeCode(),
                    code -> schemeCache.series(code, LocalDate.of(1900, 1, 1), today));
            NavPoint nav = series.publishedTradeOn(debit.getTxnDate()).orElse(null);
            if (nav == null) {
                continue;
            }

            if (purchase == null) {
                purchase = new MfTransaction();
                purchase.setUser(userRepository.getReferenceById(userId));
                purchase.setType(MfTxnType.BUY);
                purchase.setLedgerTransactionId(debit.getId());
                added++;
            }
            purchase.setSipLink(link);
            purchase.setSchemeCode(link.getSchemeCode());
            purchase.setTxnDate(debit.getTxnDate());
            purchase.setAmountPaise(amount);
            purchase.setUnits(PortfolioMath.unitsFor(amount, nav.nav()));
            purchase.setNav(nav.nav());
            transactionRepository.save(purchase);
        }

        // A debit that was edited so it no longer looks like the SIP isn't a purchase any more
        for (MfTransaction purchase : existing) {
            if (!stillMatching.contains(purchase.getLedgerTransactionId())) {
                transactionRepository.delete(purchase);
            }
        }
        return added;
    }

    // ------------------------------------------------------------------ helpers

    private static List<PortfolioMath.Trade> toTrades(List<MfTransaction> transactions) {
        return transactions.stream()
                .map(t -> new PortfolioMath.Trade(t.getTxnDate(), t.getType(), t.getAmountPaise(), t.getUnits()))
                .toList();
    }

    /** The median debit, so one odd month doesn't skew it. */
    private static long typicalAmount(List<Transaction> debits) {
        List<Long> amounts = debits.stream().map(t -> -t.getAmountPaise()).sorted().toList();
        return amounts.get(amounts.size() / 2);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
