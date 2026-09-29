package com.chatdiet.omron;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OmronImportControllerTest {

    private MockMvc mockMvc;
    private OmronCsvImportService importService;

    @BeforeEach
    void standaloneController() {
        importService = mock(OmronCsvImportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OmronImportController(importService, "unused")).build();
    }

    @Test
    void anUploadedExportIsImportedAndSummarized() throws Exception {
        when(importService.importUpload(anyString())).thenReturn(new OmronUploadResult(3, 2,
                LocalDateTime.of(2026, 9, 12, 5, 34), LocalDateTime.of(2026, 9, 16, 6, 0)));
        var csv = "Date,Time,Systolic (mmHg)\nSep 12 2026,05:34 am,141,76,63\n";

        mockMvc.perform(multipart("/api/omron/upload").file(new MockMultipartFile("file", "report.csv", "text/csv",
                        csv.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings").value(3))
                .andExpect(jsonPath("$.newReadings").value(2));

        verify(importService).importUpload(csv);
    }

    @Test
    void aFileThatIsNotAnOmronExportIsA400WithAMessage() throws Exception {
        when(importService.importUpload(anyString()))
                .thenThrow(new IllegalArgumentException("That doesn't look like an OMRON blood pressure export"));

        mockMvc.perform(multipart("/api/omron/upload").file(new MockMultipartFile("file", "foods.csv", "text/csv",
                        "name,calories\n".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("That doesn't look like an OMRON blood pressure export"));
    }
}
