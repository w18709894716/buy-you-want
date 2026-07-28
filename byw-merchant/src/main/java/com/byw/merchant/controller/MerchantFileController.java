package com.byw.merchant.controller;

import com.byw.api.file.FileFeignClient;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 商家端文件上传：商品图片等（委托 byw-file）。
 */
@RestController
@RequestMapping("/merchant")
@RequireRole({CommonConstants.ROLE_MERCHANT_OWNER, CommonConstants.ROLE_MERCHANT_STAFF})
@RequiredArgsConstructor
public class MerchantFileController {

    private final FileFeignClient fileFeignClient;

    @PostMapping("/upload")
    public R<String> upload(@RequestPart("file") MultipartFile file,
                            @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return fileFeignClient.uploadFile(file, folder);
    }

    @PostMapping("/upload-batch")
    public R<List<String>> uploadBatch(@RequestPart("files") List<MultipartFile> files,
                                       @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return fileFeignClient.uploadFiles(files, folder);
    }
}
