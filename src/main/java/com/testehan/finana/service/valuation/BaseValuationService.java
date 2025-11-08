package com.testehan.finana.service.valuation;

import com.testehan.finana.repository.BalanceSheetRepository;
import com.testehan.finana.repository.CashFlowRepository;
import com.testehan.finana.repository.CompanyOverviewRepository;
import com.testehan.finana.repository.IncomeStatementRepository;
import com.testehan.finana.repository.StockQuotesRepository;
import com.testehan.finana.repository.ValuationsRepository;
import com.testehan.finana.service.FMPService;
import com.testehan.finana.util.SafeParser;

public abstract class BaseValuationService {

    protected final CompanyOverviewRepository companyOverviewRepository;
    protected final StockQuotesRepository stockQuotesRepository;
    protected final IncomeStatementRepository incomeStatementRepository;
    protected final BalanceSheetRepository balanceSheetRepository;
    protected final CashFlowRepository cashFlowRepository;
    protected final ValuationsRepository valuationsRepository;
    protected final FMPService fmpService;
    protected final SafeParser safeParser;

    protected BaseValuationService(CompanyOverviewRepository companyOverviewRepository,
                                   StockQuotesRepository stockQuotesRepository,
                                   IncomeStatementRepository incomeStatementRepository,
                                   BalanceSheetRepository balanceSheetRepository,
                                   CashFlowRepository cashFlowRepository,
                                   ValuationsRepository valuationsRepository,
                                   FMPService fmpService,
                                   SafeParser safeParser) {
        this.companyOverviewRepository = companyOverviewRepository;
        this.stockQuotesRepository = stockQuotesRepository;
        this.incomeStatementRepository = incomeStatementRepository;
        this.balanceSheetRepository = balanceSheetRepository;
        this.cashFlowRepository = cashFlowRepository;
        this.valuationsRepository = valuationsRepository;
        this.fmpService = fmpService;
        this.safeParser = safeParser;
    }
}
