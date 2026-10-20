package com.mindex.challenge.service;

import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.CompensationHistory;

public interface CompensationService {
    Compensation create(String employeeId, Compensation compensation);
    CompensationHistory read(String employeeId);
}
