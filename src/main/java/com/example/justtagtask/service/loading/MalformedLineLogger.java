package com.example.justtagtask.service.loading;

import com.example.justtagtask.model.canonical.AdRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MalformedLineLogger implements SkipListener<AdRecord, AdRecord> {

    @Override
    public void onSkipInRead(Throwable throwable) {
        log.warn("Skipped a malformed line: {}", throwable.getMessage());
    }

}
