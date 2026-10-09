package com.chatdiet.measurement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Measurement history with Navy body-fat estimates, for the Measurements page. */
@RestController
public class MeasurementController {

    public record MeasurementsResponse(List<MeasurementService.MeasurementView> measurements, boolean profileComplete) {
    }

    private final MeasurementService measurementService;

    public MeasurementController(MeasurementService measurementService) {
        this.measurementService = measurementService;
    }

    @GetMapping("/api/measurements")
    public MeasurementsResponse measurements() {
        return new MeasurementsResponse(measurementService.history(), measurementService.profileComplete());
    }
}
