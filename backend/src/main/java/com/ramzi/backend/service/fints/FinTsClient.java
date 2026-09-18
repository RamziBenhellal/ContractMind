package com.ramzi.backend.service.fints;

import java.util.List;

/**
 * Schnittstelle zum Python-FinTS-Dienst. In Tests wird die Implementierung gemockt.
 */
public interface FinTsClient {

    List<FinTsModels.BankInfo> searchBanks(String query);

    FinTsModels.SessionStartResponse startSession(String blz, String loginId, String pin);

    List<FinTsModels.FinTsAccount> confirmTan(String sessionId, String tanMethodId, String tan);

    FinTsModels.SyncResponse sync(String blz, String loginId, String pin);
}
