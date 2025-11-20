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
//            all tech stocks from us
//            "AAOI", "AAPL", "ABTC", "ACIW", "ACLS", "ACMR", "ADAM", "ADBE", "ADEA", "ADI",
//            "ADSK", "AEIS", "AEYE", "AGYS", "AIFF", "AIP", "AIRG", "AISP", "AISPW", "ALAB",
//            "ALGM", "ALKT", "ALMU", "ALOT", "ALRM", "AMAT", "AMBA", "AMD", "AMKR", "AMPL",
//            "AMST", "APP", "APPF", "APPN", "APXT", "ASST", "ASTI", "ASUR", "ATHR", "ATOM",
//            "AUID", "AUR", "AUROW", "AUUD", "AUUDW", "AVGO", "AVNW", "AVPT", "AWRE", "AXTI",
//            "AZTA", "BAND", "BEEM", "BKYI", "BL", "BLBX", "BLIN", "BLKB", "BLZE", "BMBL",
//            "BNAI", "BNAIW", "BNZI", "BNZIW", "BRZE", "BSY", "CARG", "CCC", "CCIX", "CCIXW",
//            "CCLD", "CCLDO", "CCSI", "CDLX", "CDNS", "CDW", "CERS", "CERT", "CETX", "CEVA",
//            "CFLT", "CLMB", "CMRC", "CMTL", "CNXC", "CRDO", "CREX", "CRNC", "CRSR", "CRUS",
//            "CRWD", "CRWV", "CSAI", "CSGS", "CSPI", "CTSH", "CVLT", "CVV", "CXAI", "CXAIW",
//            "CYCU", "CYCUW", "CYN", "DAIC", "DAICW", "DASH", "DBX", "DDOG", "DH", "DIOD",
//            "DJT", "DJTWW", "DMRC", "DOCU", "DOMO", "DTCX", "DTST", "DTSTW", "DUOL", "DUOT",
//            "DVLT", "EGAN", "EGHT", "ENPH", "ERII", "EVCM", "EVER", "EVGO", "EVGOW", "EVLV",
//            "EVLVW", "EXFY", "FA", "FATN", "FNGR", "FORA", "FORM", "FROG", "FRSH", "FSLY",
//            "FTCI", "FTNT", "FUSE", "FUSEW", "GDRX", "GDYN", "GEG", "GEGGL", "GEN", "GENVR",
//            "GFS", "GIGGU", "GLOO", "GMGI", "GOAI", "GOOG", "GOOGL", "GSIT", "GTLB", "GTM",
//            "GXAI", "HCAT", "HCTI", "HLIT", "HOLO", "HOLOW", "HSTM", "IAC", "ICHR", "IDAI",
//            "IDN", "IMMR", "INDI", "INOD", "INSE", "INTA", "INTC", "INTU", "INVE", "IPDN",
//            "IPGP", "IPM", "IPWR", "ISSC", "IVDA", "IVDAW", "JKHY", "KDK", "KDKRW", "KE",
//            "KLAC", "KLTPN", "KOPN", "KTCC", "KUST", "KVHI", "LASR", "LIF", "LINK", "LOGI",
//            "LPSN", "LPTH", "LRCX", "LSCC", "LTRYW", "LZ", "MANH", "MAPS", "MAPSW", "MARA",
//            "MCHP", "MCHPP", "MCHX", "MDB", "META", "MGNI", "MITK", "MLGO", "MOBX", "MOBXW",
//            "MPWR", "MQ", "MRAM", "MRCY", "MRVL", "MSAI", "MSAIW", "MSFT", "MSGM", "MSTR",
//            "MTCH", "MTSI", "MU", "MVIS", "MXL", "MYPS", "MYPSW", "NAVN", "NCNO", "NIXX",
//            "NIXXW", "NRDS", "NSYS", "NTAP", "NTCT", "NTNX", "NTSK", "NTWK", "NVDA", "NVEC",
//            "NVTS", "OKTA", "OLED", "OMCL", "ON", "ONDS", "ONFO", "OPTX", "OPTXW", "OPXS",
//            "OS", "OSIS", "OSPN", "OSS", "PANW", "PAYS", "PCTY", "PDFS", "PDYN", "PDYNW",
//            "PEGA", "PENG", "PI", "PLAB", "PLTR", "PLUS", "PLXS", "PODC", "POWI", "PRCH",
//            "PRGS", "PRSO", "PTC", "PUBM", "PXLW", "QCOM", "QLYS", "QMCO", "QRVO", "QUBT",
//            "QUIK", "RAIN", "RAINW", "RBBN", "RCAT", "RDNW", "RDVT", "RELL", "RFIL", "RGTI",
//            "RGTIW", "RMBS", "RMSG", "RMSGW", "ROP", "RPD", "RSSS", "RUM", "RUMBW", "RXT",
//            "SABR", "SAIC", "SAIL", "SANM", "SBET", "SCKT", "SCSC", "SEGG", "SHLS", "SITM",
//            "SKYT", "SLAB", "SLNH", "SLNHP", "SLP", "SMCI", "SMSI", "SMTC", "SNAL", "SNDK",
//            "SNPS", "SOTK", "SOUN", "SOUNW", "SPSC", "SPT", "SPWR", "SPWRW", "SSNC", "SSYS",
//            "STRC", "STRD", "STRF", "STRK", "SVKO", "SWKS", "SYNA", "TACT", "TASK", "TBRG",
//            "TEAD", "TEM", "TENB", "TLS", "TRIP", "TRNR", "TTAN", "TTD", "TTMI", "TWAV",
//            "TXN", "TYGO", "UCTT", "ULY", "UPLD", "UPWK", "VECO", "VELO", "VERI", "VERX",
//            "VHUB", "VIAV", "VICR", "VISN", "VRAR", "VREX", "VRME", "VRNS", "VRSN", "VSAT",
//            "VTIX", "VUZI", "VWAV", "VWAVW", "WATT", "WAY", "WDAY", "WDC", "WFCF", "WGS",
//            "WGSWW", "WULF", "XBP", "XBPEW", "XRX", "XRXDW", "XTIA", "ZM", "ZS", "ZSPC"

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
