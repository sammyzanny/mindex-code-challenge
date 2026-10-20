package com.mindex.challenge.service;

import com.mindex.challenge.data.Compensation;

import java.util.List;

public interface CompensationService {
    Compensation create(String employeeId, Compensation compensation);
    List<Compensation> read(String employeeId);
}
