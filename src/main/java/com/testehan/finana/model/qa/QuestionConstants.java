package com.testehan.finana.model.qa;

import java.util.List;
import java.util.Optional;

public class QuestionConstants {

    public static final List<Question> BUSINESS_ANALYSIS_QUESTIONS = List.of(
            new Question("revenue_model", "How does this company make money?"),
            new Question("customer_profile_and_motivation", "Who are the company's customers, and why do they buy the product or service?"),
            new Question("problem_criticality", "What problem does the company solve, and how critical is that problem for customers?"),
            new Question("competitive_advantage", "What is the company's competitive advantage (moat), if any?"),
            new Question("moat_durability", "How strong and durable is that competitive advantage over time?"),
            new Question("business_lifecycle_stage", "Where is the company in its business lifecycle?"),
            new Question("growth_drivers", "What are the primary drivers of growth going forward?"),
            new Question("market_opportunity", "What is the size of the total addressable market (TAM), and what is the company's current market share and penetration potential?"),
            new Question("growth_constraints", "What are the main constraints or bottlenecks to that growth?"),
            new Question("key_business_risks", "What are the biggest business risks that could materially impair the company?"),
            new Question("thesis_break_conditions", "What would have to be true for the core investment thesis to break?"),
            new Question("silver_bullet", "If the company had a 'silver bullet,' which competitor would it most want to use it against?")
    );

    public static final List<Question> BUFFETT_QUESTIONS = List.of(
            new Question("buffet_predictable_economics", "Can I confidently predict what the economics of this business will look like in 10 or 20 years?"),
            new Question("buffet_no_genius_required", "Does this company require a genius to run it?"),
            new Question("buffet_no_tech_disruption", "Is the company subject to rapid technological change or disruption?"),
            new Question("buffet_pricing_power", "If the company raised its prices by 10% tomorrow, would they lose significant market share to competitors?"),
            new Question("buffet_must_have_product", "Does the company provide a product or service that people feel they must have, regardless of the economic climate?"),
            new Question("buffet_inflation_resistant", "Can this business grow its earnings during periods of high inflation without needing massive amounts of additional capital?"),
            new Question("buffet_toll_bridge", "Is this company an unregulated 'toll bridge'?"),
            new Question("buffet_share_of_mind", "Does the company possess a 'Share of Mind' rather than just a 'Share of Market'?"),
            new Question("buffet_capital_light", "Does the business require massive, ongoing capital expenditures just to stay competitive?"),
            new Question("buffet_candid_management", "Does management communicate candidly with shareholders, freely admitting mistakes in their annual letters rather than just highlighting successes?"),
            new Question("buffet_no_institutional_imperative", "Does management resist the 'Institutional Imperative'?"),
            new Question("buffet_rational_capital_allocation", "Does management have a proven track record of rational capital allocation?"),
            new Question("buffet_holding_period", "If the stock market were to close tomorrow and not reopen for five years, would I be perfectly happy holding this business?"),
            new Question("buffet_long_term_purchase", "Am I buying this because I think the stock will go up next month, or because I want to own a piece of this specific business?")
    );

    public static final List<Question> MUNGER_QUESTIONS = List.of(
            new Question("munger_inversion_death", "If we fast-forward five years and this company's stock has collapsed, what was the single most likely cause of death?"),
            new Question("munger_inversion_competitor_disrupt", "What is the easiest way a competitor could permanently disrupt this business model?"),
            new Question("munger_capital_needs", "Does the business require heavy ongoing capital infusions (inventory, capex, R&D) just to maintain its position, or does it throw off cash with minimal reinvestment?"),
            new Question("munger_inversion_survival", "Instead of asking how this company succeeds, what must not happen for this company to survive?"),
            new Question("munger_management_incentives", "How exactly is the C-suite compensated, and does it encourage long-term value creation or short-term stock pumping?"),
            new Question("munger_management_skin_in_game", "Do the founders or executives have a significant portion of their own net worth tied up in the stock?"),
            new Question("munger_sales_incentives", "Are the incentives of the company's salespeople aligned with the long-term success of the customer, or are they incentivized to just close the deal and run?"),
            new Question("munger_lollapalooza_positive", "Is this company currently benefiting from a 'Lollapalooza effect'—a confluence of multiple, unstoppable macro-trends acting heavily in its favor?"),
            new Question("munger_lollapalooza_negative", "Are there multiple negative forces (e.g., rising interest rates + changing consumer habits + new regulations) converging to work against them?"),
            new Question("munger_psychology_habit", "Does the company's product benefit from deep-seated psychological tendencies (like extreme habit, social proof, or FOMO) that make it incredibly painful for customers to quit?"),
            new Question("munger_moat_billion_test", "If you gave a highly competent competitor $10 billion and a decade to aggressively steal this company's market share, could they realistically do it?"),
            new Question("munger_moat_circle_competence", "Is the company staying strictly within its 'Circle of Competence', or are they engaging in 'diworsification' by making acquisitions in industries they don't truly understand?"),
            new Question("munger_avoid_stupidity_complexity", "Is the company relying on highly complex, opaque financial engineering to generate its profits?"),
            new Question("munger_avoid_stupidity_macro", "Is management trying to aggressively predict complex macroeconomic factors (like interest rates or commodity prices), or are they just focusing on making the core business resilient?"),
            new Question("munger_management_trustworthy", "Is management honest, rational, and trustworthy—or do we see any signs of questionable character, promotion of hype, or self-dealing?"),
            new Question("munger_valuation_margin_safety", "Is there a substantial margin of safety between the current price and a conservative estimate of intrinsic value, even in pessimistic scenarios?"),
            new Question("munger_valuation_psychology", "Is the current market valuation driven by extreme irrational exuberance or extreme pessimism, rather than the underlying math of the business?")
    );

    public static final List<Question> LYNCH_QUESTIONS = List.of(
            new Question("lynch_invest_what_you_know_mall", "If I walked through a shopping mall or talked to professionals in my industry, would I see people enthusiastically adopting this company's product?"),
            new Question("lynch_invest_what_you_know_explain", "Could I easily explain exactly how this company makes money to an 11-year-old using crayons?"),
            new Question("lynch_invest_what_you_know_fraction", "Is the company's product uniquely responsible for its success, or is it just a tiny fraction of a massive conglomerate's overall revenue?"),
            new Question("lynch_six_categories_type", "Which of the 'Six Categories' (Fast Growers, Stalwarts, Slow Growers, Cyclicals, Turnarounds, Asset Plays) does this company fall into?"),
            new Question("lynch_six_categories_expectations", "Are my expectations aligned with the reality of this company's category?"),
            new Question("lynch_six_categories_expansion", "If this is a 'Fast Grower', are they successfully duplicating a proven formula in new cities/markets, or are they just running out of room to expand?"),
            new Question("lynch_boring_disagreeable_product", "Does this company do something incredibly boring, dull, or disagreeable (like waste management, pest control, oil recycling, or funeral homes)?"),
            new Question("lynch_boring_disagreeable_wall_street", "Is the company largely ignored by major Wall Street analysts and under-owned by institutional investors?"),
            new Question("lynch_boring_disagreeable_rumor", "Is there a widespread, unfounded rumor or stigma depressing the stock price that doesn't actually affect the underlying business?"),
            new Question("lynch_garp_peg_ratio", "Is the company's P/E ratio equal to or less than its historical growth rate? (PEG ratio)"),
            new Question("lynch_garp_inventory", "Are inventories piling up faster than sales are growing?"),
            new Question("lynch_garp_balance_sheet", "Does the company have a 'fortress' balance sheet with enough net cash to survive a sudden industry recession without going bankrupt?"),
            new Question("lynch_management_insider_buying", "Are the company's insiders (executives and directors) aggressively buying shares of their own stock on the open market?"),
            new Question("lynch_management_di_worsification", "Is management engaging in 'diworsification' by blowing their cash on foolish acquisitions in industries they know nothing about?")
    );

    public static final List<Question> GARDNER_QUESTIONS = List.of(
            new Question("gardner_top_dog_industry", "Is this company the undisputed 'top dog' and first mover in a highly important, rapidly emerging industry?"),
            new Question("gardner_top_dog_new_industry", "Is the company literally creating a new industry that didn't exist 5 years ago, or completely redefining an old one?"),
            new Question("gardner_top_dog_thanos_snap", "If you removed this company from the world today, would consumers notice and care deeply?"),
            new Question("gardner_visionary_founder", "Is the company still led by its visionary, passionate founder?"),
            new Question("gardner_visionary_culture", "Does the company have a phenomenal workplace culture that attracts the absolute best engineers and talent in the world?"),
            new Question("gardner_visionary_skin_in_game", "Does leadership have a massive amount of 'skin in the game' (high insider ownership)?"),
            new Question("gardner_consumer_appeal", "Does the company have immense consumer appeal, creating a product or service that customers actively rave about to their friends?"),
            new Question("gardner_consumer_word_of_mouth", "Does the word-of-mouth marketing for this product drastically reduce the amount of money the company has to spend on traditional advertising?"),
            new Question("gardner_contrarian_overvalued", "Is this stock widely considered to be brutally 'overvalued' by traditional Wall Street analysts and financial media?"),
            new Question("gardner_contrarian_advantage", "Does the company possess a sustainable advantage (patents, visionary leadership, or sheer business momentum) that the market is currently underestimating?"),
            new Question("gardner_momentum_price_appreciation", "Has the stock already shown strong, historical price appreciation?"),
            new Question("gardner_momentum_business_momentum", "Is the company demonstrating extreme business momentum (hyper-growth in revenue), even if they are intentionally losing money on the bottom line to capture market share?")
    );

    public static final List<Question> DAMODARAN_QUESTIONS = List.of(
            new Question("damodaran_business_story", "1. First, tell me the fundamental business story of this company. What does it actually do, who are its customers, how does it make money today, and what is the plausible narrative for its future growth over the next 5–10 years? Be honest about what we know and what we don't."),
            new Question("damodaran_revenue_growth", "2. What is a realistic, defensible revenue growth rate for this company over the next 5 years and then in the stable growth phase? Base it on market size, competitive position, and historical trends — don't be optimistic just because the market is."),
            new Question("damodaran_operating_margins", "3. What operating margins can this company sustainably achieve in the high-growth phase and in stable growth? Explain the competitive and efficiency drivers that support (or constrain) those margins."),
            new Question("damodaran_reinvestment_roic", "4. How much reinvestment (capex + change in working capital) will be required to deliver the growth you just described? What Return on Invested Capital (ROIC) is realistic for this business, and why?"),
            new Question("damodaran_risk_discount_rate", "5. What is the appropriate cost of capital (or cost of equity) for valuing this company right now? Walk me through the key risk factors, beta, country risk if any, and your final discount rate."),
            new Question("damodaran_free_cash_flows", "6. Based on the growth, margins, and reinvestment assumptions above, what do you expect the free cash flows to the firm (or equity) to look like over the next 5–10 years? Show the key numbers, not just a summary."),
            new Question("damodaran_terminal_value", "7. Now let's talk about the terminal value. What stable growth rate, reinvestment rate, and exit assumptions are you using, and why are they consistent with the economics of a mature version of this business?"),
            new Question("damodaran_intrinsic_value", "8. Putting the pieces together, what is your best-estimate intrinsic value per share (or enterprise value) for this company today using a DCF? Give me both your base-case number and the key drivers behind it."),
            new Question("damodaran_scenario_analysis", "9. Run three quick scenarios — base case (what you just gave), optimistic, and pessimistic. How much does the value change in each case, and what are the biggest swing factors?"),
            new Question("damodaran_margin_safety", "10. Given your intrinsic value estimate, is the current market price providing a sufficient margin of safety? Be specific: how much upside or downside do you see, and under what conditions would you actually buy (or sell) this stock?"),
            new Question("damodaran_price_vs_value", "11. Right now, is the market pricing this stock based on fundamentals or on hype/exuberance/fear? In Damodaran terms, is the stock undervalued, fairly valued, or overvalued relative to its story and numbers?"),
            new Question("damodaran_key_risks", "12. What are the two or three biggest risks or assumptions that could make this entire valuation wrong? Be brutally honest — what could break the story?"),
            new Question("damodaran_competitive_moat", "13. How strong and durable is this company's competitive advantage? How will it affect long-term margins and growth, and what could erode it?"),
            new Question("damodaran_consistency_check", "14. Do all the pieces of this valuation story hang together logically? If the growth is high, do the reinvestment and ROIC numbers make sense? If the margins are high, is the competitive position defensible? Flag any inconsistencies."),
            new Question("damodaran_revisit_trigger", "15. What new information or change in fundamentals would cause you to materially revise this valuation upward or downward? In other words, what would make you change your mind?"),
            new Question("damodaran_final_judgment", "16. Finally, in your own words as a valuation professor: would you buy this stock today at the current price? Why or why not? Keep it concise but rigorous.")
    );

    public static final List<Question> EARNINGS_TRANSCRIPT_QUESTIONS = List.of(
            new Question("transcript_30_seconds_summary", "The 30-Second Summary"),
            new Question("transcript_evasion_tracker", "The Evasion Tracker"),
            new Question("transcript_buzzword_context_counter", "Buzzword Context Counter"),
            new Question("transcript_tone_shift_indicator", "Tone Shift Indicator")
    );

    public static List<Question> getGuruQuestions(String guruName) {
        return switch (guruName.toLowerCase()) {
            case "buffett" -> BUFFETT_QUESTIONS;
            case "munger" -> MUNGER_QUESTIONS;
            case "lynch" -> LYNCH_QUESTIONS;
            case "gardner" -> GARDNER_QUESTIONS;
            case "damodaran" -> DAMODARAN_QUESTIONS;
            default -> List.of();
        };
    }

    public static Optional<String> getGuruNameForQuestion(String questionId) {
        if (findInList(BUFFETT_QUESTIONS, questionId).isPresent()) {
            return Optional.of("Warren Buffett");
        }
        if (findInList(MUNGER_QUESTIONS, questionId).isPresent()) {
            return Optional.of("Charlie Munger");
        }
        if (findInList(LYNCH_QUESTIONS, questionId).isPresent()) {
            return Optional.of("Peter Lynch");
        }
        if (findInList(GARDNER_QUESTIONS, questionId).isPresent()) {
            return Optional.of("David Gardner");
        }
        if (findInList(DAMODARAN_QUESTIONS, questionId).isPresent()) {
            return Optional.of("Aswath Damodaran");
        }
        return Optional.empty();
    }

    public static Optional<Question> findQuestionById(String questionId) {
        return findInList(BUFFETT_QUESTIONS, questionId)
                .or(() -> findInList(MUNGER_QUESTIONS, questionId))
                .or(() -> findInList(LYNCH_QUESTIONS, questionId))
                .or(() -> findInList(GARDNER_QUESTIONS, questionId))
                .or(() -> findInList(DAMODARAN_QUESTIONS, questionId))
                .or(() -> findInList(BUSINESS_ANALYSIS_QUESTIONS, questionId))
                .or(() -> findInList(EARNINGS_TRANSCRIPT_QUESTIONS, questionId));
    }

    public static String getQuestionText(String questionId) {
        return findQuestionById(questionId)
                .map(Question::getText)
                .orElseThrow(() -> new IllegalArgumentException("Question not found with ID: " + questionId));
    }

    public static boolean isGuruQuestion(String questionId) {
        return !getGuruNameForQuestion(questionId).isEmpty();
    }

    public static final List<String> SEQUENTIAL_GURUS = List.of("damodaran");

    public static boolean isSequentialGuru(String guruName) {
        return SEQUENTIAL_GURUS.contains(guruName.toLowerCase());
    }

    public static boolean isDamodaranQuestion(String questionId) {
        return questionId.startsWith("damodaran_");
    }

    public static int getDamodaranQuestionIndex(String questionId) {
        for (int i = 0; i < DAMODARAN_QUESTIONS.size(); i++) {
            if (DAMODARAN_QUESTIONS.get(i).getId().equals(questionId)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isFirstDamodaranQuestion(String questionId) {
        return "damodaran_business_story".equals(questionId);
    }

    private static Optional<Question> findInList(List<Question> questions, String questionId) {
        return questions.stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst();
    }
}
