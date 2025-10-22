package com.testehan.finana.service.periodic;

import com.testehan.finana.controller.StockController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PeriodicRequester {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicRequester.class);

    private final List<String> tickers = new ArrayList<>(Arrays.asList(
            "CWAN", "YOU", "PAYC", "DUOL", "DAVE",
            "PLUS", "IDCC", "ALRM", "NATL", "GTM",
            "ADEA", "PCTY", "PEGA", "APPF", "SPSC",
            "BL", "LYFT", "DBD", "CVLT", "AGYS",
            "QTWO", "LIF", "RNG", "SOUN", "NAVN",
            "NCNO", "INTA", "KVYO", "TTAN", "VERX",
            "WK", "PTRN", "ALKT", "ESTC", "FROG",
            "FRSH", "GRND", "BLKB", "BRZE", "BULL",
            "CCCC", "CHYM", "ASAN", "BILL"
    ));

    private final StockController stockController;
    private final AtomicInteger index = new AtomicInteger(0);

    public PeriodicRequester(StockController stockController, RestClient.Builder builder) {
        this.stockController = stockController;
    }

    @Scheduled(fixedRate = 60_000) // every 1 minute
    public void callEndpoint() {
        int currentIndex = index.getAndIncrement();

        if (currentIndex >= tickers.size()) {
            // All values processed — nothing more to do
            LOGGER.info("All tickers were processed.");
            return;
        }

        String ticket = tickers.get(currentIndex);
        LOGGER.info("Processing ticker: " + ticket);

        try {
            stockController.getCompanyOverview(ticket);

        } catch (Exception ex) {
            // Always catch exceptions in @Scheduled methods
            ex.printStackTrace();
        }
    }
}
