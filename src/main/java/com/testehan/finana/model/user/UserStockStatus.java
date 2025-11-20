package com.testehan.finana.model.user;

public enum UserStockStatus {
    NEW,                        // just added, no work done
    RESEARCHING,                // working on this
    WATCHLIST,                  // good companies but not sure yet if good enough to be moved to buy candidates
    BUY_CANDIDATE,              // awesome companies where i wait for better price
    OWNED,      // TODO maybe i should add more PASS_ statuses..with reasons..that way i can revisit some companies..
    // like PASS_VALUATION ? ... and/or maybe i should add "potential_starter" for 0-3% "potential_booster" 3-7% "potential_anchor" 7-15%
    PASS                      // i don't like something about this ..could be domain...risks..etc

}
