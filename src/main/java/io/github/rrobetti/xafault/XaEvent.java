package io.github.rrobetti.xafault;

import java.time.Instant;

public record XaEvent(
        long sequence,
        Instant time,
        String resourceId,
        ResourceKind resourceKind,
        String resourceInstanceId,
        XaOperation operation,
        EventPosition position,
        XidSnapshot xid,
        Integer flags,
        Boolean onePhase,
        Integer returnCode,
        XaError error,
        String threadName,
        long operationOrdinal,
        long resourceOperationOrdinal) {
}
