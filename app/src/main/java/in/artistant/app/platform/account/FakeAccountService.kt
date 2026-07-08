package `in`.artistant.app.platform.account

/**
 * In-memory [AccountService] for tests / previews (the iOS `FakeAccountService`
 * twin). [deleteError] makes deleteAccount throw (drives the "delete failed →
 * surface, don't fake success" path); [exportResult] is what exportData returns,
 * and [exportError] makes it throw. [deleteCalls] records invocations so a test
 * can assert the server call happened.
 */
class FakeAccountService(
    var deleteError: Throwable? = null,
    var exportResult: ExportResult = ExportResult.Inline("{}"),
    var exportError: Throwable? = null,
) : AccountService {

    var deleteCalls = 0
        private set
    var exportCalls = 0
        private set

    override suspend fun deleteAccount() {
        deleteCalls++
        deleteError?.let { throw it }
    }

    override suspend fun exportData(): ExportResult {
        exportCalls++
        exportError?.let { throw it }
        return exportResult
    }
}
