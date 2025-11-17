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

    public void saveReverseDcfValuation(ReverseDcfValuation reverseDcfValuation) {
        reverseDcfValuation.setValuationDate(LocalDateTime.now().toString());
        String ticker = reverseDcfValuation.getDcfCalculationData().meta().ticker();
        Valuations valuations = valuationsRepository.findById(ticker).orElse(new Valuations());
        valuations.setTicker(ticker);
        valuations.getReverseDcfValuations().add(reverseDcfValuation);
        valuationsRepository.save(valuations);
    }

    public List<ReverseDcfValuation> getReverseDcfHistory(String ticker) {
        return valuationsRepository.findById(ticker)
                .map(Valuations::getReverseDcfValuations)
                .orElse(java.util.Collections.emptyList());
    }
}
