package com.byw.file.feign;

import com.byw.api.file.FileFeignClient;
import com.byw.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.byw.file.service.FileService;

import java.util.List;

@RestController
@RequestMapping("/feign/file")
@RequiredArgsConstructor
public class FileFeignImpl implements FileFeignClient {

    private final FileService fileService;

    @Override
    public R<String> uploadFile(@RequestPart("file") MultipartFile file,
                                @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFile(file, folder));
    }

    @Override
    public R<List<String>> uploadFiles(@RequestPart("files") List<MultipartFile> files,
                                       @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFiles(files, folder));
    }

    @Override
    public R<Boolean> deleteFile(@RequestParam("url") String fileUrl) {
        return R.ok(fileService.deleteFile(fileUrl));
    }
}
