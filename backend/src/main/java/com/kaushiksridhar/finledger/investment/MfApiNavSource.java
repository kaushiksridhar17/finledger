package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fund data from mfapi.in, a free API over AMFI's official daily NAV files. No key needed.
 *   GET /mf/search?q=parag  ->  [{"schemeCode": 122639, "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth"}, ...]
 *   GET /mf/122639          ->  {"meta": {...}, "data": [{"date": "25-09-2026", "nav": "89.95800"}, ...]}  (newest first)
 */
@Component
@ConditionalOnProperty(name = "finledger.mf.source", havingValue = "mfapi", matchIfMissing = true)
public class MfApiNavSource implements NavSource {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int MAX_RESULTS = 25;

    private final RestClient http;

    public MfApiNavSource(@Value("${finledger.mf.base-url:https://api.mfapi.in}") String baseUrl) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(Integer schemeCode, String schemeName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scheme(Meta meta, List<Point> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("fund_house") String fundHouse,
            @JsonProperty("scheme_category") String schemeCategory,
            @JsonProperty("scheme_code") Integer schemeCode,
            @JsonProperty("scheme_name") String schemeName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Point(String date, String nav) {
    }

    @Override
    public List<SchemeHit> search(String query) {
        try {
            Hit[] hits = http.get()
                    .uri(uri -> uri.path("/mf/search").queryParam("q", query).build())
                    .retrieve()
                    .body(Hit[].class);
            if (hits == null) {
                return List.of();
            }
            return Arrays.stream(hits)
                    .filter(h -> h.schemeCode() != null && h.schemeName() != null)
                    .limit(MAX_RESULTS)
                    .map(h -> new SchemeHit(h.schemeCode(), h.schemeName().trim()))
                    .toList();
        } catch (RestClientException e) {
            throw new NavUnavailableException("mfapi.in search failed", false, e);
        }
    }

    @Override
    public SchemeData fetch(int schemeCode) {
        Scheme scheme;
        try {
            scheme = http.get().uri("/mf/{code}", schemeCode).retrieve().body(Scheme.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new NavUnavailableException("No fund with scheme code " + schemeCode, true, e);
        } catch (RestClientException e) {
            throw new NavUnavailableException("mfapi.in didn't answer for scheme " + schemeCode, false, e);
        }

        if (scheme == null || scheme.meta() == null || scheme.meta().schemeName() == null || scheme.data() == null) {
            throw new NavUnavailableException("No fund with scheme code " + schemeCode, true, null);
        }

        List<NavPoint> navs = new ArrayList<>();
        for (Point point : scheme.data()) {
            NavPoint parsed = parse(point);
            if (parsed != null) {
                navs.add(parsed);
            }
        }
        if (navs.isEmpty()) {
            throw new NavUnavailableException("No NAVs for scheme code " + schemeCode, true, null);
        }

        Meta meta = scheme.meta();
        return new SchemeData(schemeCode, meta.schemeName().trim(), meta.fundHouse(), meta.schemeCategory(), navs);
    }

    /** Skips the odd row with a missing or zero NAV instead of failing the whole fund. */
    private static NavPoint parse(Point point) {
        if (point == null || point.date() == null || point.nav() == null) {
            return null;
        }
        try {
            BigDecimal nav = new BigDecimal(point.nav().trim());
            if (nav.signum() <= 0) {
                return null;
            }
            return new NavPoint(LocalDate.parse(point.date().trim(), DATE), nav);
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }
}
