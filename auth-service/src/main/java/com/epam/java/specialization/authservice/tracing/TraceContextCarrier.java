package com.epam.java.specialization.authservice.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TraceContextCarrier {

    public static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContextCarrier(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    /** The {@code traceparent} of the current span, or {@code null} outside a trace. */
    public String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /**
     * Starts a span that continues the trace of {@code traceparent}, or a new trace when there is none.
     * The caller puts it in scope with {@link Tracer#withSpan(Span)} and ends it.
     */
    public Span startSpan(String name, String traceparent) {
        Span.Builder builder = traceparent == null
                ? tracer.spanBuilder()
                : propagator.extract(Map.of(TRACEPARENT, traceparent), Map::get);
        return builder.name(name).start();
    }

    public Tracer tracer() {
        return tracer;
    }
}
