package com.byw.api.file;

import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@FeignClient(name = "byw-file", contextId = "fileFeignClient")
public interface FileFeignClient {

    @PostMapping(value = "/feign/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<String> uploadFile(@RequestPart("file") MultipartFile file,
                         @RequestParam(value = "folder", required = false, defaultValue = "default") String folder);

    @PostMapping(value = "/feign/file/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<List<String>> uploadFiles(@RequestPart("files") List<MultipartFile> files,
                                @RequestParam(value = "folder", required = false, defaultValue = "default") String folder);

    @DeleteMapping("/feign/file/delete")
    R<Boolean> deleteFile(@RequestParam("url") String fileUrl);
}
