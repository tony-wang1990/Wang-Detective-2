package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.service.ClientFeatureGatewayService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/client-features")
public class ClientFeatureGatewayController {

    private final ClientFeatureGatewayService gatewayService;

    public ClientFeatureGatewayController(ClientFeatureGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping("/menu")
    public ResponseData<ClientFeatureGatewayService.GatewayResponse> menu() {
        return ResponseData.successData(gatewayService.menu());
    }

    @PostMapping("/callback")
    public ResponseData<ClientFeatureGatewayService.GatewayResponse> callback(@RequestBody CallbackRequest request) {
        return ResponseData.successData(gatewayService.invoke(request.callbackData(), request.sessionId()));
    }

    @PostMapping("/input")
    public ResponseData<ClientFeatureGatewayService.GatewayResponse> input(@RequestBody InputRequest request) {
        return ResponseData.successData(gatewayService.submitInput(
                request.callbackData(),
                request.sessionId(),
                request.value()
        ));
    }

    @PostMapping(path = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseData<ClientFeatureGatewayService.GatewayResponse> restore(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password
    ) {
        return ResponseData.successData(gatewayService.restore(sessionId, file, password));
    }

    @GetMapping("/coverage")
    public ResponseData<CoverageResponse> coverage() {
        List<String> patterns = gatewayService.registeredPatterns();
        return ResponseData.successData(new CoverageResponse(patterns.size(), patterns));
    }

    @GetMapping("/attachments/{token}")
    public ResponseEntity<Resource> attachment(@PathVariable String token) {
        return gatewayService.getAttachment(token)
                .map(download -> ResponseEntity.ok()
                        .contentType(safeMediaType(download.contentType()))
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment()
                                        .filename(download.fileName(), StandardCharsets.UTF_8)
                                        .build()
                                        .toString()
                        )
                        .body((Resource) new FileSystemResource(download.path())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private MediaType safeMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public record CallbackRequest(String callbackData, String sessionId) {
    }

    public record InputRequest(String callbackData, String sessionId, String value) {
    }

    public record CoverageResponse(int registeredHandlerCount, List<String> patterns) {
    }
}
