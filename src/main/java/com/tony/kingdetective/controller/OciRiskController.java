package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.response.oci.risk.OciRiskReportRsp;
import com.tony.kingdetective.service.oci.OciRiskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oci/risk")
public class OciRiskController {

    private final OciRiskService riskService;

    public OciRiskController(OciRiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping
    public ResponseData<OciRiskReportRsp> report(@RequestParam(value = "maxConfigs", required = false, defaultValue = "8") Integer maxConfigs) {
        return ResponseData.successData(riskService.report(maxConfigs));
    }
}
