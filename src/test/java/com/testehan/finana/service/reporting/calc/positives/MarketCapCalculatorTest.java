package com.testehan.finana.service.reporting.calc.positives;

import com.testehan.finana.model.CompanyOverview;
import com.testehan.finana.model.reporting.ReportItem;
import com.testehan.finana.repository.CompanyOverviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketCapCalculator Tests")
class MarketCapCalculatorTest {

    @Mock
    private CompanyOverviewRepository companyOverviewRepository;

    private MarketCapCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MarketCapCalculator(companyOverviewRepository);
    }

    private CompanyOverview createCompanyOverview(String marketCap) {
        CompanyOverview overview = new CompanyOverview();
        overview.setMarketCap(marketCap);
        return overview;
    }

    @Test
    @DisplayName("Should return -1 score when company overview not found")
    void shouldReturnMinusOneWhenCompanyNotFound() {
        when(companyOverviewRepository.findBySymbol("INVALID")).thenReturn(Optional.empty());

        ReportItem result = calculator.calculate("INVALID");

        assertThat(result.getScore()).isEqualTo(-1);
        assertThat(result.getExplanation()).contains("Could not retrieve");
    }

    @Test
    @DisplayName("Should return score 5 when market cap < $2B")
    void shouldReturnScoreFiveWhenMarketCapVerySmall() {
        CompanyOverview overview = createCompanyOverview("1000000000"); // $1B
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL");

        assertThat(result.getScore()).isEqualTo(5);
        assertThat(result.getExplanation()).contains("less than $2B");
    }

    @Test
    @DisplayName("Should return score 5 when market cap is less than $2B")
    void shouldReturnScoreFiveWhenMarketCapLessThanTwoBillion() {
        CompanyOverview overview = createCompanyOverview("1999999999"); // just under $2B
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL");

        assertThat(result.getScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should return score 3 when market cap is between $2B and $5B")
    void shouldReturnScoreThreeWhenMarketCapMedium() {
        CompanyOverview overview = createCompanyOverview("3000000000"); // $3B
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL");

        assertThat(result.getScore()).isEqualTo(3);
        assertThat(result.getExplanation()).contains("less than $5B");
    }

    @Test
    @DisplayName("Should return score 3 when market cap is between $2B and $5B")
    void shouldReturnScoreThreeWhenMarketCapBetweenTwoAndFiveBillion() {
        CompanyOverview overview = createCompanyOverview("4000000000"); // $4B - between $2B and $5B
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL");

        assertThat(result.getScore()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should return score 0 when market cap > $5B")
    void shouldReturnScoreZeroWhenMarketCapLarge() {
        CompanyOverview overview = createCompanyOverview("10000000000"); // $10B
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL");

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getExplanation()).contains("greater than $5B");
    }

    @Test
    @DisplayName("Should format market cap correctly for billions")
    void shouldFormatMarketCapForBillions() {
        CompanyOverview overview = createCompanyOverview("1500000000"); // $1.5B
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL");

        assertThat(result.getExplanation()).contains("B");
    }

    @Test
    @DisplayName("Should format market cap correctly for millions")
    void shouldFormatMarketCapForMillions() {
        CompanyOverview overview = createCompanyOverview("500000000"); // $500M
        when(companyOverviewRepository.findBySymbol("AAPL")).thenReturn(Optional.of(overview));

        ReportItem result = calculator.calculate("AAPL");

        assertThat(result.getExplanation()).contains("M");
    }
}
