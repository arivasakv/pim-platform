package com.pim.backend.lov.repository;

import com.pim.backend.lov.model.ChangeRecord;

/** Where list change records are kept (FR-019; research R-11). Write-only in this feature. */
public interface ChangeRecordRepository {

    void insert(ChangeRecord changeRecord);
}
