package com.byw.file.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户端文件上传接口（经网关 /api/file/** 路由，去除 /api 前缀后为 /file/**）
 */
@Tag(name = "文件上传", description = "用户端文件上传与删除")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileService fileService;

    /**
     * 免登录上传：商家入驻等游客场景需上传资质材料（byw-shop 侧提交接口同为 @Public）
     */
    @Public
    @Operation(summary = "上传单个文件")
    @PostMapping("/upload")
    public R<String> upload(@RequestPart("file") MultipartFile file,
                            @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFile(file, folder));
    }

    @Operation(summary = "批量上传文件")
    @PostMapping("/upload-batch")
    public R<List<String>> uploadBatch(@RequestPart("files") List<MultipartFile> files,
                                       @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFiles(files, folder));
    }

    /**
     * 删除文件：文件没有归属记录，任何登录用户都能凭 URL 删除他人文件，
     * 因此收紧为**仅平台管理员**（当前前端未使用该接口，收紧无兼容影响）。
     */
    @RequireAdmin
    @Operation(summary = "删除文件（平台管理员）")
    @DeleteMapping("/delete")
    public R<Boolean> delete(@RequestParam("url") String fileUrl) {
        return R.ok(fileService.deleteFile(fileUrl));
    }
}
