package com.genailab.api.model;

import com.genailab.ai.dto.ModelResponse;
import com.genailab.ai.service.ModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    /**
     *
     * <p>Returns all active models with their availability status.
     * Frontend uses this to populate the model selector dropdown.
     */
    @GetMapping
    public ResponseEntity<List<ModelResponse>> getModels() {
        return ResponseEntity.ok(modelService.getAvailableModels());
    }
}