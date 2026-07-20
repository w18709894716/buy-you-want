package com.byw.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {

    /**
     * 上传单个文件
     *
     * @param file   文件
     * @param folder 存储文件夹（如 review, product, avatar）
     * @return 文件访问 URL
     */
    String uploadFile(MultipartFile file, String folder);

    /**
     * 批量上传文件
     */
    List<String> uploadFiles(List<MultipartFile> files, String folder);

    /**
     * 删除文件
     */
    boolean deleteFile(String fileUrl);
}
