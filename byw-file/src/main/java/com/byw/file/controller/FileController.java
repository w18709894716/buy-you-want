package com.byw.file.controller;

import com.byw.api.file.FileFeignClient;
import com.byw.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.byw.file.service.FileService;

import java.util.List;

@Tag(name = "文件管理", description = "文件上传与删除")
@RestController
@RequiredArgsConstructor
public class FileController implements FileFeignClient {

    private final FileService fileService;

    @Operation(summary = "上传单个文件")
    @Override
    public R<String> uploadFile(@RequestPart("file") MultipartFile file,
                                @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFile(file, folder));
    }

    @Operation(summary = "批量上传文件")
    @Override
    public R<List<String>> uploadFiles(@RequestPart("files") List<MultipartFile> files,
                                       @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFiles(files, folder));
    }

    @Operation(summary = "删除文件")
    @Override
    public R<Boolean> deleteFile(@RequestParam("url") String fileUrl) {
        return R.ok(fileService.deleteFile(fileUrl));
    }
}
