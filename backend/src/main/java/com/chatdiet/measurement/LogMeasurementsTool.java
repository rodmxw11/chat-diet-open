package com.chatdiet.measurement;

import com.chatdiet.food.LoggedAtResolver;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Function;

/** IntentTool that logs a tape-measurement session and echoes the Navy body-fat estimate. */
@Component
@IntentTool(
        name = "log_measurements",
        intents = {"log_measurements"},
        description = "Log body tape measurements in inches - any of waist, neck, hip - for tracking fat loss."
)
public class LogMeasurementsTool implements Function<LogMeasurementsRequest, ToolResult> {

    /** Anything outside this range is a unit mix-up or a typo, not an inch measurement. */
    private static final double MIN_INCHES = 8;
    private static final double MAX_INCHES = 120;

    private final BodyMeasurementRepository repository;
    private final MeasurementService measurementService;

    public LogMeasurementsTool(BodyMeasurementRepository repository, MeasurementService measurementService) {
        this.repository = repository;
        this.measurementService = measurementService;
    }

    @Override
    public ToolResult apply(LogMeasurementsRequest request) {
        if (request.waistIn() == null && request.neckIn() == null && request.hipIn() == null) {
            return new ToolResult.NeedsClarification("Which measurement - waist, neck, or hip, in inches?", request);
        }
        for (var value : new Double[] {request.waistIn(), request.neckIn(), request.hipIn()}) {
            if (value != null && (value < MIN_INCHES || value > MAX_INCHES)) {
                return new ToolResult.NeedsClarification(
                        "%s inches doesn't look right - was that in centimeters?".formatted(format(value)), request);
            }
        }

        var measuredAt = LoggedAtResolver.resolve(LoggedAtResolver.parse(request.measuredAt()));
        var saved = repository.save(new BodyMeasurement(measuredAt, request.waistIn(), request.neckIn(), request.hipIn()));

        var parts = new ArrayList<String>();
        if (saved.waistIn() != null) parts.add("waist " + format(saved.waistIn()) + " in");
        if (saved.neckIn() != null) parts.add("neck " + format(saved.neckIn()) + " in");
        if (saved.hipIn() != null) parts.add("hip " + format(saved.hipIn()) + " in");
        var message = new StringBuilder("Logged ").append(String.join(", ", parts)).append('.');

        var view = measurementService.history().stream().filter(v -> v.id().equals(saved.id())).findFirst();
        if (view.isPresent() && view.get().bodyFatPct() != null) {
            message.append(" Navy body-fat estimate: ").append(format(view.get().bodyFatPct())).append('%');
            if (view.get().neckCarried()) {
                message.append(" (using your last neck measurement)");
            }
            message.append('.');
        } else if (!measurementService.profileComplete()) {
            message.append(" (No body-fat estimate: sex and height aren't set in the app's profile config.)");
        }
        return new ToolResult.Success(message.toString(), saved);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
