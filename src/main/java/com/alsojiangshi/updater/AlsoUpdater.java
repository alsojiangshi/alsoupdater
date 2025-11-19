package com.alsojiangshi.updater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

public class AlsoUpdater {

    private static String ENDPOINT;
    private static String ACCESS_KEY;
    private static String SECRET_KEY;
    private static String BUCKET;
    private static String REGION;
    private static String DOWNLOAD_DIR;

    public static void main(String[] args) {
        try {
            // 1. 加载配置文件
            loadConfig();

            // 2. 初始化 Minio 客户端
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(ENDPOINT)
                    .credentials(ACCESS_KEY, SECRET_KEY)
                    .region(REGION)
                    .build();

            System.out.println("正在获取 manifest.json ...");

            // 3. 获取 manifest.json 的预签名 URL
            String manifestUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(BUCKET)
                            .object("manifest.json")
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );

            // 4. 下载并解析 manifest
            String manifestJson = downloadString(manifestUrl);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject manifestObj = gson.fromJson(manifestJson, JsonObject.class);
            
            if (!manifestObj.has("files")) {
                System.out.println("Manifest 中没有文件");
                return;
            }

            JsonArray files = manifestObj.getAsJsonArray("files");
            System.out.printf("开始校验和下载 %d 个文件...%n", files.size());

            OkHttpClient httpClient = new OkHttpClient();

            // 5. 遍历文件列表
            for (JsonElement element : files) {
                JsonObject entry = element.getAsJsonObject();
                String relPath = entry.get("path").getAsString();
                long expectedSize = entry.get("size").getAsLong();
                String expectedEtag = entry.get("etag").getAsString();

                // 构造本地文件路径 (处理跨平台路径分隔符)
                File localFile = new File(DOWNLOAD_DIR, relPath);
                boolean needDownload = true;

                // 6. 检查本地文件
                if (localFile.exists()) {
                    long localSize = localFile.length();
                    String localMd5 = calcMd5(localFile);

                    // 比较大小和MD5 (忽略大小写比较MD5)
                    if (localSize == expectedSize && localMd5.equalsIgnoreCase(expectedEtag)) {
                        System.out.printf("[已存在] %s%n", relPath);
                        needDownload = false;
                    }
                }

                // 7. 如果需要下载
                if (needDownload) {
                    // 获取文件的预签名 URL
                    // 对应 Python: f"files/{rel_path}"
                    String objectName = "files/" + relPath.replace("\\", "/"); // 确保对象路径是正斜杠

                    String fileUrl = minioClient.getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.GET)
                                    .bucket(BUCKET)
                                    .object(objectName)
                                    .expiry(1, TimeUnit.HOURS)
                                    .build()
                    );

                    System.out.printf("[下载] %s -> %s%n", relPath, localFile.getPath());
                    downloadFile(httpClient, fileUrl, localFile);
                }
            }

            System.out.println("更新完成！");

        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 加载配置文件
    private static void loadConfig() throws IOException {
        File configFile = new File("config.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        
        if (!configFile.exists()) {
            JsonObject def = new JsonObject();
            def.addProperty("endpoint", "http://127.0.0.1:9000");
            def.addProperty("access_key", "YOUR_ACCESS_KEY");
            def.addProperty("secret_key", "YOUR_SECRET_KEY");
            def.addProperty("bucket", "modpack");
            def.addProperty("region", "shanghai");
            def.addProperty("download_dir", "./modpack");

            try (Writer w = new FileWriter(configFile)) {
                gson.toJson(def, w);
            }

            throw new FileNotFoundException("config.json not found. A default config.json has been generated. Fill it and run again.");
        }
        // 读取 JSON
        JsonObject cfg;
        try (Reader reader = new FileReader(configFile)) {
            cfg = gson.fromJson(reader, JsonObject.class);
        }

        ENDPOINT     = cfg.get("endpoint").getAsString();
        ACCESS_KEY   = cfg.get("access_key").getAsString();
        SECRET_KEY   = cfg.get("secret_key").getAsString();
        BUCKET       = cfg.get("bucket").getAsString();
        REGION       = cfg.get("region").getAsString();
        DOWNLOAD_DIR = cfg.get("download_dir").getAsString();

        if (ENDPOINT.isEmpty() || ACCESS_KEY.isEmpty() || SECRET_KEY.isEmpty() || BUCKET.isEmpty()) {
            throw new IllegalArgumentException("config.json is missing required values");
            
            // 确保 endpoint 包含协议头，如果用户没写 http://，Minio 客户端可能需要处理，
            // 但这里我们保留原样，MinioClient.builder().endpoint() 会处理 URL
        }
    }


    // 计算文件的 MD5 (十六进制字符串)
    private static String calcMd5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // 下载字符串内容 (用于获取 manifest json)
    private static String downloadString(String url) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return response.body().string();
        }
    }

    // 下载文件流并保存到本地
    private static void downloadFile(OkHttpClient client, String url, File destination) throws IOException {
        // 确保父目录存在
        File parentDir = destination.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Download failed: " + response);
            
            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
        }
    }
}