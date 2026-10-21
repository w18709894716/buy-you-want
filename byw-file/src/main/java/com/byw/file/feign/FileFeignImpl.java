package com.byw.file.feign;

import com.byw.api.file.FileFeignClient;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.InternalApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.byw.file.service.FileService;

import java.util.List;

/**
 * 服务间文件接口。接口 FileFeignClient 上写的是绝对路径，Spring MVC 会把它与类级前缀拼接，
 * 所以这里必须在方法上重新声明相对路径，否则会注册到 /feign/file/feign/file/*，
 * 使 @InternalApi 落在无人调用的路径上、真实路径无人保护。
 */
@RestController
@RequestMapping("/feign/file")
@RequiredArgsConstructor
@InternalApi
public class FileFeignImpl implements FileFeignClient {

    private final FileService fileService;

    @Override
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<String> uploadFile(@RequestPart("file") MultipartFile file,
                                @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFile(file, folder));
    }

    @Override
    @PostMapping(value = "/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<List<String>> uploadFiles(@RequestPart("files") List<MultipartFile> files,
                                       @RequestParam(value = "folder", required = false, defaultValue = "default") String folder) {
        return R.ok(fileService.uploadFiles(files, folder));
    }

    @Override
    @DeleteMapping("/delete")
    public R<Boolean> deleteFile(@RequestParam("url") String fileUrl) {
        return R.ok(fileService.deleteFile(fileUrl));
    }
}
