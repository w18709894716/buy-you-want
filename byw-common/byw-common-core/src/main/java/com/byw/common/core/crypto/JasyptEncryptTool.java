package com.byw.common.core.crypto;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

/**
 * 配置项加解密小工具：生成/还原 {@code ENC(...)} 密文。
 *
 * <p>主密钥只从环境变量 {@code JASYPT_ENCRYPTOR_PASSWORD} 读取（不接受命令行参数，
 * 避免密钥留在 shell 历史里）。
 *
 * <p>用法（推荐用 {@code docs/deploy/jasypt/encrypt.ps1} 包装）：
 * <pre>
 * $env:JASYPT_ENCRYPTOR_PASSWORD = '你的主密钥'
 * java -cp ... com.byw.common.core.crypto.JasyptEncryptTool encrypt "明文"
 * java -cp ... com.byw.common.core.crypto.JasyptEncryptTool decrypt "ENC(...)"
 * </pre>
 *
 * <p>算法与 jasypt-spring-boot 的默认值保持一致（PBEWITHHMACSHA512ANDAES_256 + 随机 IV），
 * 否则生成出来的密文服务端解不开。
 */
public final class JasyptEncryptTool {

    private static final String ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";
    private static final String ENV_KEY = "JASYPT_ENCRYPTOR_PASSWORD";

    private JasyptEncryptTool() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("用法: JasyptEncryptTool <encrypt|decrypt> <文本>");
            System.err.println("主密钥从环境变量 " + ENV_KEY + " 读取");
            System.exit(2);
        }
        String password = System.getenv(ENV_KEY);
        if (password == null || password.isBlank()) {
            System.err.println("环境变量 " + ENV_KEY + " 未设置");
            System.exit(2);
        }
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setAlgorithm(ALGORITHM);
        encryptor.setIvGenerator(new RandomIvGenerator());

        String mode = args[0].trim().toLowerCase();
        String text = args[1];
        if ("encrypt".equals(mode)) {
            System.out.println("ENC(" + encryptor.encrypt(text) + ")");
        } else if ("decrypt".equals(mode)) {
            String cipher = text.trim();
            if (cipher.startsWith("ENC(") && cipher.endsWith(")")) {
                cipher = cipher.substring(4, cipher.length() - 1);
            }
            System.out.println(encryptor.decrypt(cipher));
        } else {
            System.err.println("未知模式: " + mode + "（只支持 encrypt / decrypt）");
            System.exit(2);
        }
    }
}
