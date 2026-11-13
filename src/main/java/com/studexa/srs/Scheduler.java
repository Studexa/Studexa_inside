package com.studexa.srs;

import java.time.Instant;
import java.util.Map;

public interface Scheduler {

    CardState schedule(CardState current, Rating rating, Instant now);

    Map<Rating, String> previewLabels(CardState current);
}
