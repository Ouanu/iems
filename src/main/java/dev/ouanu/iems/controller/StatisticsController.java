package dev.ouanu.iems.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.ouanu.iems.service.StatisticsService;
import dev.ouanu.iems.vo.DashboardStatsVO;

@RestController
@RequestMapping("/api/stats")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @PreAuthorize("hasAnyAuthority('operator:read','operator:manage','device:read','device:manage','app:read','app:manage')")
    @GetMapping("/overview")
    public ResponseEntity<DashboardStatsVO> getDashboardOverview() {
        DashboardStatsVO stats = statisticsService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }
}
