package com.byw.admin.controller;

import com.byw.api.file.FileFeignClient;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "管理端-文件上传", description = "商品图片、品牌logo等文件上传")
@RestController
@RequestMapping("/admin")
@RequireAdmin
@RequiredArgsConstructor
public class AdminFileController {

    private final FileFeignClient fileFeignClient;

    @Operation(summary = "上传单个文件")
    @PostMapping("/upload")
    public R<String> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return fileFeignClient.uploadFile(file, folder);
    }

    @Operation(summary = "批量上传文件")
    @PostMapping("/upload-batch")
    public R<List<String>> uploadBatch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return fileFeignClient.uploadFiles(files, folder);
    }
}
