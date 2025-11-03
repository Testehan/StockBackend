package com.testehan.finana.model;

public enum UserStockStatus {
    NEW,                        // just added, no work done
    RESEARCHING,                // working on this
    WATCHLIST,                  // good companies but not sure yet if good enough to be moved to buy candidates
    BUY_CANDIDATE,              // awesome companies where i wait for better price
    OWNED,
    PASS                      // i don't like something about this ..could be domain...risks..etc

}
