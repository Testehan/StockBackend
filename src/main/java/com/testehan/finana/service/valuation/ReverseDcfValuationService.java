package com.testehan.finana.service.valuation;

import com.testehan.finana.model.valuation.Valuations;
import com.testehan.finana.model.valuation.dcf.DcfCalculationData;
import com.testehan.finana.model.valuation.dcf.ReverseDcfOutput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfUserInput;
import com.testehan.finana.model.valuation.dcf.ReverseDcfValuation;
import com.testehan.finana.repository.*;
import com.testehan.finana.service.FMPService;
import com.testehan.finana.service.valuation.dcf.ReverseDCFValuationCalculator;
import com.testehan.finana.util.SafeParser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReverseDcfValuationService extends BaseValuationService {

    private final ReverseDCFValuationCalculator reverseDCFValuationCalculator;

    public ReverseDcfValuationService(CompanyOverviewRepository companyOverviewRepository,
                                      StockQuotesRepository stockQuotesRepository,
                                      IncomeStatementRepository incomeStatementRepository,
                                      BalanceSheetRepository balanceSheetRepository,
                                      CashFlowRepository cashFlowRepository,
                                      ValuationsRepository valuationsRepository,
                                      FMPService fmpService,
                                      SafeParser safeParser,
                                      ReverseDCFValuationCalculator reverseDCFValuationCalculator) {
        super(companyOverviewRepository, stockQuotesRepository, incomeStatementRepository,
                balanceSheetRepository, cashFlowRepository, valuationsRepository, fmpService, safeParser);
        this.reverseDCFValuationCalculator = reverseDCFValuationCalculator;
    }

    public ReverseDcfOutput calculateReverseDcfValuation(DcfCalculationData data, ReverseDcfUserInput input) {
        return reverseDCFValuationCalculator.calculateImpliedGrowthRate(data, input);
    }

    public void saveReverseDcfValuation(ReverseDcfValuation reverseDcfValuation, String userEmail) {
        reverseDcfValuation.setValuationDate(LocalDateTime.now().toString());
        String ticker = reverseDcfValuation.getDcfCalculationData().meta().ticker();
        String id = ticker + "_" + userEmail;
        Valuations valuations = valuationsRepository.findByTickerAndUserEmail(ticker, userEmail).orElse(new Valuations());
        valuations.setId(id);
        valuations.setTicker(ticker);
        valuations.setUserEmail(userEmail);
        valuations.getReverseDcfValuations().add(reverseDcfValuation);
        valuationsRepository.save(valuations);
    }

    public List<ReverseDcfValuation> getReverseDcfHistory(String ticker, String userEmail) {
        return valuationsRepository.findByTickerAndUserEmail(ticker, userEmail)
                .map(Valuations::getReverseDcfValuations)
                .orElse(java.util.Collections.emptyList());
    }

    public boolean deleteReverseDcfValuation(String ticker, String valuationDate, String userEmail) {
        Optional<Valuations> valuationsOpt = valuationsRepository.findByTickerAndUserEmail(ticker.toUpperCase(), userEmail);
        if (valuationsOpt.isEmpty()) {
            return false;
        }

        Valuations valuations = valuationsOpt.get();
        List<ReverseDcfValuation> reverseDcfValuations = valuations.getReverseDcfValuations();

        boolean removed = reverseDcfValuations.removeIf(v -> valuationDate.equals(v.getValuationDate()));

        if (removed) {
            valuationsRepository.save(valuations);
        }

        return removed;
    }
}
