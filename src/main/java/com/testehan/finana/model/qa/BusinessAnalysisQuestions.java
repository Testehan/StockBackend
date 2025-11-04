package com.testehan.finana.model.qa;

import java.util.List;

public class BusinessAnalysisQuestions {

    public static final List<Question> QUESTIONS = List.of(
            new Question("revenue_model", "How does this company make money?"),
            new Question("customer_profile_and_motivation", "Who are the company’s customers, and why do they buy the product or service?"),
            new Question("problem_criticality", "What problem does the company solve, and how critical is that problem for customers?"),
            new Question("competitive_advantage", "What is the company’s competitive advantage (moat), if any?"),
            new Question("moat_durability", "How strong and durable is that competitive advantage over time?"),
            new Question("business_lifecycle_stage", "Where is the company in its business lifecycle?"),
            new Question("growth_drivers", "What are the primary drivers of growth going forward?"),
            new Question("growth_constraints", "What are the main constraints or bottlenecks to that growth?"),
            new Question("key_business_risks", "What are the biggest business risks that could materially impair the company?"),
            new Question("thesis_break_conditions", "What would have to be true for the core investment thesis to break?"),
            new Question("silver_bullet", "If the company had a ‘silver bullet,’ which competitor would it most want to use it against?")
    );
}
