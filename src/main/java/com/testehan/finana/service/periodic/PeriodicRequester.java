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

    List<String> tickers = new ArrayList<>(Arrays.asList(
            // Software / SaaS / Cloud
            "CRM","NOW","ORCL","WDAY","ADP","INTC","IBM","HUBS","OKTA","ZSAN",
            "ANSS","TYL","PTC","ZEN","S","MDBX","DOMO","VRNS","BLZE","NCNO",
            "MSTR","DBX","SMAR","EGHT","RNGX","U","PATH","SSTK","DOCU",

            // Semiconductors / Hardware
            "ASML","TSM","UMC","ADI","TXN","MPWR","LSCC","OLED","ONTO","ENTG",
            "AMKR","COHU","FORM","DIOD","NXST","POWI","IPGP","ACLX","AXTI","AEHR",

            // Consumer / Internet / Media
            "ROKU","PINS","SNAP","MTCH","BMBL","ETSY","CHWY","W","OSTK","YETI",
            "CROX","DECK","SKX","HBI","VFC","COLM","FIGS","KTB","PLCE","GOOS",

            // Healthcare / MedTech / Biotech
            "VRTX","REGN","BIIB","ALNY","IONS","INCY","EXEL","NBIX","ACAD","ICUI",
            "PODD","MASI","RGEN","GKOS","IRTC","CRSP","EDIT","BEAM","NTLA","SRPT",

            // Industrials / Defense / Manufacturing
            "LMT","NOC","GD","TXT","ITT","BWXT","HWM","RBC","CW","FTAI",
            "TDG","AME","GWW","FASTX","ROCK","AOS","IR","XYL","PNR","ITW",

            // Transportation / Logistics
            "ODFL","SAIA","WERN","JBHT","KNX","RXO","SNDR","ARCB","ULH","DSKE",

            // Clean Tech / Materials (non-oil)
            "ENPH","SEDG","RUN","ARRY","NXT","PLUG","BLDP","FCEL","QS","SLDP",
            "ALB","LTHM","MP","CLF","X","ATI","CRS","CMC","STLD","NUE",

            // Gaming / Entertainment / Misc Tech
            "EA","U","RBLX","TTWOZ","PLAY","PENN","DKNG","GAN","RSI","SKLZ",
            "IAC","ANGI","ZD","YELP","FVRR","UPWK","TASK","ASUR","PAYO","RELY",

            // Networking / Infra / Cyber adj
            "JNPR","FFIV","VIAV","CALX","LITE","INFN","COMM","CMBM","HLIT","EXTR",

            // Additional diversified tech / growth
            "AXNX","BLBD","CVCO","STRA","ATKR","BECN","KFY","HURN","WING","SHAK"
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
