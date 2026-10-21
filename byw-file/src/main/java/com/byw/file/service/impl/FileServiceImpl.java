package com.byw.file.service.impl;

import com.byw.common.core.exception.BusinessException;
import com.byw.file.config.MinioConfig;
import com.byw.file.service.FileService;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 文件服务实现。
 *
 * <p>安全约束（原实现几乎不校验，等于开放的任意文件上传）：
 * <ul>
 *   <li>扩展名白名单：只允许图片与常用资质材料，避免上传 html/svg/jsp 等可执行或可注入内容；</li>
 *   <li>大小上限：与 {@code spring.servlet.multipart.max-file-size} 一致；</li>
 *   <li>folder 段白名单：只允许字母数字下划线中划线，避免构造出越界对象键；</li>
 *   <li>删除时校验对象键必须落在本桶内且不含 {@code ..}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** 允许上传的扩展名（图片 + 常用办公/资质材料） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "pdf", "doc", "docx", "xls", "xlsx", "zip");

    /** 目录段白名单格式 */
    private static final Pattern FOLDER_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    /** 单文件大小上限，默认 10MB（与 multipart 配置保持一致） */
    @Value("${byw.file.max-size-bytes:10485760}")
    private long maxSizeBytes;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucketName()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioConfig.getBucketName()).build());
                log.info("MinIO bucket '{}' 创建成功", minioConfig.getBucketName());
            }
            // 设置 bucket 公开读取策略（允许匿名 GET 访问文件）
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(minioConfig.getBucketName());
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(minioConfig.getBucketName()).config(policy).build());
        } catch (Exception e) {
            log.warn("MinIO 初始化失败，请确认 MinIO 服务已启动: {}", e.getMessage());
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        validateSize(file);
        String extension = resolveExtension(file.getOriginalFilename());
        validateExtension(extension);
        String safeFolder = normalizeFolder(folder);
        try {
            String objectName = buildObjectName(extension, safeFolder);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            String fileUrl = minioConfig.getPublicUrl() + "/" + minioConfig.getBucketName() + "/" + objectName;
            log.info("文件上传成功: {}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> uploadFiles(List<MultipartFile> files, String folder) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(uploadFile(file, folder));
        }
        return urls;
    }

    @Override
    public boolean deleteFile(String fileUrl) {
        // 校验放在 try 之外：非法地址应直接拒绝，而不是被"删除失败返回 false"吞掉
        String objectName = extractObjectName(fileUrl);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            log.info("文件删除成功: {}", fileUrl);
            return true;
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /** 大小校验：multipart 层已有一道限制，这里保证服务被内部直接调用时同样受限 */
    private void validateSize(MultipartFile file) {
        if (maxSizeBytes > 0 && file.getSize() > maxSizeBytes) {
            throw new BusinessException("文件大小不能超过 " + Math.max(maxSizeBytes / 1024 / 1024, 1) + "MB");
        }
    }

    /** 校验扩展名白名单；空扩展名一律拒绝 */
    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型，仅允许图片与常用文档");
        }
    }

    /**
     * 提取小写扩展名：只取最后一个点之后的纯字母数字段（长度≤10），
     * 使 {@code a.php.jpg}、{@code a.jsp } 这类伪装无法绕过白名单。
     */
    private String resolveExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return "";
        }
        String extension = originalFilename.substring(index + 1).trim().toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? extension : "";
    }

    /** folder 段白名单：避免 {@code ../} 之类构造出越界对象键 */
    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "default";
        }
        if (!FOLDER_PATTERN.matcher(folder).matches()) {
            throw new BusinessException("上传目录不合法");
        }
        return folder;
    }

    /**
     * 构建对象名称：folder/yyyy/MM/dd/uuid.ext（扩展名来自白名单，不再直接拼接原始文件名）
     */
    private String buildObjectName(String extension, String folder) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return folder + "/" + datePath + "/" + uuid + "." + extension;
    }

    /**
     * 从 URL 中提取对象名称（兼容对外 public-url 与内部 endpoint 两种前缀），
     * 并校验结果是桶内相对键。
     */
    private String extractObjectName(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BusinessException("文件地址不能为空");
        }
        String bucketPart = "/" + minioConfig.getBucketName() + "/";
        String objectName = null;
        for (String base : new String[]{minioConfig.getPublicUrl(), minioConfig.getEndpoint()}) {
            String prefix = base + bucketPart;
            if (fileUrl.startsWith(prefix)) {
                objectName = fileUrl.substring(prefix.length());
                break;
            }
        }
        if (objectName == null) {
            // 兜底：按桶名切分，取其后的对象路径
            int idx = fileUrl.indexOf(bucketPart);
            objectName = idx >= 0 ? fileUrl.substring(idx + bucketPart.length()) : fileUrl;
        }
        if (objectName.startsWith("/") || objectName.contains("..") || objectName.isBlank()) {
            throw new BusinessException("文件地址不合法");
        }
        return objectName;
    }
}
