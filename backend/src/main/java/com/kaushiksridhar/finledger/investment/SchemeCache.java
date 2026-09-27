package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.kaushiksridhar.finledger.common.ApiException;

/**
 * Keeps fund details and NAV history in the database, so pages never wait on mfapi.in.
 * A fund is fetched the first time anyone uses it, then refreshed nightly (MfNightlyRefresh).
 *
 * Writes are plain SQL upserts ("insert, or update if it's already there"), so two requests
 * fetching the same fund at once can't clash. Deliberately not @Transactional: a failed fetch
 * here must never roll back the caller's work.
 */
@Component
public class SchemeCache {

    /** NAV rows per INSERT statement. One statement per row would mean thousands of round trips per fund. */
    private static final int NAV_ROWS_PER_INSERT = 500;

    /** On a refresh, the last month is rewritten too, in case a NAV was corrected. */
    private static final int REWRITE_DAYS = 30;

    // The latest NAV is only filled in after the history is written (see store)
    private static final String UPSERT_SCHEME = """
            INSERT INTO mf_schemes (scheme_code, name, fund_house, category)
            VALUES (?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE name = new.name, fund_house = new.fund_house, category = new.category
            """;

    private static final String SET_LATEST_NAV = """
            UPDATE mf_schemes SET latest_nav = ?, latest_nav_date = ?, refreshed_at = ? WHERE scheme_code = ?
            """;

    private static final String SELECT_SCHEME = """
            SELECT scheme_code, name, fund_house, category, latest_nav, latest_nav_date
            FROM mf_schemes WHERE scheme_code = ?
            """;

    private final NavSource navSource;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SchemeCache(NavSource navSource, JdbcTemplate jdbcTemplate, Clock clock) {
        this.navSource = navSource;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** A fund's details with its latest NAV. latestNav is null only if it has never been fetched. */
    public record SchemeInfo(int schemeCode, String name, String fundHouse, String category,
            BigDecimal latestNav, LocalDate latestNavDate) {
    }

    public List<SchemeHit> search(String query) {
        try {
            return navSource.search(query);
        } catch (NavUnavailableException e) {
            throw unavailable();
        }
    }

    /** Makes sure a fund and its NAV history are in the database, fetching them if not. */
    public SchemeInfo ensure(int schemeCode) {
        Optional<SchemeInfo> cached = find(schemeCode);
        if (cached.isPresent() && cached.get().latestNav() != null) {
            return cached.get();
        }
        return refresh(schemeCode);
    }

    /** Fetches the latest details and NAVs from the source and stores them. */
    public SchemeInfo refresh(int schemeCode) {
        SchemeData data;
        try {
            data = navSource.fetch(schemeCode);
        } catch (NavUnavailableException e) {
            if (e.isNotFound()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "There's no fund with scheme code " + schemeCode);
            }
            throw unavailable();
        }
        store(data);
        return find(schemeCode).orElseThrow();
    }

    public Optional<SchemeInfo> find(int schemeCode) {
        return jdbcTemplate.query(SELECT_SCHEME, SchemeCache::toInfo, schemeCode).stream().findFirst();
    }

    public Map<Integer, SchemeInfo> findAll(Collection<Integer> schemeCodes) {
        Map<Integer, SchemeInfo> found = new LinkedHashMap<>();
        for (Integer code : schemeCodes) {
            find(code).ifPresent(info -> found.put(code, info));
        }
        return found;
    }

    /** NAVs between two dates (inclusive). */
    public NavSeries series(int schemeCode, LocalDate from, LocalDate to) {
        List<NavPoint> points = jdbcTemplate.query("""
                SELECT nav_date, nav FROM mf_nav_history
                WHERE scheme_code = ? AND nav_date BETWEEN ? AND ?
                ORDER BY nav_date
                """,
                (rs, row) -> new NavPoint(rs.getObject("nav_date", LocalDate.class), rs.getBigDecimal("nav")),
                schemeCode, from, to);
        return new NavSeries(points);
    }

    /** Every fund someone holds or has a SIP in, for the nightly refresh. */
    public List<Integer> codesInUse() {
        return jdbcTemplate.queryForList(
                "SELECT scheme_code FROM mf_transactions UNION SELECT scheme_code FROM mf_sip_links", Integer.class);
    }

    /**
     * Writes the fund, then its NAV history, then its latest NAV. A fund only counts as cached once it has a
     * latest NAV (see ensure), so a request arriving mid-way fetches it again rather than seeing half a history.
     */
    private void store(SchemeData data) {
        jdbcTemplate.update(UPSERT_SCHEME, data.schemeCode(), truncate(data.name(), 200), truncate(data.fundHouse(), 100),
                truncate(data.category(), 120));

        // Only NAVs that are new (plus the last month) need writing; the first fetch writes everything
        LocalDate stored = jdbcTemplate.queryForObject(
                "SELECT MAX(nav_date) FROM mf_nav_history WHERE scheme_code = ?", LocalDate.class, data.schemeCode());
        LocalDate from = stored == null ? LocalDate.MIN : stored.minusDays(REWRITE_DAYS);
        List<NavPoint> toWrite = data.navs().stream().filter(p -> !p.date().isBefore(from)).toList();

        for (int start = 0; start < toWrite.size(); start += NAV_ROWS_PER_INSERT) {
            List<NavPoint> chunk = toWrite.subList(start, Math.min(start + NAV_ROWS_PER_INSERT, toWrite.size()));
            StringBuilder sql = new StringBuilder("INSERT INTO mf_nav_history (scheme_code, nav_date, nav) VALUES ");
            List<Object> args = new ArrayList<>(chunk.size() * 3);
            for (int i = 0; i < chunk.size(); i++) {
                sql.append(i == 0 ? "(?, ?, ?)" : ", (?, ?, ?)");
                args.add(data.schemeCode());
                args.add(chunk.get(i).date());
                args.add(scaled(chunk.get(i).nav()));
            }
            sql.append(" AS new ON DUPLICATE KEY UPDATE nav = new.nav");
            jdbcTemplate.update(sql.toString(), args.toArray());
        }

        NavPoint latest = data.navs().stream().max(Comparator.comparing(NavPoint::date)).orElseThrow();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbcTemplate.update(SET_LATEST_NAV, scaled(latest.nav()), latest.date(), now, data.schemeCode());
    }

    private static SchemeInfo toInfo(ResultSet rs, int row) throws SQLException {
        return new SchemeInfo(
                rs.getInt("scheme_code"),
                rs.getString("name"),
                rs.getString("fund_house"),
                rs.getString("category"),
                rs.getBigDecimal("latest_nav"),
                rs.getObject("latest_nav_date", LocalDate.class));
    }

    private static BigDecimal scaled(BigDecimal nav) {
        return nav.setScale(5, RoundingMode.HALF_UP);
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Couldn't get fund prices from mfapi.in right now. Please try again in a minute.");
    }
}
