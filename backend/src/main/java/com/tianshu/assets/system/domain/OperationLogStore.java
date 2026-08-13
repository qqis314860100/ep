package com.tianshu.assets.system.domain;

import java.util.List;

public interface OperationLogStore {

    OperationLog append(OperationLog log);

    List<OperationLog> query(OperationLogCriteria criteria);

    long count(OperationLogCriteria criteria);
}
