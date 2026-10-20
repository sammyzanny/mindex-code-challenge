package com.mindex.challenge.controller;

import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.CompensationHistory;
import com.mindex.challenge.service.CompensationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompensationController {
    private static final Logger LOG = LoggerFactory.getLogger(CompensationController.class);

    private final CompensationService compensationService;

    public CompensationController(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @PostMapping("/compensation/{id}")
    public Compensation create(@PathVariable String id, @RequestBody Compensation compensation) {
        LOG.debug("Received compensation create request for employee id [{}] and compensation [{}]", id, compensation);

        return compensationService.create(id, compensation);
    }

    @GetMapping("/compensation/{id}")
    public CompensationHistory read(@PathVariable String id) {
        LOG.debug("Received compensation read request for employee id [{}]", id);

        return compensationService.read(id);
    }
}
